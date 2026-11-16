package br.com.lotolab.research.application.service;

import br.com.lotolab.domain.PatternInsight;
import br.com.lotolab.research.application.port.in.GetPatternRunInsightsUseCase;
import br.com.lotolab.research.application.port.out.PatternResearchRepository;
import java.util.List;
import java.util.Objects;

public final class GetPatternRunInsightsService implements GetPatternRunInsightsUseCase {
    private final PatternResearchRepository repository;
    public GetPatternRunInsightsService(PatternResearchRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }
    @Override public List<PatternInsight> execute(long runId) {
        if (runId <= 0) throw new IllegalArgumentException("runId must be positive");
        return repository.findRunInsights(runId);
    }
}
