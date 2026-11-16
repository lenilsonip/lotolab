package br.com.lotolab.api;

import br.com.lotolab.application.MachineLearningService;
import br.com.lotolab.application.MlTrainingJobNotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/ml")
public class MachineLearningController {
    private final MachineLearningService service;
    public MachineLearningController(MachineLearningService service) { this.service = service; }

    @PostMapping("/treinamentos")
    public ResponseEntity<MlTrainingJobResponse> train(@Valid @RequestBody MlTrainingRequest request) {
        var job = service.enqueueTraining(request.trainingCutoffExclusive(), request.seed());
        return ResponseEntity.accepted().body(MlTrainingJobResponse.from(job));
    }

    @GetMapping("/treinamentos/{id}")
    public MlTrainingJobResponse training(@PathVariable long id) {
        return service.findJob(id).map(MlTrainingJobResponse::from).orElseThrow(() -> new MlTrainingJobNotFoundException(id));
    }

    @GetMapping("/modelos")
    public List<MlModelSummaryResponse> models() { return service.listModels().stream().map(MlModelSummaryResponse::from).toList(); }

    @GetMapping("/modelos/{id}")
    public MlModelResponse model(@PathVariable long id) {
        return service.findModel(id).map(MlModelResponse::from)
                .orElseThrow(() -> new MachineLearningService.MlModelNotFoundException(id));
    }

    @PostMapping("/previsoes-experimentais")
    public MlExperimentalPredictionResponse predict(@Valid @RequestBody MlExperimentalPredictionRequest request) {
        return MlExperimentalPredictionResponse.from(service.generate(request.modelId(), request.targetContestNumber(),
                request.evenNumbers(), request.oddNumbers(), request.seed()));
    }
}
