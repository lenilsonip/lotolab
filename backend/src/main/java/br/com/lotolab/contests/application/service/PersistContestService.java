package br.com.lotolab.contests.application.service;

import br.com.lotolab.contests.application.ContestResultPersistedEvent;
import br.com.lotolab.contests.application.port.in.PersistContestUseCase;
import br.com.lotolab.contests.application.port.out.ContestRepository;
import br.com.lotolab.contests.application.port.out.ContestResultEventPublisher;
import br.com.lotolab.contests.application.port.out.ContestUnitOfWork;
import br.com.lotolab.contests.application.port.out.PendingPredictionEvaluator;
import br.com.lotolab.domain.Contest;

import java.util.Objects;

/** Atomically persists a new result, evaluates due attempts and publishes its transactional event. */
public final class PersistContestService implements PersistContestUseCase {
    private final ContestRepository contests;
    private final PendingPredictionEvaluator predictionEvaluator;
    private final ContestResultEventPublisher eventPublisher;
    private final ContestUnitOfWork unitOfWork;

    public PersistContestService(ContestRepository contests,
                                 PendingPredictionEvaluator predictionEvaluator,
                                 ContestResultEventPublisher eventPublisher,
                                 ContestUnitOfWork unitOfWork) {
        this.contests = Objects.requireNonNull(contests);
        this.predictionEvaluator = Objects.requireNonNull(predictionEvaluator);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
        this.unitOfWork = Objects.requireNonNull(unitOfWork);
    }

    @Override
    public boolean persist(Contest contest) {
        Objects.requireNonNull(contest, "Contest is required");
        return unitOfWork.execute(() -> {
            boolean created = contests.saveIfAbsent(contest);
            if (created) {
                predictionEvaluator.evaluateForContest(contest.number());
                eventPublisher.publish(new ContestResultPersistedEvent(contest.number()));
            }
            return created;
        });
    }
}
