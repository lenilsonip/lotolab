package br.com.lotolab.api;

import br.com.lotolab.application.port.AgentInterpretationRepository;
import br.com.lotolab.domain.AgentInterpretation;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Instant;

@RestController
@RequestMapping("/api/agentes/jobs")
public class AgentJobController {
    private final AgentInterpretationRepository repository;
    public AgentJobController(AgentInterpretationRepository repository) { this.repository = repository; }

    @GetMapping("/{id}")
    public ResponseEntity<JobResponse> get(@PathVariable long id) {
        if (id <= 0) return ResponseEntity.badRequest().build();
        return repository.findJob(id).map(JobResponse::from).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/latest")
    public ResponseEntity<JobResponse> latest(@RequestParam(defaultValue = "PATTERN_RESEARCHER") String agentKey) {
        if (!java.util.Set.of("PATTERN_RESEARCHER", "GAME_EVALUATOR").contains(agentKey))
            return ResponseEntity.badRequest().build();
        return repository.findLatestJob(agentKey).map(JobResponse::from).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    public record JobResponse(long id, String status, int attempts, int maxAttempts,
                              Long interpretationId, String interpretationStatus,
                              String summary, String explanation, String errorCategory, Instant createdAt) {
        static JobResponse from(br.com.lotolab.application.port.AgentJob job) {
            AgentInterpretation value = job.interpretation();
            return new JobResponse(job.id(), job.status(), job.attempts(), job.maxAttempts(), value.id(),
                    value.status(), value.summary(), value.explanation(), value.errorCategory(), value.createdAt());
        }
    }
}
