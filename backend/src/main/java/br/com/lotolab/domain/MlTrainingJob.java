package br.com.lotolab.domain;

import java.time.Instant;

public record MlTrainingJob(long id, String status, int trainingCutoffExclusive, String datasetChecksum,
                            long seed, int attempts, int maxAttempts, Long modelId,
                            String errorCategory, Instant createdAt, Instant updatedAt) { }
