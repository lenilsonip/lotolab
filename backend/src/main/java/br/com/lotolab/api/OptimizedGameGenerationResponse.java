package br.com.lotolab.api;

import br.com.lotolab.domain.GameGenerationCriteria;
import br.com.lotolab.domain.OptimizedGameResult;
import br.com.lotolab.domain.AgentGameRecommendation;
import br.com.lotolab.domain.AgentInterpretation;
import br.com.lotolab.domain.PredictionAttempt;
import br.com.lotolab.domain.StrategyWalkForwardFold;

import java.util.List;

public record OptimizedGameGenerationResponse(
        List<Integer> game,
        long fitnessScore,
        int contestsEvaluated,
        int iterations,
        long trainingFitnessScore,
        int trainingContestsEvaluated,
        int duplicateMutationsSkipped,
        boolean searchExhausted,
        Criteria criteria,
        String statisticalNotice,
        Long predictionId,
        int targetContestNumber,
        int sourceCutoffContest,
        boolean targetWasOverridden,
        String strategyVersion,
        String evidenceStatus,
        String performanceStatus,
        List<Long> evidenceIds,
        long randomBaselineFitnessScore,
        int randomBaselineContestsEvaluated,
        int candidateBudget,
        long randomBaselineSeed,
        long walkForwardStrategyScore,
        long walkForwardBaselineScore,
        List<WalkForwardFoldResponse> walkForwardFolds,
        String aiInterpretationStatus, String aiSummary, List<String> aiHypotheses, String aiExplanation,
        long interpretationJobId) {
    public OptimizedGameGenerationResponse {
        game = List.copyOf(game);
        walkForwardFolds = List.copyOf(walkForwardFolds);
        aiHypotheses = aiHypotheses == null ? List.of() : List.copyOf(aiHypotheses);
    }

    static OptimizedGameGenerationResponse from(GameGenerationCriteria criteria, AgentGameRecommendation recommendation,
                                                 PredictionAttempt attempt, AgentInterpretation interpretation,
                                                 long interpretationJobId,
                                                 boolean targetWasOverridden) {
        OptimizedGameResult result = recommendation.result();
        return new OptimizedGameGenerationResponse(result.game().numbers(), result.fitnessScore(),
                result.contestsEvaluated(), result.iterations(), result.trainingFitnessScore(),
                result.trainingContestsEvaluated(), result.duplicateMutationsSkipped(), result.searchExhausted(),
                new Criteria(criteria.evenNumbers(), criteria.oddNumbers(), criteria.seed()),
                "FitnessScore é medido em concursos posteriores separados para validação e não usados para escolher mutações. "
                        + "A pontuação não indica maior probabilidade de prêmio em sorteios futuros.",
                attempt.id(), recommendation.targetContestNumber(), recommendation.sourceCutoffContest(), targetWasOverridden,
                recommendation.strategyVersion(), recommendation.evidenceStatus(), recommendation.performanceStatus(), recommendation.evidenceIds(),
                recommendation.baselineFitnessScore(), recommendation.baselineContestCount(),
                recommendation.candidateBudget(), recommendation.baselineSeed(),
                recommendation.walkForwardStrategyScore(), recommendation.walkForwardBaselineScore(),
                recommendation.folds().stream().map(WalkForwardFoldResponse::from).toList(),
                interpretation.status(), interpretation.summary(), interpretation.hypotheses(), interpretation.explanation(),
                interpretationJobId);
    }

    public record Criteria(Integer evenNumbers, Integer oddNumbers, Long seed) { }
    public record WalkForwardFoldResponse(int foldNumber, int trainingFirstContest, int trainingLastContest,
                                          int validationFirstContest, int validationLastContest,
                                          int trainingSampleSize, int validationSampleSize, long strategyScore,
                                          long randomBaselineScore, long randomSeed, int candidateBudget) {
        static WalkForwardFoldResponse from(StrategyWalkForwardFold fold) {
            return new WalkForwardFoldResponse(fold.foldNumber(), fold.trainingFirstContest(), fold.trainingLastContest(),
                    fold.validationFirstContest(), fold.validationLastContest(), fold.trainingSampleSize(),
                    fold.validationSampleSize(), fold.strategyScore(), fold.randomBaselineScore(),
                    fold.randomSeed(), fold.candidateBudget());
        }
    }
}
