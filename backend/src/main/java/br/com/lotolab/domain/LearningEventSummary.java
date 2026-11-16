package br.com.lotolab.domain;

import java.time.Instant;

public record LearningEventSummary(long id, long attemptId, int contestNumber, int observedHits,
                                  int sampleSize, double meanBefore, double meanAfter,
                                  double lower95, double upper95, String decision, String reason,
                                  Instant createdAt) { }
