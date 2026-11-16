package br.com.lotolab.contests.application.service;

import br.com.lotolab.contests.application.ContestNotFoundException;
import br.com.lotolab.contests.application.port.in.GetContestUseCase;
import br.com.lotolab.contests.application.port.out.ContestRepository;
import br.com.lotolab.domain.Contest;

import java.util.Objects;

public final class GetContestHandler implements GetContestUseCase {
    private final ContestRepository contests;

    public GetContestHandler(ContestRepository contests) { this.contests = Objects.requireNonNull(contests); }

    @Override
    public Contest get(int number) {
        if (number <= 0) throw new IllegalArgumentException("O número do concurso deve ser positivo");
        return contests.findByNumber(number).orElseThrow(() -> new ContestNotFoundException(number));
    }
}
