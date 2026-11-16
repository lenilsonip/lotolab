package br.com.lotolab.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record MlExperimentalPredictionRequest(@NotNull @Positive Long modelId,
                                              @Positive Integer targetContestNumber,
                                              @Min(0) @Max(15) Integer evenNumbers,
                                              @Min(0) @Max(15) Integer oddNumbers,
                                              Long seed) { }
