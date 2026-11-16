package br.com.lotolab.research.application.service;

import br.com.lotolab.research.application.port.in.GetLatestResearchJobUseCase;
import br.com.lotolab.research.application.ResearchJobSummary;
import br.com.lotolab.research.application.port.out.ResearchInterpretationQueries;
import java.util.Objects;
import java.util.Optional;

public final class GetLatestResearchJobService implements GetLatestResearchJobUseCase {
    private final ResearchInterpretationQueries queries;
    public GetLatestResearchJobService(ResearchInterpretationQueries queries) {
        this.queries = Objects.requireNonNull(queries);
    }
    @Override public Optional<ResearchJobSummary> execute() { return queries.latestJob(); }
}
