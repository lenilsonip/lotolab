package br.com.lotolab.application;

import br.com.lotolab.application.port.PredictionAttemptRepository;
import br.com.lotolab.domain.PredictionAttempt;
import br.com.lotolab.domain.PredictionAttemptView;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SavePredictionAttemptService {
    private final PredictionAttemptRepository repository;
    private AgentInterpretationService interpretationService;

    public SavePredictionAttemptService(PredictionAttemptRepository repository) {
        this.repository = Objects.requireNonNull(repository);
        this.interpretationService = null;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setInterpretationService(AgentInterpretationService interpretationService) {
        this.interpretationService = interpretationService;
    }

    public PredictionAttempt save(Integer targetContestNumber, List<Integer> predictedNumbers) {
        if (targetContestNumber == null) {
            throw new IllegalArgumentException("O número do concurso alvo é obrigatório");
        }
        PredictionAttempt attempt = PredictionAttempt.newAttempt(targetContestNumber, predictedNumbers);
        return repository.save(attempt);
    }

    public PredictionAttempt saveGenerated(Integer targetContestNumber, List<Integer> predictedNumbers,
                                           int sourceCutoffContest, boolean targetWasOverridden, String strategyVersion,
                                           long experimentalScore, Long baselineScore, Long baselineSeed,
                                           int candidateBudget, String evidenceStatus, String performanceStatus,
                                           List<br.com.lotolab.domain.StrategyWalkForwardFold> folds,
                                           List<Long> evidenceIds) {
        if (targetContestNumber == null || targetContestNumber <= 0) {
            throw new IllegalArgumentException("O número do concurso alvo deve ser positivo");
        }
        PredictionAttempt attempt = PredictionAttempt.newAttempt(targetContestNumber, predictedNumbers);
        return repository.saveGenerated(attempt, sourceCutoffContest, targetWasOverridden, strategyVersion,
                experimentalScore, baselineScore, baselineSeed, candidateBudget, evidenceStatus, performanceStatus,
                folds, evidenceIds);
    }

    @Transactional
    public GeneratedPrediction saveGeneratedAndQueue(br.com.lotolab.domain.AgentGameRecommendation recommendation,
                                                       boolean targetWasOverridden) {
        if (interpretationService == null) throw new IllegalStateException("Fila de interpretação não configurada");
        var attempt = saveGenerated(recommendation.targetContestNumber(), recommendation.result().game().numbers(),
                recommendation.sourceCutoffContest(), targetWasOverridden, recommendation.strategyVersion(),
                recommendation.result().fitnessScore(), recommendation.baselineFitnessScore(), recommendation.baselineSeed(),
                recommendation.candidateBudget(), recommendation.evidenceStatus(), recommendation.performanceStatus(),
                recommendation.folds(), recommendation.evidenceIds());
        var job = interpretationService.enqueuePredictionInterpretation(attempt, recommendation, "MANUAL");
        return new GeneratedPrediction(attempt, job.id());
    }

    @Transactional
    public PredictionAttempt savePolicyGenerated(int targetContestNumber, List<Integer> numbers,
                                                 boolean targetWasOverridden, String modelVersion) {
        return repository.savePolicyGenerated(PredictionAttempt.newAttempt(targetContestNumber, numbers),
                targetWasOverridden, modelVersion);
    }

    public record GeneratedPrediction(PredictionAttempt attempt, long interpretationJobId) { }

    public List<PredictionAttemptView> findAll() { return repository.findAllRecords(); }
}
