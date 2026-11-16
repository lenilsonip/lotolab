package br.com.lotolab.research.application.port.in;

import br.com.lotolab.research.application.ResearchJobSummary;

import java.util.Optional;

public interface GetResearchJobForRunUseCase {
    Optional<ResearchJobSummary> execute(long runId);
}
