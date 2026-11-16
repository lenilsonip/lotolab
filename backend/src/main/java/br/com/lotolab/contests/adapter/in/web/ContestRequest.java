package br.com.lotolab.contests.adapter.in.web;

import br.com.lotolab.contests.application.port.in.RegisterContestCommand;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public record ContestRequest(
        @NotNull @Positive Integer number,
        @NotNull LocalDate drawDate,
        @NotNull @Size(min = 15, max = 15) List<@NotNull @Min(1) @Max(25) Integer> drawnNumbers) {
    RegisterContestCommand toCommand() {
        return new RegisterContestCommand(number, drawDate, drawnNumbers);
    }
}
