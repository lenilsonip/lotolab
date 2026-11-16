package br.com.lotolab.contests.application.service;

import br.com.lotolab.contests.application.ContestPage;
import br.com.lotolab.contests.application.port.in.ListContestsUseCase;
import br.com.lotolab.contests.application.port.out.ContestRepository;

import java.util.Objects;

public final class ListContestsHandler implements ListContestsUseCase {
    private static final int MAX_PAGE_SIZE = 100;
    private final ContestRepository contests;

    public ListContestsHandler(ContestRepository contests) { this.contests = Objects.requireNonNull(contests); }

    @Override
    public ContestPage list(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("A página deve ser não negativa e o tamanho deve estar entre 1 e 100");
        }
        long total = contests.count();
        long offset = (long) page * size;
        int totalPages = (int) ((total + size - 1) / size);
        return new ContestPage(contests.findPage(offset, size), page, size, total, totalPages);
    }
}
