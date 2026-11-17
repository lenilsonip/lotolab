package br.com.lotolab.research.adapter.out.contests;

import br.com.lotolab.contests.application.port.out.ContestRepository;
import br.com.lotolab.domain.Contest;
import br.com.lotolab.research.application.port.out.ResearchContestHistory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/** Bridges the research read model to the contests capability's public output port. */
@Component
public final class ContestHistoryAdapter implements ResearchContestHistory {
    private final ContestRepository contests;
    public ContestHistoryAdapter(ContestRepository contests) { this.contests = Objects.requireNonNull(contests); }
    @Override public List<Contest> loadAll() { return contests.findAll(); }
}
