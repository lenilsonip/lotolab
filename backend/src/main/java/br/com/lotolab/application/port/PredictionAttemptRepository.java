package br.com.lotolab.application.port;

import br.com.lotolab.domain.PredictionAttempt;
import br.com.lotolab.domain.PredictionAttemptView;

public interface PredictionAttemptRepository {
    PredictionAttempt save(PredictionAttempt attempt);

    PredictionAttempt saveGenerated(PredictionAttempt attempt, int sourceCutoffContest, boolean targetWasOverridden,
                                    String strategyVersion, long experimentalScore, Long baselineScore, Long baselineSeed,
                                    int candidateBudget,
                                    String evidenceStatus, String performanceStatus,
                                    java.util.List<br.com.lotolab.domain.StrategyWalkForwardFold> folds,
                                    java.util.List<Long> evidenceIds);

    PredictionAttempt savePolicyGenerated(PredictionAttempt attempt, boolean targetWasOverridden, String strategyVersion);

    java.util.List<PredictionAttemptView> findAllRecords();
}
