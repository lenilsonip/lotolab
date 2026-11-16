package br.com.lotolab.api;

import br.com.lotolab.application.SavePredictionAttemptService;
import br.com.lotolab.domain.PredictionAttempt;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/previsoes")
public class PredictionAttemptController {
    private final SavePredictionAttemptService service;

    public PredictionAttemptController(SavePredictionAttemptService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<PredictionAttemptResponse> save(@Valid @RequestBody PredictionAttemptRequest request) {
        PredictionAttempt saved = service.save(request.targetContestNumber(), request.predictedNumbers());
        return ResponseEntity.status(201).body(PredictionAttemptResponse.from(saved));
    }

    @GetMapping
    public List<PredictionHistoryResponse> history() {
        return service.findAll().stream().map(PredictionHistoryResponse::from).toList();
    }
}
