package br.com.lotolab.contests.adapter.in.web;

import br.com.lotolab.domain.Contest;
import java.time.LocalDate;
import java.util.List;

public record ContestResponse(int number, LocalDate drawDate, List<Integer> drawnNumbers) {
    public ContestResponse { drawnNumbers = List.copyOf(drawnNumbers); }
    static ContestResponse from(Contest contest) {
        return new ContestResponse(contest.number(), contest.drawDate(), contest.drawnNumbers());
    }
}
