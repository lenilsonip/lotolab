package br.com.lotolab.application;

import br.com.lotolab.application.port.ContestRepository;
import br.com.lotolab.domain.BacktestingResult;
import br.com.lotolab.domain.Contest;
import br.com.lotolab.domain.LotofacilGame;
import br.com.lotolab.domain.GameGenerationCriteria;
import br.com.lotolab.domain.UniformLotofacilGameGenerator;
import br.com.lotolab.infrastructure.logging.LogContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
public class GameBacktestingService {
    private static final Logger LOGGER = LoggerFactory.getLogger(GameBacktestingService.class);
    private static final double TRAINING_PROPORTION = 0.8;
    private final ContestRepository contestRepository;

    public GameBacktestingService(ContestRepository contestRepository) {
        this.contestRepository = Objects.requireNonNull(contestRepository);
    }

    public BacktestingHistory loadHistory() {
        try (LogContext ignored = LogContext.component("Backtesting", "load-history")) {
            long startedAt = System.nanoTime();
            LogContext.phase("START");
            List<Contest> contests = contestRepository.findAll();
            LogContext.phase("SUCCESS");
            LOGGER.info("contestCount={} durationMs={}", contests.size(), elapsedMs(startedAt));
            return new BacktestingHistory(contests);
        }
    }

    public List<Contest> loadContests() {
        try (LogContext ignored = LogContext.component("Backtesting", "load-contests")) {
            long startedAt = System.nanoTime();
            LogContext.phase("START");
            List<Contest> contests = contestRepository.findAll().stream()
                    .sorted(Comparator.comparingInt(Contest::number)).toList();
            LogContext.phase("SUCCESS");
            LOGGER.info("contestCount={} durationMs={}", contests.size(), elapsedMs(startedAt));
            return contests;
        }
    }

    public BacktestingHistory historyForFold(List<Contest> training, List<Contest> validation) {
        return new BacktestingHistory(training, validation);
    }

    /** Descriptive full-history score. Do not use it to select a candidate for an unbiased backtest. */
    public BacktestingResult evaluate(LotofacilGame game, BacktestingHistory history) {
        return evaluateMasks(game, history.allMasks);
    }

    public BacktestingResult evaluateTraining(LotofacilGame game, BacktestingHistory history) {
        return evaluateMasks(game, history.trainingMasks);
    }

    public BacktestingResult evaluateValidation(LotofacilGame game, BacktestingHistory history) {
        return evaluateMasks(game, history.validationMasks);
    }

    public BacktestingResult evaluate(LotofacilGame game) {
        return evaluate(game, loadHistory());
    }

    /** Selects a random baseline champion on training data using the same candidate budget and parity filters. */
    public BacktestingResult evaluateRandomBaseline(GameGenerationCriteria criteria, BacktestingHistory history,
                                                    int candidateBudget, long seed) {
        try (LogContext ignored = LogContext.component("Backtesting", "random-baseline")) {
        long startedAt = System.nanoTime();
        if (candidateBudget <= 0) throw new IllegalArgumentException("O orçamento deve ser positivo");
        LogContext.phase("START");
        UniformLotofacilGameGenerator generator = new UniformLotofacilGameGenerator();
        LotofacilGame champion = null;
        long bestTrainingScore = -1;
        for (int i = 0; i < candidateBudget; i++) {
            GameGenerationCriteria randomCriteria = new GameGenerationCriteria(1, criteria.evenNumbers(),
                    criteria.oddNumbers(), seed + i);
            LotofacilGame candidate = generator.generate(randomCriteria).getFirst();
            long trainingScore = evaluateTraining(candidate, history).fitnessScore();
            if (trainingScore > bestTrainingScore) {
                champion = candidate;
                bestTrainingScore = trainingScore;
            }
        }
        BacktestingResult result = evaluateValidation(Objects.requireNonNull(champion), history);
        LogContext.phase("SUCCESS");
        LOGGER.info("strategy=random-baseline candidateBudget={} seed={} trainingContests={} validationContests={} validationScore={} durationMs={}",
                candidateBudget, seed, history.trainingContestCount(), history.validationContestCount(),
                result.fitnessScore(), elapsedMs(startedAt));
        return result;
        }
    }

    private BacktestingResult evaluateMasks(LotofacilGame game, int[] contestMasks) {
        Objects.requireNonNull(game, "O jogo é obrigatório");
        int gameMask = mask(game.numbers());
        long score = 0;
        for (int contestMask : contestMasks) {
            score += scoreForHits(Integer.bitCount(gameMask & contestMask));
        }
        return new BacktestingResult(game, score, contestMasks.length);
    }

    static int scoreForHits(int hits) {
        return switch (hits) {
            case 11 -> 1;
            case 12 -> 5;
            case 13 -> 25;
            case 14 -> 500;
            case 15 -> 10_000;
            default -> 0;
        };
    }

    private static long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }

    private static int mask(List<Integer> numbers) {
        int result = 0;
        for (int number : numbers) result |= 1 << (number - 1);
        return result;
    }

    public static final class BacktestingHistory {
        private final int[] allMasks;
        private final int[] trainingMasks;
        private final int[] validationMasks;
        private final int trainingLastContestNumber;

        private BacktestingHistory(List<Contest> contests) {
            List<Contest> sorted = contests.stream().sorted(Comparator.comparingInt(Contest::number)).toList();
            this.allMasks = sorted.stream().mapToInt(contest -> mask(contest.drawnNumbers())).toArray();
            int trainingCount = allMasks.length < 2 ? allMasks.length
                    : Math.max(1, Math.min(allMasks.length - 1, (int) (allMasks.length * TRAINING_PROPORTION)));
            this.trainingMasks = Arrays.copyOfRange(allMasks, 0, trainingCount);
            this.validationMasks = Arrays.copyOfRange(allMasks, trainingCount, allMasks.length);
            this.trainingLastContestNumber = trainingCount == 0 ? 0 : sorted.get(trainingCount - 1).number();
        }

        private BacktestingHistory(List<Contest> training, List<Contest> validation) {
            List<Contest> sortedTraining = training.stream().sorted(Comparator.comparingInt(Contest::number)).toList();
            this.trainingMasks = sortedTraining.stream().mapToInt(contest -> mask(contest.drawnNumbers())).toArray();
            this.validationMasks = validation.stream().sorted(Comparator.comparingInt(Contest::number))
                    .mapToInt(contest -> mask(contest.drawnNumbers())).toArray();
            this.allMasks = java.util.stream.IntStream.concat(Arrays.stream(trainingMasks), Arrays.stream(validationMasks)).toArray();
            this.trainingLastContestNumber = sortedTraining.isEmpty() ? 0
                    : sortedTraining.getLast().number();
        }

        public int contestCount() { return allMasks.length; }
        public int trainingLastContestNumber() { return trainingLastContestNumber; }
        public int trainingContestCount() { return trainingMasks.length; }
        public int validationContestCount() { return validationMasks.length; }
    }
}
