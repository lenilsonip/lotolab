package br.com.lotolab.research.application.service;

import br.com.lotolab.domain.AgentInterpretation;
import br.com.lotolab.research.application.port.in.GetLatestResearchInterpretationUseCase;
import br.com.lotolab.research.application.port.out.ResearchInterpretationQueries;
import java.util.Objects;
import java.util.Optional;

public final class GetLatestResearchInterpretationService implements GetLatestResearchInterpretationUseCase {
    private final ResearchInterpretationQueries queries;
    public GetLatestResearchInterpretationService(ResearchInterpretationQueries queries) {
        this.queries = Objects.requireNonNull(queries);
    }
    @Override public Optional<AgentInterpretation> execute() { return queries.latestInterpretation(); }
}
