package br.com.lotolab.contests.application.port.in;

import java.time.LocalDate;
import java.util.List;

/** Application input; transport validation remains in the REST adapter. */
public record RegisterContestCommand(int number, LocalDate drawDate, List<Integer> drawnNumbers) {
    public RegisterContestCommand {
        drawnNumbers = drawnNumbers == null ? null : List.copyOf(drawnNumbers);
    }
}
