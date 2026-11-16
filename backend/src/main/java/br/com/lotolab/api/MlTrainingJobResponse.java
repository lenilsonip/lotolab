package br.com.lotolab.api;

import br.com.lotolab.domain.MlTrainingJob;

import java.time.Instant;

public record MlTrainingJobResponse(long id, String status, int trainingCutoffExclusive,
                                    String datasetChecksum, long seed, int attempts, int maxAttempts,
                                    Long modelId, String errorCategory, Instant createdAt, Instant updatedAt) {
    public static MlTrainingJobResponse from(MlTrainingJob value) {
        return new MlTrainingJobResponse(value.id(), value.status(), value.trainingCutoffExclusive(),
                value.datasetChecksum(), value.seed(), value.attempts(), value.maxAttempts(), value.modelId(),
                value.errorCategory(), value.createdAt(), value.updatedAt());
    }
}
