package br.com.lotolab.research.application.service;

import br.com.lotolab.research.application.ResearchJobSummary;
import br.com.lotolab.research.application.port.in.GetResearchJobForRunUseCase;
import br.com.lotolab.research.application.port.out.ResearchInterpretationQueries;

import java.util.Objects;
import java.util.Optional;

public final class GetResearchJobForRunService implements GetResearchJobForRunUseCase {
    private final ResearchInterpretationQueries queries;

    public GetResearchJobForRunService(ResearchInterpretationQueries queries) {
        this.queries = Objects.requireNonNull(queries);
    }

    @Override
    public Optional<ResearchJobSummary> execute(long runId) {
        if (runId <= 0) throw new IllegalArgumentException("runId must be positive");
        return queries.jobForRun(runId);
    }
}
