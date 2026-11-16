package br.com.lotolab.domain;

public record OptimizedGameResult(
        LotofacilGame game,
        long fitnessScore,
        int contestsEvaluated,
        int iterations,
        long trainingFitnessScore,
        int trainingContestsEvaluated,
        int duplicateMutationsSkipped,
        boolean searchExhausted) {

    public OptimizedGameResult {
        if (game == null) throw new IllegalArgumentException("O jogo otimizado é obrigatório");
        if (fitnessScore < 0 || contestsEvaluated < 0 || iterations < 0 || trainingFitnessScore < 0
                || trainingContestsEvaluated < 0 || duplicateMutationsSkipped < 0) {
            throw new IllegalArgumentException("Métricas do backtesting não podem ser negativas");
        }
    }
}
