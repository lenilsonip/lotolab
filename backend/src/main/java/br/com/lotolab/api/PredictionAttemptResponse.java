package br.com.lotolab.api;

import br.com.lotolab.domain.PredictionAttempt;

import java.time.LocalDateTime;
import java.util.List;

public record PredictionAttemptResponse(Long id, int targetContestNumber,
                                        LocalDateTime createdAt, List<Integer> predictedNumbers) {
    public PredictionAttemptResponse {
        predictedNumbers = List.copyOf(predictedNumbers);
    }

    static PredictionAttemptResponse from(PredictionAttempt attempt) {
        return new PredictionAttemptResponse(attempt.id(), attempt.targetContestNumber(),
                attempt.createdAt(), attempt.predictedNumbers());
    }
}
