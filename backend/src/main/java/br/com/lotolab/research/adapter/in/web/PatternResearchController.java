package br.com.lotolab.research.adapter.in.web;

import br.com.lotolab.domain.PatternInsight;
import br.com.lotolab.infrastructure.logging.LogContext;
import br.com.lotolab.research.application.port.in.GetLatestPatternInsightsUseCase;
import br.com.lotolab.research.application.port.in.GetLatestResearchInterpretationUseCase;
import br.com.lotolab.research.application.port.in.GetResearchJobForRunUseCase;
import br.com.lotolab.research.application.port.in.GetPatternRunInsightsUseCase;
import br.com.lotolab.research.application.port.in.MinePatternsCommand;
import br.com.lotolab.research.application.port.in.MinePatternsUseCase;
import br.com.lotolab.research.application.port.in.PatternMiningResult;
import br.com.lotolab.research.application.ResearchJobSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/agentes/pesquisador")
public class PatternResearchController {
    private static final Logger LOGGER = LoggerFactory.getLogger(PatternResearchController.class);
    private final MinePatternsUseCase mining;
    private final GetLatestPatternInsightsUseCase latestInsights;
    private final GetPatternRunInsightsUseCase runInsights;
    private final GetLatestResearchInterpretationUseCase latestInterpretation;
    private final GetResearchJobForRunUseCase jobForRun;

    public PatternResearchController(MinePatternsUseCase mining,
                                     GetLatestPatternInsightsUseCase latestInsights,
                                     GetPatternRunInsightsUseCase runInsights,
                                     GetLatestResearchInterpretationUseCase latestInterpretation,
                                     GetResearchJobForRunUseCase jobForRun) {
        this.mining = mining;
        this.latestInsights = latestInsights;
        this.runInsights = runInsights;
        this.latestInterpretation = latestInterpretation;
        this.jobForRun = jobForRun;
    }

    @PostMapping("/minerar")
    public ResponseEntity<MiningRunResponse> mine() {
        long startedAt = System.nanoTime();
        try (LogContext ignored = LogContext.agentRun("Agent1", "pattern-mining", "MANUAL")) {
            LogContext.phase("START");
            try {
                PatternMiningResult result = mining.execute(new MinePatternsCommand("MANUAL"));
                List<PatternInsightResponse> insights = result.insightsWritten() == 0
                        ? List.of()
                        : runInsights.execute(result.runId()).stream().map(PatternInsightResponse::from).toList();
                Optional<ResearchJobSummary> job = result.completedNow() && result.runId() != null
                        ? jobForRun.execute(result.runId()) : Optional.empty();
                LogContext.phase("SUCCESS");
                LOGGER.info("source=manual outcome={} agentRunId={} contestCount={} sourceRange={}..{} folds={} insightsWritten={} returnedInsights={} durationMs={}",
                        result.outcome(), result.runId(), result.contestCount(), result.firstContest(),
                        result.lastContest(), result.foldCount(), result.insightsWritten(), insights.size(),
                        (System.nanoTime() - startedAt) / 1_000_000L);
                return ResponseEntity.ok(new MiningRunResponse(result.insightsWritten(),
                        result.insightsWritten() == 0
                                ? "Sem novo intervalo elegível ou execução já registrada."
                                : "Mineração concluída.",
                        insights, job.map(ResearchJobSummary::id).orElse(null),
                        job.map(ResearchJobSummary::status).orElse("NOT_QUEUED")));
            } catch (RuntimeException | Error exception) {
                LogContext.phase("ERROR");
                LOGGER.error("cause={} durationMs={}", exception.getClass().getSimpleName(),
                        (System.nanoTime() - startedAt) / 1_000_000L, exception);
                throw exception;
            }
        }
    }

    @GetMapping("/insights")
    public List<PatternInsightResponse> latestInsights() {
        return latestInsights.execute().stream().map(PatternInsightResponse::from).toList();
    }

    @GetMapping("/interpretacao")
    public ResponseEntity<AgentInterpretationResponse> latestInterpretation() {
        return latestInterpretation.execute().map(AgentInterpretationResponse::from)
                .map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    public record MiningRunResponse(int insightsWritten, String message, List<PatternInsightResponse> insights,
                                    Long interpretationJobId, String interpretationJobStatus) { }
}
