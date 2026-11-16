package br.com.lotolab.domain;

public record PredictionOutcome(long attemptId, int contestNumber, int hits, long modelVersionId) { }
