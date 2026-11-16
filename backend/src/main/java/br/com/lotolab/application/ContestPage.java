package br.com.lotolab.application;

import br.com.lotolab.domain.Contest;
import java.util.List;

/** Compatibility result type; new API code uses contests.application.ContestPage. */
public record ContestPage(List<Contest> contests, int page, int size, long totalElements, int totalPages) {
    public ContestPage { contests = List.copyOf(contests); }
}
