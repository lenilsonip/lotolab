package br.com.lotolab.application;

import br.com.lotolab.contests.application.port.in.ListContestsUseCase;

/** Compatibility facade; the contests input use case owns the implementation. */
public final class ListContestsService {
    private final ListContestsUseCase listContests;
    public ListContestsService(ListContestsUseCase listContests) { this.listContests = listContests; }
    public ContestPage list(int page, int size) {
        var result = listContests.list(page, size);
        return new ContestPage(result.contests(), result.page(), result.size(), result.totalElements(), result.totalPages());
    }
}
