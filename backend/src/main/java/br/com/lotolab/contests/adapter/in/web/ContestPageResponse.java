package br.com.lotolab.contests.adapter.in.web;

import br.com.lotolab.contests.application.ContestPage;
import java.util.List;

public record ContestPageResponse(List<ContestResponse> content, int page, int size,
                                  long totalElements, int totalPages) {
    static ContestPageResponse from(ContestPage page) {
        return new ContestPageResponse(page.contests().stream().map(ContestResponse::from).toList(),
                page.page(), page.size(), page.totalElements(), page.totalPages());
    }
}
