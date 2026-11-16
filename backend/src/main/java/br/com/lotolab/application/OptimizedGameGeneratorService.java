package br.com.lotolab.application;

import br.com.lotolab.domain.GameGenerationCriteria;
import br.com.lotolab.domain.LotofacilGame;
import br.com.lotolab.domain.OptimizedGameResult;
import br.com.lotolab.domain.AgentGameRecommendation;
import br.com.lotolab.domain.PatternInsight;
import br.com.lotolab.domain.StrategyWalkForwardFold;
import br.com.lotolab.domain.Contest;
import br.com.lotolab.application.port.PatternInsightRepository;
import br.com.lotolab.application.port.ContestRepository;
import br.com.lotolab.domain.UniformLotofacilGameGenerator;
import br.com.lotolab.infrastructure.logging.LogContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.HashMap;
import java.util.Map;

@Service
public class OptimizedGameGeneratorService {
    private static final Logger LOGGER = LoggerFactory.getLogger(OptimizedGameGeneratorService.class);
    public static final int DEFAULT_ITERATIONS = 500;
    private static final int MAX_CANDIDATE_ATTEMPTS = 50;
    private static final Pattern JSON_NUMBER = Pattern.compile("\\\"%s\\\":(-?[0-9]+(?:\\.[0-9]+)?)");
    private static final Pattern JSON_ARRAY = Pattern.compile("\\\"%s\\\":\\[([^]]*)]");

    private final GameBacktestingService backtestingService;
    private final PatternInsightRepository insightRepository;
    private final ContestRepository contestRepository;
    private final UniformLotofacilGameGenerator initialGenerator = new UniformLotofacilGameGenerator();
    private final int iterationLimit;

    @Autowired
    public OptimizedGameGeneratorService(GameBacktestingService backtestingService,
                                         PatternInsightRepository insightRepository,
                                         ContestRepository contestRepository) {
        this(backtestingService, DEFAULT_ITERATIONS, insightRepository, contestRepository);
    }

    public OptimizedGameGeneratorService(GameBacktestingService backtestingService) {
        this(backtestingService, DEFAULT_ITERATIONS, null, null);
    }

    OptimizedGameGeneratorService(GameBacktestingService backtestingService, int iterationLimit) {
        this(backtestingService, iterationLimit, null, null);
    }

    OptimizedGameGeneratorService(GameBacktestingService backtestingService, int iterationLimit,
                                  PatternInsightRepository insightRepository, ContestRepository contestRepository) {
        if (iterationLimit < 1) throw new IllegalArgumentException("O limite de iterações deve ser positivo");
        this.backtestingService = Objects.requireNonNull(backtestingService);
        this.insightRepository = insightRepository;
        this.contestRepository = contestRepository;
        this.iterationLimit = iterationLimit;
    }

    public OptimizedGameResult generate(GameGenerationCriteria criteria) {
        int target = contestRepository == null ? 1 : contestRepository.findLatest()
                .map(contest -> contest.number() + 1).orElse(1);
        return generateForTarget(criteria, target).result();
    }

