package br.com.lotolab.application.port;

import br.com.lotolab.domain.LearningModel;
import br.com.lotolab.domain.PredictionOutcome;

import java.util.List;

public interface LearningPolicyRepository {
    List<PredictionOutcome> findUnlearnedOutcomes(int contestNumber);
    LearningModel champion();
    LearningModel candidate();
    LearningModel createVersion(String status, String strategy, int samples, double mean, double variance,
                                Integer firstContest, Integer lastContest, String reason);
    void changeStatus(long modelId, String status);
    void record(long attemptId, int contestNumber, long beforeModel, Long afterModel, int observedHits,
                int sampleSize, double meanBefore, double meanAfter, double lower95, double upper95,
                String decision, String reason);
    java.util.List<br.com.lotolab.domain.LearningEventSummary> latestEvents(int limit);
}
