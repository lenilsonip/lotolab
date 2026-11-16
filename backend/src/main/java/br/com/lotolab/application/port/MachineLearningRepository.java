package br.com.lotolab.application.port;

import br.com.lotolab.domain.LogisticModelArtifact;
import br.com.lotolab.domain.MlExperimentalPrediction;
import br.com.lotolab.domain.MlEvaluationSlice;
import br.com.lotolab.domain.MlModelSummary;
import br.com.lotolab.domain.MlTrainingJob;
import br.com.lotolab.domain.StoredMlModel;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MachineLearningRepository {
    MlTrainingJob enqueue(String idempotencyKey, int cutoffExclusive, String datasetChecksum, long seed);
    Optional<MlTrainingJob> claimNext(String workerId, Instant leaseUntil);
    void failJob(long jobId, String workerId, String safeCategory, Instant retryAt);
    StoredMlModel completeJob(long jobId, String workerId, LogisticModelArtifact artifact,
                              String artifactJson, String artifactChecksum, List<MlEvaluationSlice> evaluations);
    Optional<MlTrainingJob> findJob(long id);
    List<MlModelSummary> listModels();
    Optional<StoredMlModel> findModel(long id);
    MlExperimentalPrediction savePrediction(long modelId, int modelVersion, int targetContest,
                                            int cutoffExclusive, List<Integer> numbers,
                                            List<Double> rankingScores, List<Integer> baselineNumbers, long seed);
}