    public AgentGameRecommendation generateForTarget(GameGenerationCriteria criteria, Integer requestedTarget) {
        long startedAt = System.nanoTime();
        try (LogContext ignored = LogContext.agentRun("Agent2", "optimized-game-evaluation", "MANUAL")) {
        if (criteria == null) throw new IllegalArgumentException("Os critérios são obrigatórios");
        int latest = contestRepository == null ? 0 : contestRepository.findLatest().map(contest -> contest.number()).orElse(0);
        int target = requestedTarget == null ? latest + 1 : requestedTarget;
        if (target <= latest || target <= 0) {
            throw new IllegalArgumentException("O concurso-alvo já possui resultado conhecido; previsão retroativa não é permitida");
        }
        GameBacktestingService.BacktestingHistory history = backtestingService.loadHistory();
        int trainingEvidenceCutoff = Math.min(target, history.trainingLastContestNumber() + 1);
        List<PatternInsight> evidence = insightRepository == null ? List.of()
                : insightRepository.findEligible(trainingEvidenceCutoff);
        LOGGER.info("phase=START targetContest={} latestKnownContest={} knownHistoryThroughContest={} candidateTrainingLastContest={} evidenceCutoffExclusive={} historyCount={} evidenceCount={} candidateBudget={} evenNumbers={} oddNumbers={} seedPresent={}",
                target, latest, latest, history.trainingLastContestNumber(), trainingEvidenceCutoff,
                history.contestCount(), evidence.size(), iterationLimit + 1,
                criteria.evenNumbers(), criteria.oddNumbers(), criteria.seed() != null);
        long baselineSeed = (((long) target) << 32) ^ history.contestCount()
                ^ (criteria.seed() == null ? 0L : criteria.seed());
        SearchResult selected = search(criteria, history, evidence, criteria.seed());
        OptimizedGameResult result = selected.result();
        var baseline = backtestingService.evaluateRandomBaseline(criteria, history, iterationLimit + 1, baselineSeed);
        String evidenceStatus = evidence.isEmpty() ? "NO_ELIGIBLE_EVIDENCE" : "EVIDENCE_USED";
        List<StrategyWalkForwardFold> folds = evaluateWalkForward(criteria, baselineSeed, backtestingService.loadContests());
        long walkForwardStrategyScore = folds.stream().mapToLong(StrategyWalkForwardFold::strategyScore).sum();
        long walkForwardBaselineScore = folds.stream().mapToLong(StrategyWalkForwardFold::randomBaselineScore).sum();
        String performanceStatus = (folds.isEmpty() ? result.fitnessScore() > baseline.fitnessScore()
                : walkForwardStrategyScore > walkForwardBaselineScore)
                ? "SCORE_EXCEEDS_BASELINE" : "NO_DEMONSTRATED_ADVANTAGE";
        AgentGameRecommendation recommendation = new AgentGameRecommendation(target, result, latest, evidenceStatus,
                evidence.stream().map(PatternInsight::id).filter(Objects::nonNull).toList(), "mutation-1",
                baseline.fitnessScore(), baseline.contestsEvaluated(), baselineSeed, iterationLimit + 1,
                performanceStatus, walkForwardStrategyScore, walkForwardBaselineScore, folds);
        LogContext.phase("SUCCESS");
        LOGGER.info("targetContest={} selectedGame={} trainingScore={} validationScore={} baselineScore={} performanceStatus={} strategyWalkForwardScore={} baselineWalkForwardScore={} folds={} iterations={} duplicateMutationsSkipped={} searchExhausted={} durationMs={}",
                target, result.game().numbers(), result.trainingFitnessScore(), result.fitnessScore(),
                baseline.fitnessScore(), performanceStatus, walkForwardStrategyScore, walkForwardBaselineScore,
                folds.size(), result.iterations(), result.duplicateMutationsSkipped(), result.searchExhausted(),
                (System.nanoTime() - startedAt) / 1_000_000L);
        return recommendation;
        }
    }

    private SearchResult search(GameGenerationCriteria criteria, GameBacktestingService.BacktestingHistory history,
                                List<PatternInsight> evidence, Long seed) {
        LotofacilGame champion = initialGenerator.generate(new GameGenerationCriteria(
                1, criteria.evenNumbers(), criteria.oddNumbers(), seed)).getFirst();
        var championTraining = backtestingService.evaluateTraining(champion, history);
        double championEvidencePenalty = evidencePenalty(champion, evidence);
        Random random = seed == null ? new Random() : new Random(seed ^ 0x5DEECE66DL);
        Set<LotofacilGame> evaluated = new HashSet<>();
        evaluated.add(champion);
        int iterations = 0;
        int duplicatesSkipped = 0;
        boolean searchExhausted = false;
        for (int iteration = 0; iteration < iterationLimit; iteration++) {
            LotofacilGame candidate = null;
            for (int attempt = 0; attempt < MAX_CANDIDATE_ATTEMPTS; attempt++) {
                LotofacilGame proposed = mutateOnce(champion, criteria, random);
                if (evaluated.add(proposed)) { candidate = proposed; break; }
                duplicatesSkipped++;
            }
            if (candidate == null) { searchExhausted = true; break; }
            var candidateTraining = backtestingService.evaluateTraining(candidate, history);
            double candidatePenalty = evidencePenalty(candidate, evidence);
            iterations++;
            if (candidateTraining.fitnessScore() > championTraining.fitnessScore()
                    || candidateTraining.fitnessScore() == championTraining.fitnessScore()
                    && candidatePenalty < championEvidencePenalty) {
                champion = candidate;
                championTraining = candidateTraining;
                championEvidencePenalty = candidatePenalty;
            }
        }
        var validation = backtestingService.evaluateValidation(champion, history);
        LOGGER.debug("phase=SUCCESS searchIterations={} duplicateMutationsSkipped={} searchExhausted={} trainingScore={} validationScore={} evidencePenalty={}",
                iterations, duplicatesSkipped, searchExhausted, championTraining.fitnessScore(),
                validation.fitnessScore(), championEvidencePenalty);
        return new SearchResult(new OptimizedGameResult(champion, validation.fitnessScore(), validation.contestsEvaluated(),
                iterations, championTraining.fitnessScore(), championTraining.contestsEvaluated(),
                duplicatesSkipped, searchExhausted));
    }

