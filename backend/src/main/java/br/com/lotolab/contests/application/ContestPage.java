package br.com.lotolab.contests.application;

import br.com.lotolab.domain.Contest;
import java.util.List;

public record ContestPage(List<Contest> contests, int page, int size, long totalElements, int totalPages) {
    public ContestPage { contests = List.copyOf(contests); }
}
