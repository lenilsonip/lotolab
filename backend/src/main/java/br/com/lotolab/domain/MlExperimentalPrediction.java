package br.com.lotolab.domain;

import java.time.Instant;
import java.util.List;

public record MlExperimentalPrediction(long id, long modelId, int modelVersion, int targetContestNumber,
                                       int trainingCutoffExclusive, List<Integer> numbers,
                                       List<Integer> uniformBaselineNumbers, long seed, Instant createdAt) {
    public MlExperimentalPrediction {
        numbers = new LotofacilGame(numbers).numbers();
        uniformBaselineNumbers = new LotofacilGame(uniformBaselineNumbers).numbers();
        if (targetContestNumber < trainingCutoffExclusive || targetContestNumber <= 0) {
            throw new IllegalArgumentException("Previsão anterior ao cutoff do modelo");
        }
    }
}
