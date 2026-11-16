package br.com.lotolab.application;

import br.com.lotolab.application.port.AiFeedbackGenerator;
import br.com.lotolab.application.port.AgentInterpretationRepository;
import br.com.lotolab.application.port.AgentJob;
import br.com.lotolab.application.port.PatternInsightRepository;
import br.com.lotolab.research.domain.PatternMiningAlgorithm;
import br.com.lotolab.domain.AgentGameRecommendation;
import br.com.lotolab.domain.AgentInterpretation;
import br.com.lotolab.domain.PatternInsight;
import br.com.lotolab.domain.PredictionAttempt;
import br.com.lotolab.infrastructure.logging.LogContext;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Optional narrative layer. All values sent to the model are computed and persisted by Java first. */
@Service
public class AgentInterpretationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AgentInterpretationService.class);
    private static final String PROMPT_VERSION = "statistical-interpretation-v1";
    private static final Pattern DIGIT = Pattern.compile("\\d");
    private static final Pattern CLAIM = Pattern.compile("(?iu)probabilidade|chance de (?:ganhar|prêmio)|prever sorteios|previsão de sorteio");

    private final AiFeedbackGenerator ai;
    private final PatternInsightRepository insightRepository;
    private final AgentInterpretationRepository interpretationRepository;
    private final ObjectMapper json;
    private final boolean enabled;

    @Autowired
    public AgentInterpretationService(AiFeedbackGenerator ai, PatternInsightRepository insightRepository,
                                      AgentInterpretationRepository interpretationRepository, ObjectMapper json,
                                      @Value("${lotolab.agents.gemini.enabled:false}") boolean enabled) {
        this.ai = Objects.requireNonNull(ai);
        this.insightRepository = Objects.requireNonNull(insightRepository);
        this.interpretationRepository = Objects.requireNonNull(interpretationRepository);
        this.json = Objects.requireNonNull(json);
        this.enabled = enabled;
    }

    public AgentInterpretationService(AiFeedbackGenerator ai, PatternInsightRepository insightRepository,
                                      AgentInterpretationRepository interpretationRepository, ObjectMapper json) {
        this(ai, insightRepository, interpretationRepository, json, false);
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onMiningCompleted(PatternMiningCompletedEvent event) {
        try (LogContext ignored = LogContext.runId(event.executionRunId(), "AgentJobOutbox",
                "enqueue-research-interpretation", event.origin())) {
            enqueuePatternRunInterpretation(event.runId(), event.origin());
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public AgentJob enqueuePatternRunInterpretation(long runId, String origin) {
        List<PatternInsight> insights = insightRepository.findRunInsights(runId);
        Map<String, Long> counts = insights.stream().collect(Collectors.groupingBy(
                PatternInsight::patternType, Collectors.counting()));
        List<PatternEvidence> evidence = insights.stream()
                .sorted(Comparator.comparing((PatternInsight item) -> !"EXPERIMENTAL".equals(item.evidenceStatus()))
                        .thenComparing(item -> difference(item).negate()))
                .limit(60).map(PatternEvidence::from).toList();
        Map<String, Object> input = Map.of("agent", "PATTERN_RESEARCHER", "runId", runId,
                "algorithmVersion", insights.isEmpty() ? PatternMiningAlgorithm.ALGORITHM_VERSION
                        : insights.getFirst().algorithmVersion(),
                "insightCount", insights.size(), "countsByPatternType", counts, "evidence", evidence);
        return enqueue(runId, null, "PATTERN_RESEARCHER", input, "RESEARCH_INTERPRETATION", origin);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public AgentJob enqueuePredictionInterpretation(PredictionAttempt attempt,
                                                    AgentGameRecommendation recommendation, String origin) {
        List<PatternInsight> used = insightRepository.findByIds(recommendation.evidenceIds());
        List<PatternEvidence> evidence = used.stream().limit(40).map(PatternEvidence::from).toList();
        Map<String, Object> input = Map.ofEntries(
                Map.entry("agent", "GAME_EVALUATOR"), Map.entry("predictionId", attempt.id()),
                Map.entry("targetContestNumber", recommendation.targetContestNumber()),
                Map.entry("sourceCutoffContest", recommendation.sourceCutoffContest()),
                Map.entry("strategyVersion", recommendation.strategyVersion()),
                Map.entry("game", recommendation.result().game().numbers()),
                Map.entry("experimentalScore", recommendation.result().fitnessScore()),
                Map.entry("randomBaselineScore", recommendation.baselineFitnessScore()),
                Map.entry("performanceStatus", recommendation.performanceStatus()),
                Map.entry("walkForwardFolds", recommendation.folds()),
                Map.entry("evidenceStatus", recommendation.evidenceStatus()), Map.entry("evidence", evidence));
        return enqueue(null, attempt.id(), "GAME_EVALUATOR", input, "PREDICTION_INTERPRETATION", origin);
    }

    private AgentJob enqueue(Long runId, Long attemptId, String agentKey, Map<String, Object> input,
                             String type, String origin) {
        String serialized = serialize(input);
        AgentInterpretation pending = new AgentInterpretation(0, runId, attemptId, agentKey,
                "GOOGLE_GEMINI", ai.modelName(), PROMPT_VERSION, "PENDING", null, List.of(), null,
                serialized, null, Instant.now());
        return interpretationRepository.enqueue(pending, type, origin == null ? "MANUAL" : origin,
                LogContext.correlationId());
    }

    /** Claims at most one durable job; provider errors are retried with bounded exponential delay. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void processNextJob(String workerId) {
        var optional = interpretationRepository.claimNext(workerId, Instant.now().plusSeconds(300));
        if (optional.isEmpty()) return;
            AgentJob job = optional.get();
        AgentInterpretation source = job.interpretation();
        try (LogContext ignored = LogContext.runId(job.runId(), "GeminiJob", "interpret", job.origin());
             org.slf4j.MDC.MDCCloseable correlationContext = job.correlationId() == null ? null
                     : org.slf4j.MDC.putCloseable("correlationId", job.correlationId());
             org.slf4j.MDC.MDCCloseable jobContext = org.slf4j.MDC.putCloseable("jobId", Long.toString(job.id()))) {
            save(source.agentRunId(), source.predictionAttemptId(), source.agentKey(), "RUNNING", null,
                    List.of(), null, source.statisticalInputJson(), null);
            if (!enabled || !ai.isAvailable()) {
                String status = enabled ? "SKIPPED_NO_API_KEY" : "SKIPPED_DISABLED";
                save(source.agentRunId(), source.predictionAttemptId(), source.agentKey(), status, null,
                        List.of(), null, source.statisticalInputJson(), enabled ? "API_KEY_NOT_CONFIGURED" : "GEMINI_DISABLED");
                interpretationRepository.finishJob(job.id(), "SKIPPED", enabled ? "API_KEY_NOT_CONFIGURED" : "GEMINI_DISABLED", null);
                return;
            }
            try {
                String response = ai.generateFeedback(prompt(source.statisticalInputJson(), source.agentKey()));
                InterpretationPayload payload = json.readValue(response, InterpretationPayload.class);
                validate(payload);
                save(source.agentRunId(), source.predictionAttemptId(), source.agentKey(), "SUCCEEDED",
                        payload.summary(), payload.hypotheses(), payload.explanation(), source.statisticalInputJson(), null);
                interpretationRepository.finishJob(job.id(), "SUCCEEDED", null, null);
            } catch (Exception exception) {
                String category = exception instanceof InvalidInterpretationException invalid ? invalid.category
                        : exception instanceof AiIntegrationException aiFailure
                            ? (aiFailure.rateLimited() ? "AI_RATE_LIMITED"
                                : aiFailure.temporarilyUnavailable() ? "AI_TEMPORARILY_UNAVAILABLE" : "AI_PROVIDER_ERROR")
                            : exception.getClass().getSimpleName();
                boolean retry = exception instanceof AiIntegrationException aiFailure
                        && aiFailure.temporarilyUnavailable() && job.attempts() < job.maxAttempts();
                Instant next = retry ? Instant.now().plusSeconds(Math.min(900, 30L << Math.min(job.attempts() - 1, 4))) : null;
                String status = exception instanceof InvalidInterpretationException ? "INVALID_RESPONSE" : (retry ? "PENDING" : "FAILED");
                save(source.agentRunId(), source.predictionAttemptId(), source.agentKey(), status, null,
                        List.of(), null, source.statisticalInputJson(), category);
                interpretationRepository.finishJob(job.id(), retry ? "PENDING" : "FAILED", category, next);
                LOGGER.warn("agent={} jobId={} status={} errorCategory={} attempt={}/{}",
                        source.agentKey(), job.id(), retry ? "RETRY_SCHEDULED" : status, category,
                        job.attempts(), job.maxAttempts());
            }
        }
    }

    public AgentInterpretation interpretPatternRun(long runId) {
        List<PatternInsight> insights = insightRepository.findRunInsights(runId);
        Map<String, Long> counts = insights.stream().collect(Collectors.groupingBy(
                PatternInsight::patternType, Collectors.counting()));
        List<PatternEvidence> evidence = insights.stream()
                .sorted(Comparator.comparing((PatternInsight item) -> !"EXPERIMENTAL".equals(item.evidenceStatus()))
                        .thenComparing(item -> difference(item).negate()))
                .limit(60).map(PatternEvidence::from).toList();
        Map<String, Object> input = Map.of("agent", "PATTERN_RESEARCHER", "runId", runId,
                "algorithmVersion", insights.isEmpty() ? PatternMiningAlgorithm.ALGORITHM_VERSION
                        : insights.getFirst().algorithmVersion(),
                "insightCount", insights.size(), "countsByPatternType", counts, "evidence", evidence);
        return interpret(runId, null, "PATTERN_RESEARCHER", input);
    }

    public AgentInterpretation interpretPrediction(PredictionAttempt attempt, AgentGameRecommendation recommendation) {
        List<PatternInsight> used = insightRepository.findByIds(recommendation.evidenceIds());
        List<PatternEvidence> evidence = used.stream().limit(40).map(PatternEvidence::from).toList();
        Map<String, Object> input = Map.ofEntries(
                Map.entry("agent", "GAME_EVALUATOR"), Map.entry("predictionId", attempt.id()),
                Map.entry("targetContestNumber", recommendation.targetContestNumber()),
                Map.entry("sourceCutoffContest", recommendation.sourceCutoffContest()),
                Map.entry("strategyVersion", recommendation.strategyVersion()),
                Map.entry("game", recommendation.result().game().numbers()),
                Map.entry("experimentalScore", recommendation.result().fitnessScore()),
                Map.entry("randomBaselineScore", recommendation.baselineFitnessScore()),
                Map.entry("performanceStatus", recommendation.performanceStatus()),
                Map.entry("walkForwardFolds", recommendation.folds()),
                Map.entry("evidenceStatus", recommendation.evidenceStatus()), Map.entry("evidence", evidence));
        return interpret(null, attempt.id(), "GAME_EVALUATOR", input);
    }

    public java.util.Optional<AgentInterpretation> latestResearchInterpretation() {
        return interpretationRepository.findLatestByAgent("PATTERN_RESEARCHER");
    }

    public java.util.Optional<AgentJob> latestResearchJob() {
        return interpretationRepository.findLatestJob("PATTERN_RESEARCHER");
    }

    public java.util.Optional<AgentInterpretation> predictionInterpretation(long attemptId) {
        return interpretationRepository.findByPredictionAttempt(attemptId);
    }

    private AgentInterpretation interpret(Long runId, Long attemptId, String agentKey, Map<String, Object> input) {
        String serialized = serialize(input);
        long startedAt = System.nanoTime();
        try (LogContext ignored = LogContext.component("Gemini", "agent-interpretation")) {
            LogContext.phase("START");
            LOGGER.info("agent={} agentRunId={} predictionId={} featureEnabled={} model={} inputBytes={}",
                    agentKey, runId, attemptId, enabled, ai.modelName(),
                    serialized.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
            if (!enabled) {
                LogContext.phase("SKIP");
                LOGGER.info("agent={} reason=feature-disabled", agentKey);
                return save(runId, attemptId, agentKey, "SKIPPED_DISABLED", null, List.of(), null,
                        serialized, "GEMINI_DISABLED");
            }
            if (!ai.isAvailable()) {
                LogContext.phase("SKIP");
                LOGGER.info("agent={} reason=provider-not-configured", agentKey);
                return save(runId, attemptId, agentKey, "SKIPPED_NO_API_KEY", null, List.of(), null,
                        serialized, "API_KEY_NOT_CONFIGURED");
            }
            try {
                String response = ai.generateFeedback(prompt(serialized, agentKey));
                InterpretationPayload payload = json.readValue(response, InterpretationPayload.class);
                validate(payload);
                AgentInterpretation saved = save(runId, attemptId, agentKey, "SUCCEEDED", payload.summary(),
                        payload.hypotheses(), payload.explanation(), serialized, null);
                LogContext.phase("SUCCESS");
                LOGGER.info("agent={} status={} outputChars={} durationMs={}",
                        agentKey, saved.status(), response.length(), (System.nanoTime() - startedAt) / 1_000_000L);
                return saved;
            } catch (InvalidInterpretationException exception) {
                LogContext.phase("ERROR");
                LOGGER.warn("agent={} status=INVALID_RESPONSE category={} durationMs={}",
                        agentKey, exception.category, (System.nanoTime() - startedAt) / 1_000_000L);
                return save(runId, attemptId, agentKey, "INVALID_RESPONSE", null, List.of(), null,
                        serialized, exception.category);
            } catch (Exception exception) {
                String category = exception.getClass().getSimpleName();
                LogContext.phase("ERROR");
                LOGGER.warn("agent={} status=FAILED category={} durationMs={}",
                        agentKey, category, (System.nanoTime() - startedAt) / 1_000_000L);
                return save(runId, attemptId, agentKey, "FAILED", null, List.of(), null, serialized, category);
            }
        }
    }

    private AgentInterpretation save(Long runId, Long attemptId, String agentKey, String status,
                                     String summary, List<String> hypotheses, String explanation,
                                     String input, String errorCategory) {
        long startedAt = System.nanoTime();
        try (LogContext ignored = LogContext.component("InterpretationRepository", "save-interpretation")) {
            LogContext.phase("START");
            AgentInterpretation saved = interpretationRepository.save(new AgentInterpretation(0, runId, attemptId, agentKey,
                    "GOOGLE_GEMINI", ai.modelName(), PROMPT_VERSION, status, summary, hypotheses,
                    explanation, input, errorCategory, Instant.now()));
            LogContext.phase("SUCCESS");
            LOGGER.info("agent={} status={} interpretationId={} durationMs={}",
                    agentKey, status, saved.id(), (System.nanoTime() - startedAt) / 1_000_000L);
            return saved;
        }
    }

    private String prompt(String input, String agentKey) {
        return "You are a cautious statistical analyst for lottery experiments. The JSON below is the complete "
                + "machine-calculated evidence. Do not calculate, change, infer, or invent any metric, number, "
                + "probability, odds, causal claim, or prediction. Do not claim a game is more likely to win. "
                + "Interpret only patterns and score comparisons explicitly present in the input. Suggest at most "
                + "five falsifiable hypotheses as text for later Java walk-forward tests; label them as hypotheses, "
                + "not findings. Explain limitations and that past draws do not establish future advantage. "
                + "Return only JSON matching {\"summary\":string,\"hypotheses\":[string],\"explanation\":string}. "
                + "Do not include digits, percentages, odds, or claims of predictive power in the output. "
                + "The calling agent is " + agentKey + ". Evidence JSON: " + input;
    }

    private void validate(InterpretationPayload payload) {
        if (payload == null || !validText(payload.summary(), 20, 1200)
                || !validText(payload.explanation(), 20, 1600)
                || payload.hypotheses() == null || payload.hypotheses().size() > 5
                || payload.hypotheses().stream().anyMatch(text -> !validText(text, 15, 500))) {
            throw new InvalidInterpretationException("SCHEMA_OR_LENGTH");
        }
    }

    private boolean validText(String text, int min, int max) {
        return text != null && text.length() >= min && text.length() <= max
                && !DIGIT.matcher(text).find() && !CLAIM.matcher(text).find();
    }

    private String serialize(Map<String, Object> value) {
        try { return json.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalStateException("Could not serialize statistical evidence", exception); }
    }

    private static java.math.BigDecimal difference(PatternInsight insight) {
        return insight.baselineMetric() == null || insight.observedMetric() == null
                ? java.math.BigDecimal.ZERO : insight.baselineMetric().subtract(insight.observedMetric()).abs();
    }

    private record InterpretationPayload(String summary, List<String> hypotheses, String explanation) { }
    private record PatternEvidence(String patternType, String hypothesisKey, String evidenceStatus,
                                   Integer trainingFirst, Integer trainingLast, Integer validationFirst,
                                   Integer validationLast, Integer sampleSize, String observedMetric,
                                   String baselineMetric, String evidenceJson) {
        static PatternEvidence from(PatternInsight value) {
            return new PatternEvidence(value.patternType(), value.hypothesisKey(), value.evidenceStatus(),
                    value.trainingFirstContest(), value.trainingLastContest(), value.validationFirstContest(),
                    value.validationLastContest(), value.sampleSize(), String.valueOf(value.observedMetric()),
                    String.valueOf(value.baselineMetric()), value.evidenceJson());
        }
    }

    private static final class InvalidInterpretationException extends RuntimeException {
        private final String category;
        private InvalidInterpretationException(String category) { this.category = category; }
    }
}