    /** Every candidate for a fold is trained only before the first contest in that fold's validation block. */
    private List<StrategyWalkForwardFold> evaluateWalkForward(GameGenerationCriteria criteria, long seed,
                                                              List<Contest> contests) {
        if (contests.size() < 3) return List.of();
        List<StrategyWalkForwardFold> folds = new ArrayList<>();
        int trainingEnd = Math.max(2, contests.size() / 2);
        int blockSize = Math.max(1, contests.size() / 10);
        int foldNumber = 0;
        while (trainingEnd < contests.size()) {
            int validationEnd = Math.min(contests.size(), trainingEnd + blockSize);
            List<Contest> training = contests.subList(0, trainingEnd);
            List<Contest> validation = contests.subList(trainingEnd, validationEnd);
            long foldSeed = seed + ++foldNumber * 104729L;
            List<PatternInsight> eligible = insightRepository == null ? List.of()
                    : insightRepository.findEligible(validation.getFirst().number());
            GameGenerationCriteria foldCriteria = new GameGenerationCriteria(1, criteria.evenNumbers(),
                    criteria.oddNumbers(), foldSeed);
            var foldHistory = backtestingService.historyForFold(training, validation);
            SearchResult optimized = search(foldCriteria, foldHistory, eligible, foldSeed);
            var baseline = backtestingService.evaluateRandomBaseline(foldCriteria, foldHistory,
                    iterationLimit + 1, foldSeed ^ 0x6A09E667L);
            folds.add(new StrategyWalkForwardFold(foldNumber, training.getFirst().number(), training.getLast().number(),
                    validation.getFirst().number(), validation.getLast().number(), training.size(), validation.size(),
                    optimized.result().fitnessScore(), baseline.fitnessScore(), foldSeed, iterationLimit + 1));
            LOGGER.info("phase=SUCCESS walkForwardFold={} trainingRange={}..{} validationRange={}..{} trainingCount={} validationCount={} strategyScore={} baselineScore={} candidateBudget={}",
                    foldNumber, training.getFirst().number(), training.getLast().number(),
                    validation.getFirst().number(), validation.getLast().number(), training.size(), validation.size(),
                    optimized.result().fitnessScore(), baseline.fitnessScore(), iterationLimit + 1);
            trainingEnd = validationEnd;
        }
        return List.copyOf(folds);
    }

    private record SearchResult(OptimizedGameResult result) { }

    double evidencePenalty(LotofacilGame game, List<PatternInsight> evidence) {
        Map<String, List<Double>> lossesByMetric = new HashMap<>();
        for (PatternInsight insight : evidence) {
            Double expected = evidenceNumber(insight.evidenceJson(), "predictedValue");
            if (expected == null) continue;
            Double actual = evidenceValue(game, insight);
            if (actual == null) continue;
            lossesByMetric.computeIfAbsent(metricFamily(insight.patternType()), ignored -> new ArrayList<>())
                    .add(Math.abs(actual - expected));
        }
        return lossesByMetric.values().stream().mapToDouble(values ->
                values.stream().mapToDouble(Double::doubleValue).average().orElse(0)).sum();
    }

