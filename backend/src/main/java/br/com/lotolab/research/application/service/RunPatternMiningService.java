package br.com.lotolab.research.application.service;

import br.com.lotolab.domain.Contest;
import br.com.lotolab.domain.PatternInsight;
import br.com.lotolab.research.application.port.in.MinePatternsCommand;
import br.com.lotolab.research.application.port.in.MinePatternsUseCase;
import br.com.lotolab.research.application.port.in.PatternMiningResult;
import br.com.lotolab.research.application.port.out.PatternMiningCompletionPublisher;
import br.com.lotolab.research.application.port.out.PatternResearchRepository;
import br.com.lotolab.research.application.port.out.PatternResearchUnitOfWork;
import br.com.lotolab.research.application.port.out.ResearchContestHistory;
import br.com.lotolab.research.domain.PatternMiningAlgorithm;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;

/** Coordinates one atomic, deterministic walk-forward run. */
public final class RunPatternMiningService implements MinePatternsUseCase {
    private static final String AGENT_KEY = "PATTERN_RESEARCHER";
    private final ResearchContestHistory history;
    private final PatternResearchRepository runs;
    private final PatternResearchUnitOfWork unitOfWork;
    private final PatternMiningCompletionPublisher completionPublisher;
    private final PatternMiningAlgorithm algorithm;

    public RunPatternMiningService(ResearchContestHistory history, PatternResearchRepository runs,
                                   PatternResearchUnitOfWork unitOfWork,
                                   PatternMiningCompletionPublisher completionPublisher,
                                   PatternMiningAlgorithm algorithm) {
        this.history = Objects.requireNonNull(history);
        this.runs = Objects.requireNonNull(runs);
        this.unitOfWork = Objects.requireNonNull(unitOfWork);
        this.completionPublisher = Objects.requireNonNull(completionPublisher);
        this.algorithm = Objects.requireNonNull(algorithm);
    }

    @Override
    public PatternMiningResult execute(MinePatternsCommand command) {
        Objects.requireNonNull(command, "command is required");
        return unitOfWork.execute(() -> mineWithinTransaction(command));
    }

    private PatternMiningResult mineWithinTransaction(MinePatternsCommand command) {
        List<Contest> contests = history.loadAll().stream()
                .sorted(Comparator.comparingInt(Contest::number)).toList();
        if (contests.size() < 3) {
            return new PatternMiningResult(PatternMiningResult.Outcome.INSUFFICIENT_HISTORY,
                    0, null, PatternMiningAlgorithm.ALGORITHM_VERSION, null, null, contests.size(), 0);
        }

        int first = contests.getFirst().number();
        int last = contests.getLast().number();
        String idempotencyKey = "pattern-miner:" + PatternMiningAlgorithm.ALGORITHM_VERSION + ":" + first + ":" + last;
        OptionalLong runId = runs.startRun(AGENT_KEY, PatternMiningAlgorithm.ALGORITHM_VERSION,
                idempotencyKey, first, last);
        if (runId.isEmpty()) {
            return new PatternMiningResult(PatternMiningResult.Outcome.ALREADY_COMPLETED,
                    0, null, PatternMiningAlgorithm.ALGORITHM_VERSION, first, last, contests.size(), 0);
        }

        int trainingEnd = Math.max(2, contests.size() / 2);
        int validationBlock = Math.max(1, contests.size() / 10);
        int saved = 0;
        int fold = 0;
        while (trainingEnd < contests.size()) {
            int validationEnd = Math.min(contests.size(), trainingEnd + validationBlock);
            List<Contest> training = contests.subList(0, trainingEnd);
            List<Contest> validation = contests.subList(trainingEnd, validationEnd);
            List<PatternInsight> insights = algorithm.evaluateFold(runId.getAsLong(), ++fold,
                    training, validation, deterministicSeed(first, last, fold));
            for (PatternInsight insight : insights) {
                runs.save(runId.getAsLong(), insight);
                saved++;
            }
            trainingEnd = validationEnd;
        }

        runs.completeRun(runId.getAsLong());
        completionPublisher.publish(runId.getAsLong(), command.origin());
        return new PatternMiningResult(PatternMiningResult.Outcome.COMPLETED, saved, runId.getAsLong(),
                PatternMiningAlgorithm.ALGORITHM_VERSION, first, last, contests.size(), fold);
    }

    private static long deterministicSeed(int first, int last, int fold) {
        return (((long) first << 32) ^ last) + fold * 104729L;
    }
}
