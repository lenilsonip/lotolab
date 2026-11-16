package br.com.lotolab.api;

import jakarta.validation.constraints.Positive;

public record MlTrainingRequest(@Positive Integer trainingCutoffExclusive, Long seed) { }
