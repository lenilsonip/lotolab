package br.com.lotolab.api;

import br.com.lotolab.application.OptimizedGameGeneratorService;
import br.com.lotolab.application.SavePredictionAttemptService;
import br.com.lotolab.application.AgentInterpretationService;
import br.com.lotolab.domain.GameGenerationCriteria;
import br.com.lotolab.infrastructure.logging.LogContext;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

@RestController
@RequestMapping("/api/experimentos")
public class OptimizedGameController {
    private static final Logger LOGGER = LoggerFactory.getLogger(OptimizedGameController.class);
    private final OptimizedGameGeneratorService service;
    private final SavePredictionAttemptService saveAttemptService;
    private final AgentInterpretationService interpretationService;

    public OptimizedGameController(OptimizedGameGeneratorService service, SavePredictionAttemptService saveAttemptService,
                                   AgentInterpretationService interpretationService) {
        this.service = Objects.requireNonNull(service);
        this.saveAttemptService = Objects.requireNonNull(saveAttemptService);
        this.interpretationService = Objects.requireNonNull(interpretationService);
    }

    @PostMapping("/gerar-otimizado")
    public OptimizedGameGenerationResponse generate(@Valid @RequestBody OptimizedGameGenerationRequest request) {
        long startedAt = System.nanoTime();
        try (LogContext ignored = LogContext.agentRun("Agent2", "optimized-game-generation", "MANUAL")) {
            LogContext.phase("START");
            LOGGER.info("targetContest={} evenNumbers={} oddNumbers={} seedPresent={}", request.targetContestNumber(),
                    request.evenNumbers(), request.oddNumbers(), request.seed() != null);
            try {
        GameGenerationCriteria criteria = new GameGenerationCriteria(1, request.evenNumbers(),
                request.oddNumbers(), request.seed());
        var recommendation = service.generateForTarget(criteria, request.targetContestNumber());
        var generated = saveAttemptService.saveGeneratedAndQueue(recommendation, request.targetContestNumber() != null);
        var attempt = generated.attempt();
        var interpretation = interpretationService.predictionInterpretation(attempt.id()).orElseThrow();
                LogContext.phase("SUCCESS");
                LOGGER.info("targetContest={} attemptId={} drawnNumberCount={} trainingScore={} validationScore={} baselineScore={} evidenceCount={} foldCount={} durationMs={}",
                        recommendation.targetContestNumber(), attempt.id(), recommendation.result().game().numbers().size(),
                        recommendation.result().trainingFitnessScore(), recommendation.result().fitnessScore(),
                        recommendation.baselineFitnessScore(), recommendation.evidenceIds().size(),
                        recommendation.folds().size(), (System.nanoTime() - startedAt) / 1_000_000L);
        return OptimizedGameGenerationResponse.from(criteria, recommendation, attempt, interpretation,
                generated.interpretationJobId(),
                request.targetContestNumber() != null);
            } catch (RuntimeException | Error exception) {
                LogContext.phase("ERROR");
                LOGGER.error("cause={} durationMs={}", exception.getClass().getSimpleName(),
                        (System.nanoTime() - startedAt) / 1_000_000L, exception);
                throw exception;
            }
        }
    }
}
