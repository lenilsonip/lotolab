package br.com.lotolab.application;

import br.com.lotolab.application.port.LearningPolicyRepository;
import br.com.lotolab.contests.application.ContestResultPersistedEvent;
import br.com.lotolab.domain.LearningModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

import java.util.Objects;

/** Updates a candidate only from completed prospective outcomes; the uniform model remains champion absent evidence. */
@Service
public class LearningPolicyService {
    private static final double NULL_MEAN = 9.0;
    private static final double Z_95 = 1.96;
    private static final int MIN_SAMPLES = 100;
    private final LearningPolicyRepository repository;

    public LearningPolicyService(LearningPolicyRepository repository) { this.repository = Objects.requireNonNull(repository); }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onRealContestPersisted(ContestResultPersistedEvent event) {
        observeContest(event.contestNumber());
    }

    @Transactional
    public int observeContest(int contestNumber) {
        int updated = 0;
        for (var outcome : repository.findUnlearnedOutcomes(contestNumber)) {
            LearningModel champion = Objects.requireNonNull(repository.champion(), "Champion inicial ausente");
            LearningModel prior = repository.candidate();
            int oldN = prior == null ? 0 : prior.sampleSize();
            double oldMean = prior == null ? 9.0 : prior.meanHits();
            double oldVariance = prior == null ? 1.5 : prior.varianceHits();
            int n = oldN + 1;
            double delta = outcome.hits() - oldMean;
            double mean = oldMean + delta / n;
            double m2 = Math.max(0, oldVariance * (oldN == 0 ? 0 : Math.max(1, oldN - 1))
                    + delta * (outcome.hits() - mean));
            double variance = n > 1 ? m2 / (n - 1) : 1.5;
            double margin = Z_95 * Math.sqrt(variance / n);
            double lower = mean - margin, upper = mean + margin;
            String decision = "RETAINED";
            String reason = n < MIN_SAMPLES ? "INSUFFICIENT_PROSPECTIVE_SAMPLE"
                    : "AUTOMATIC_PROMOTION_DISABLED_REQUIRES_PAIRED_WALK_FORWARD_REVIEW";
            String status = "CANDIDATE";
            String strategy = "MUTATION_OPTIMIZER";
            Long after = null;
            // This single-arm hit-rate interval is not a paired prospective comparison and
            // cannot promote any strategy. Promotion remains a deliberate human decision.
            if (prior != null) repository.changeStatus(prior.id(), "REJECTED");
            Integer first = prior == null ? outcome.contestNumber() : prior.firstContest();
            LearningModel next = repository.createVersion(status, strategy, n, mean, variance, first,
                    outcome.contestNumber(), reason);
            if ("PROMOTED".equals(decision)) after = next.id();
            repository.record(outcome.attemptId(), outcome.contestNumber(), outcome.modelVersionId(), after, outcome.hits(),
                    n, oldMean, mean, lower, upper, decision, reason);
            updated++;
        }
        return updated;
    }

    public LearningModel champion() { return repository.champion(); }
    public LearningModel candidate() { return repository.candidate(); }
    public java.util.List<br.com.lotolab.domain.LearningEventSummary> latestEvents() { return repository.latestEvents(50); }
}
