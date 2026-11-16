package br.com.lotolab.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PredictionAttemptRequest(
        @NotNull @Positive Integer targetContestNumber,
        @NotNull @Size(min = 15, max = 15) List<@NotNull @Min(1) @Max(25) Integer> predictedNumbers) {
}
