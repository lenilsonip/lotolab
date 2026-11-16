package br.com.lotolab.domain;

import java.util.List;

/** Auditable optimizer result; evidence is restricted to insights known before the target contest. */
public record AgentGameRecommendation(int targetContestNumber, OptimizedGameResult result,
                                      int sourceCutoffContest,
                                      String evidenceStatus, List<Long> evidenceIds,
                                      String strategyVersion, long baselineFitnessScore,
                                      int baselineContestCount, long baselineSeed, int candidateBudget,
                                      String performanceStatus, long walkForwardStrategyScore,
                                      long walkForwardBaselineScore, java.util.List<StrategyWalkForwardFold> folds) {
    public AgentGameRecommendation {
        if (targetContestNumber <= 0 || sourceCutoffContest < 0 || sourceCutoffContest >= targetContestNumber)
            throw new IllegalArgumentException("Concurso-alvo ou corte temporal inválido");
        if (result == null) throw new IllegalArgumentException("Resultado é obrigatório");
        if (baselineFitnessScore < 0 || baselineContestCount < 0 || candidateBudget <= 0)
            throw new IllegalArgumentException("Métricas da baseline inválidas");
        evidenceIds = List.copyOf(evidenceIds);
        evidenceStatus = evidenceStatus == null ? "NO_ELIGIBLE_EVIDENCE" : evidenceStatus;
        strategyVersion = strategyVersion == null ? "mutation-1" : strategyVersion;
        performanceStatus = performanceStatus == null ? "NOT_EVALUATED" : performanceStatus;
        folds = java.util.List.copyOf(folds);
    }
}
