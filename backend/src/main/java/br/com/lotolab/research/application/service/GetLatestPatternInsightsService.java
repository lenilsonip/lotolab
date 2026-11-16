package br.com.lotolab.research.application.service;

import br.com.lotolab.domain.PatternInsight;
import br.com.lotolab.research.application.port.in.GetLatestPatternInsightsUseCase;
import br.com.lotolab.research.application.port.out.PatternResearchRepository;
import java.util.List;
import java.util.Objects;

public final class GetLatestPatternInsightsService implements GetLatestPatternInsightsUseCase {
    private final PatternResearchRepository repository;
    public GetLatestPatternInsightsService(PatternResearchRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }
    @Override public List<PatternInsight> execute() { return repository.findLatestRunInsights(); }
}
