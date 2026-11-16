package br.com.lotolab.api;

import br.com.lotolab.application.ComparePredictionService;
import br.com.lotolab.application.FeedbackPromptService;
import br.com.lotolab.application.port.AiFeedbackGenerator;
import br.com.lotolab.domain.PredictionComparison;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

@RestController
@RequestMapping("/api/experimentos")
public class ExperimentController {

    private final ComparePredictionService comparePredictionService;
    private final FeedbackPromptService feedbackPromptService;
    private final AiFeedbackGenerator aiFeedbackGenerator;

    public ExperimentController(ComparePredictionService comparePredictionService,
                                FeedbackPromptService feedbackPromptService,
                                AiFeedbackGenerator aiFeedbackGenerator) {
        this.comparePredictionService = Objects.requireNonNull(comparePredictionService);
        this.feedbackPromptService = Objects.requireNonNull(feedbackPromptService);
        this.aiFeedbackGenerator = Objects.requireNonNull(aiFeedbackGenerator);
    }

    @PostMapping("/analise")
    public ExperimentAnalysisResponse analyze(@Valid @RequestBody ExperimentAnalysisRequest request) {
        PredictionComparison comparison = comparePredictionService.compare(
                request.predictedNumbers(), request.actualContestNumber());
        String promptText = feedbackPromptService.createPrompt(comparison);
        String aiFeedback = aiFeedbackGenerator.isAvailable()
                ? aiFeedbackGenerator.generateFeedback(promptText) : "";
        return new ExperimentAnalysisResponse(promptText, aiFeedback);
    }
}
