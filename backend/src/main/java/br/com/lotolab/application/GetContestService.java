package br.com.lotolab.application;

import br.com.lotolab.contests.application.port.in.GetContestUseCase;
import br.com.lotolab.domain.Contest;

/** Compatibility facade; the contests input use case owns the implementation. */
public final class GetContestService {
    private final GetContestUseCase getContest;
    public GetContestService(GetContestUseCase getContest) { this.getContest = getContest; }
    public Contest get(int number) { return getContest.get(number); }
}
