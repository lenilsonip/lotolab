package br.com.lotolab.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record GameGenerationRequest(
        @NotNull @Min(1) @Max(100) Integer quantity,
        @Min(0) @Max(15) Integer evenNumbers,
        @Min(0) @Max(15) Integer oddNumbers,
        Long seed) {
}
