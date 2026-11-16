package br.com.lotolab.api;

import br.com.lotolab.application.LearningPolicyService;
import br.com.lotolab.domain.LearningEventSummary;
import br.com.lotolab.domain.LearningModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/agentes/aprendizagem")
public class LearningPolicyController {
    private final LearningPolicyService service;
    public LearningPolicyController(LearningPolicyService service) { this.service = service; }

    @GetMapping
    public LearningStatus status() {
        LearningModel model = service.champion();
        LearningModel candidate = service.candidate();
        return new LearningStatus(model.id(), model.version(), model.status(), model.strategyKey(),
                model.sampleSize(), model.meanHits(), model.varianceHits(), model.firstContest(),
                model.lastContest(), "No demonstrated advantage implies uniform remains champion.",
                service.latestEvents(), candidate == null ? model.sampleSize() : candidate.sampleSize(),
                candidate == null ? model.meanHits() : candidate.meanHits(),
                candidate == null ? model.varianceHits() : candidate.varianceHits(),
                candidate == null ? null : candidate.status(),
                candidate == null ? null : candidate.strategyKey());
    }

    public record LearningStatus(long championModelId, int version, String status, String strategyKey,
                                 int sampleSize, double meanHits, double varianceHits,
                                 Integer firstContest, Integer lastContest, String scientificNotice,
                                 List<LearningEventSummary> recentEvents, int learningSampleSize,
                                 double learningMeanHits, double learningVarianceHits,
                                 String candidateStatus, String candidateStrategyKey) { }
}
