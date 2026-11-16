package br.com.lotolab.api;

import br.com.lotolab.application.MachineLearningService;

import java.time.Instant;
import java.util.List;

public record MlExperimentalPredictionResponse(long predictionId, long modelId, int modelVersion,
                                               int targetContestNumber, int trainingCutoffExclusive,
                                               List<Integer> numbers, List<Integer> uniformBaselineNumbers,
                                               List<Double> rankingScores, long seed, Instant createdAt,
                                               String status, String notice) {
    public MlExperimentalPredictionResponse { numbers=List.copyOf(numbers); uniformBaselineNumbers=List.copyOf(uniformBaselineNumbers); rankingScores=List.copyOf(rankingScores); }
    public static MlExperimentalPredictionResponse from(MachineLearningService.ExperimentalPrediction value) {
        var prediction = value.prediction();
        return new MlExperimentalPredictionResponse(prediction.id(), prediction.modelId(), prediction.modelVersion(),
                prediction.targetContestNumber(), prediction.trainingCutoffExclusive(), prediction.numbers(),
                prediction.uniformBaselineNumbers(), value.rankingScores(), prediction.seed(), prediction.createdAt(),
                "EXPERIMENTAL", value.notice());
    }
}
