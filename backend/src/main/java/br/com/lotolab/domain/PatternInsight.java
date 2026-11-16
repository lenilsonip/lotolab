package br.com.lotolab.domain;

import java.math.BigDecimal;
import java.util.Objects;

/** Immutable, auditable observation or temporal validation of a pattern hypothesis. */
public record PatternInsight(
        Long id,
        Long agentRunId,
        String hypothesisKey,
        String algorithmVersion,
        String patternType,
        String description,
        BigDecimal confidenceScore,
        Integer trainingFirstContest,
        Integer trainingLastContest,
        Integer validationFirstContest,
        Integer validationLastContest,
        Integer sampleSize,
        BigDecimal observedMetric,
        BigDecimal baselineMetric,
        String evidenceStatus,
        String evidenceJson) {

    public PatternInsight(String patternType, String description, BigDecimal confidenceScore) {
        this(null, null, null, null, patternType, description, confidenceScore,
                null, null, null, null, null, null, null, "LEGACY_UNASSESSED", "{}");
    }

    public PatternInsight {
        if (patternType == null || patternType.isBlank() || patternType.length() > 80) {
            throw new IllegalArgumentException("O tipo do padrão deve conter entre 1 e 80 caracteres");
        }
        if (description == null || description.isBlank()) throw new IllegalArgumentException("A descrição é obrigatória");
        Objects.requireNonNull(confidenceScore, "O score de confiança é obrigatório");
        if (confidenceScore.compareTo(BigDecimal.ZERO) < 0 || confidenceScore.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("O score de confiança deve estar entre 0 e 1");
        }
        if (sampleSize != null && sampleSize < 0) throw new IllegalArgumentException("A amostra não pode ser negativa");
        evidenceStatus = evidenceStatus == null ? "LEGACY_UNASSESSED" : evidenceStatus;
        evidenceJson = evidenceJson == null ? "{}" : evidenceJson;
    }
}
