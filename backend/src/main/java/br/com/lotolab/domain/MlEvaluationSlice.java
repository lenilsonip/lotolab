package br.com.lotolab.domain;

import java.util.List;

public record MlEvaluationSlice(String split, int trainingFirstContest, int trainingLastContest,
                               int targetFirstContest, int targetLastContest, int sampleCount,
                               double meanModelHits, double meanUniformHits, double meanPairedDifference,
                               double standardError, double confidence95Lower, double confidence95Upper,
                               List<Integer> modelHitDistribution, List<Integer> uniformHitDistribution,
                               long seed) {
    public MlEvaluationSlice {
        if (split == null || !List.of("TRAIN_DIAGNOSTIC", "VALIDATION", "HOLDOUT").contains(split)
                || sampleCount < 0 || !Double.isFinite(meanModelHits) || !Double.isFinite(meanUniformHits)
                || !Double.isFinite(meanPairedDifference) || !Double.isFinite(standardError)
                || !Double.isFinite(confidence95Lower) || !Double.isFinite(confidence95Upper)) {
            throw new IllegalArgumentException("Métricas de avaliação inválidas");
        }
        modelHitDistribution = List.copyOf(modelHitDistribution);
        uniformHitDistribution = List.copyOf(uniformHitDistribution);
        if (modelHitDistribution.size() != 16 || uniformHitDistribution.size() != 16) {
            throw new IllegalArgumentException("Distribuições devem conter acertos de 0 a 15");
        }
    }
}
