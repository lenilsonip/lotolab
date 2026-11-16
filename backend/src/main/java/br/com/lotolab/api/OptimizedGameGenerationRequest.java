package br.com.lotolab.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

public record OptimizedGameGenerationRequest(
        @Min(0) @Max(12) Integer evenNumbers,
        @Min(0) @Max(13) Integer oddNumbers,
        Long seed,
        @Positive Integer targetContestNumber) {
}
