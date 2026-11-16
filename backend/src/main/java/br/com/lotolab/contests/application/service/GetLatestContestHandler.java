package br.com.lotolab.contests.application.service;

import br.com.lotolab.contests.application.ContestNotFoundException;
import br.com.lotolab.contests.application.port.in.GetLatestContestUseCase;
import br.com.lotolab.contests.application.port.out.ContestRepository;
import br.com.lotolab.domain.Contest;

import java.util.Objects;

public final class GetLatestContestHandler implements GetLatestContestUseCase {
    private final ContestRepository contests;

    public GetLatestContestHandler(ContestRepository contests) { this.contests = Objects.requireNonNull(contests); }

    @Override
    public Contest get() { return contests.findLatest().orElseThrow(ContestNotFoundException::new); }
}
