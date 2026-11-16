package br.com.lotolab.domain;

import java.time.LocalDateTime;
import java.util.List;

/** A predicted game registered for a future or already drawn contest. */
public record PredictionAttempt(Long id, int targetContestNumber, LocalDateTime createdAt,
                                List<Integer> predictedNumbers) {

    public PredictionAttempt {
        if (targetContestNumber <= 0) {
            throw new IllegalArgumentException("O número do concurso alvo deve ser positivo");
        }
        if (id != null && id <= 0) {
            throw new IllegalArgumentException("O identificador da previsão deve ser positivo");
        }
        predictedNumbers = new LotofacilGame(predictedNumbers).numbers();
    }

    public static PredictionAttempt newAttempt(int targetContestNumber, List<Integer> predictedNumbers) {
        return new PredictionAttempt(null, targetContestNumber, null, predictedNumbers);
    }
}
