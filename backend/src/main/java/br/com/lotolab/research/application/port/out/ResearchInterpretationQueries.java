package br.com.lotolab.research.application.port.out;

import br.com.lotolab.domain.AgentInterpretation;
import br.com.lotolab.research.application.ResearchJobSummary;
import java.util.Optional;

public interface ResearchInterpretationQueries {
    Optional<AgentInterpretation> latestInterpretation();
    Optional<ResearchJobSummary> latestJob();
    Optional<ResearchJobSummary> jobForRun(long runId);
}
