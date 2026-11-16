package br.com.lotolab.api;

import br.com.lotolab.application.GenerateLotofacilGamesService;
import br.com.lotolab.application.LearningPolicyService;
import br.com.lotolab.application.OptimizedGameGeneratorService;
import br.com.lotolab.application.SavePredictionAttemptService;
import br.com.lotolab.application.port.ContestRepository;
import br.com.lotolab.domain.GameGenerationCriteria;
import br.com.lotolab.domain.LearningModel;
import br.com.lotolab.infrastructure.logging.LogContext;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Generates according to the persisted champion; uniform remains the default until evidence promotes a candidate. */
@RestController
@RequestMapping("/api/experimentos")
public class PolicyGameController {
    private final LearningPolicyService policy;
    private final GenerateLotofacilGamesService uniform;
    private final OptimizedGameGeneratorService optimized;
    private final SavePredictionAttemptService attempts;
    private final ContestRepository contests;
    private final br.com.lotolab.application.AgentInterpretationService interpretations;

    public PolicyGameController(LearningPolicyService policy, GenerateLotofacilGamesService uniform,
                                OptimizedGameGeneratorService optimized, SavePredictionAttemptService attempts,
                                ContestRepository contests, br.com.lotolab.application.AgentInterpretationService interpretations) {
        this.policy = policy; this.uniform = uniform; this.optimized = optimized; this.attempts = attempts;
        this.contests = contests; this.interpretations = interpretations;
    }

    @PostMapping("/gerar-politica")
    public PolicyGameResponse generate(@Valid @RequestBody OptimizedGameGenerationRequest request) {
        LearningModel champion = policy.champion();
        int latest = contests.findLatest().map(c -> c.number()).orElse(0);
        int target = request.targetContestNumber() == null ? latest + 1 : request.targetContestNumber();
        if (target <= latest) throw new IllegalArgumentException("O concurso-alvo deve ser posterior ao último concurso conhecido");
        var criteria = new GameGenerationCriteria(1, request.evenNumbers(), request.oddNumbers(), request.seed());
        boolean overridden = request.targetContestNumber() != null;
        if ("MUTATION_OPTIMIZER".equals(champion.strategyKey())) {
            var recommendation = optimized.generateForTarget(criteria, target);
            var generated = attempts.saveGeneratedAndQueue(recommendation, overridden);
            var interpretation = interpretations.predictionInterpretation(generated.attempt().id()).orElseThrow();
            return new PolicyGameResponse(recommendation.result().game().numbers(), generated.attempt().id(),
                    target, champion.version(), champion.strategyKey(), recommendation.performanceStatus(),
                    recommendation.result().fitnessScore(), generated.interpretationJobId(), interpretation.status(),
                    "A política só muda após feedback prospectivo; score retrospectivo não é probabilidade de prêmio.");
        }
        List<Integer> game = uniform.generate(criteria).getFirst().numbers();
        var saved = attempts.savePolicyGenerated(target, game, overridden, "policy-v" + champion.version());
        return new PolicyGameResponse(game, saved.id(), target, champion.version(), champion.strategyKey(),
                "NO_DEMONSTRATED_ADVANTAGE", null, null, "NOT_REQUESTED",
                "Champion uniforme; resultados passados não demonstram vantagem sobre sorteios independentes.");
    }

    public record PolicyGameResponse(List<Integer> game, long predictionId, int targetContestNumber,
                                     int modelVersion, String strategyKey, String evaluationStatus,
                                     Long experimentalScore, Long interpretationJobId, String interpretationStatus,
                                     String statisticalNotice) {
        public PolicyGameResponse { game = List.copyOf(game); }
    }
}
