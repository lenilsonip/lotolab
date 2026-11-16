package br.com.lotolab.application;

import br.com.lotolab.contests.application.port.in.GetLatestContestUseCase;
import br.com.lotolab.domain.Contest;

/** Compatibility facade; the contests input use case owns the implementation. */
public final class GetLatestContestService {
    private final GetLatestContestUseCase getLatestContest;
    public GetLatestContestService(GetLatestContestUseCase getLatestContest) { this.getLatestContest = getLatestContest; }
    public Contest get() { return getLatestContest.get(); }
}