    private Double evidenceValue(LotofacilGame game, PatternInsight insight) {
        String json = insight.evidenceJson();
        return switch (insight.patternType()) {
            case "ODD_COUNT" -> (double) game.numbers().stream().filter(number -> number % 2 != 0).count();
            case "DRAW_SUM" -> (double) game.numbers().stream().mapToInt(Integer::intValue).sum();
            case "CONSECUTIVE_PAIRS" -> (double) countAdjacentPairs(game.numbers());
            case "NUMBER_FREQUENCY" -> evidenceNumber(json, "number") == null ? null
                    : game.numbers().contains(evidenceNumber(json, "number").intValue()) ? 1.0 : 0.0;
            case "NUMBER_BAND" -> {
                Double first = evidenceNumber(json, "bandStart");
                Double last = evidenceNumber(json, "bandEnd");
                yield first == null || last == null ? null : game.numbers().stream()
                        .filter(number -> number >= first.intValue() && number <= last.intValue()).count() * 1.0;
            }
            case "CONSECUTIVE_DRAW_REPETITION" -> {
                List<Integer> previous = evidenceArray(json, "previousNumbers");
                yield previous.isEmpty() ? null : game.numbers().stream().filter(previous::contains).count() * 1.0;
            }
            case "CO_OCCURRENCE_PAIR", "CO_OCCURRENCE_TRIPLE" -> {
                List<Integer> members = evidenceArray(json, "members");
                yield members.isEmpty() ? null : members.stream().allMatch(game.numbers()::contains) ? 1.0 : 0.0;
            }
            case "CONSECUTIVE_RUN_LENGTH" -> {
                Double length = evidenceNumber(json, "runLength");
                yield length == null ? null : (double) countRunsAtLeast(game.numbers(), length.intValue());
            }
            case "MAX_CONSECUTIVE_SEQUENCE" -> (double) maxRun(game.numbers());
            default -> null;
        };
    }

    private static String metricFamily(String patternType) {
        return patternType.startsWith("CO_OCCURRENCE_") ? "CO_OCCURRENCE" : patternType;
    }

    private static Double evidenceNumber(String json, String key) {
        Matcher matcher = Pattern.compile(JSON_NUMBER.pattern().formatted(Pattern.quote(key))).matcher(json);
        return matcher.find() ? Double.parseDouble(matcher.group(1)) : null;
    }

    private static List<Integer> evidenceArray(String json, String key) {
        Matcher matcher = Pattern.compile(JSON_ARRAY.pattern().formatted(Pattern.quote(key))).matcher(json);
        if (!matcher.find() || matcher.group(1).isBlank()) return List.of();
        try {
            return java.util.Arrays.stream(matcher.group(1).split(","))
                    .map(String::trim).map(Integer::parseInt).toList();
        } catch (NumberFormatException exception) {
            return List.of();
        }
    }

    private static int countAdjacentPairs(List<Integer> numbers) {
        int pairs = 0;
        for (int i = 1; i < numbers.size(); i++) if (numbers.get(i) == numbers.get(i - 1) + 1) pairs++;
        return pairs;
    }

    private static int countRunsAtLeast(List<Integer> numbers, int minLength) {
        int count = 0, run = 1;
        for (int i = 1; i <= numbers.size(); i++) {
            if (i < numbers.size() && numbers.get(i) == numbers.get(i - 1) + 1) run++;
            else { if (run >= minLength) count++; run = 1; }
        }
        return count;
    }

    private static int maxRun(List<Integer> numbers) {
        int max = 1, run = 1;
        for (int i = 1; i < numbers.size(); i++) {
            run = numbers.get(i) == numbers.get(i - 1) + 1 ? run + 1 : 1;
            max = Math.max(max, run);
        }
        return max;
    }

    private LotofacilGame mutateOnce(LotofacilGame current, GameGenerationCriteria criteria, Random random) {
        List<Integer> numbers = new ArrayList<>(current.numbers());
        int changes = random.nextBoolean() ? 1 : 2;
        for (int change = 0; change < changes; change++) {
            Integer parity = criteria.evenNumbers() == null ? null : chooseMutableParity(criteria, random);
            List<Integer> eligible = numbers.stream()
                    .filter(number -> parity == null || number % 2 == parity).toList();
            int removed = eligible.get(random.nextInt(eligible.size()));
            numbers.remove(Integer.valueOf(removed));
            List<Integer> available = availableNumbers(numbers, parity).stream()
                    .filter(number -> number != removed).toList();
            numbers.add(available.get(random.nextInt(available.size())));
        }
        return new LotofacilGame(numbers);
    }

    private int chooseMutableParity(GameGenerationCriteria criteria, Random random) {
        List<Integer> mutableParities = new ArrayList<>(2);
        if (criteria.evenNumbers() > 0 && criteria.evenNumbers() < 12) mutableParities.add(0);
        if (criteria.oddNumbers() > 0 && criteria.oddNumbers() < 13) mutableParities.add(1);
        return mutableParities.get(random.nextInt(mutableParities.size()));
    }

    private List<Integer> availableNumbers(List<Integer> selected, Integer parity) {
        return java.util.stream.IntStream.rangeClosed(1, 25)
                .filter(number -> parity == null || number % 2 == parity)
                .filter(number -> !selected.contains(number))
                .boxed().toList();
    }
}
