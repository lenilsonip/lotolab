package br.com.lotolab.research.adapter.out.agent;

import br.com.lotolab.application.port.AgentInterpretationRepository;
import br.com.lotolab.domain.AgentInterpretation;
import br.com.lotolab.research.application.port.out.ResearchInterpretationQueries;
import br.com.lotolab.research.application.ResearchJobSummary;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;

/** Reuses the existing interpretation/job persistence port without coupling research application to its service. */
@Component
public final class ResearchInterpretationQueriesAdapter implements ResearchInterpretationQueries {
    private static final String AGENT = "PATTERN_RESEARCHER";
    private final AgentInterpretationRepository interpretations;
    public ResearchInterpretationQueriesAdapter(AgentInterpretationRepository interpretations) {
        this.interpretations = Objects.requireNonNull(interpretations);
    }
    @Override public Optional<AgentInterpretation> latestInterpretation() {
        return interpretations.findLatestByAgent(AGENT);
    }
    @Override public Optional<ResearchJobSummary> latestJob() {
        return interpretations.findLatestJob(AGENT).map(job -> new ResearchJobSummary(job.id(), job.status()));
    }
    @Override public Optional<ResearchJobSummary> jobForRun(long runId) {
        return interpretations.findResearchJobForRun(runId)
                .map(job -> new ResearchJobSummary(job.id(), job.status()));
    }
}
