package br.com.lotolab.domain;

public record BacktestingResult(LotofacilGame game, long fitnessScore, int contestsEvaluated) {
    public BacktestingResult {
        if (game == null) throw new IllegalArgumentException("O jogo avaliado é obrigatório");
        if (fitnessScore < 0 || contestsEvaluated < 0) {
            throw new IllegalArgumentException("Pontuação e concursos avaliados não podem ser negativos");
        }
    }
}
