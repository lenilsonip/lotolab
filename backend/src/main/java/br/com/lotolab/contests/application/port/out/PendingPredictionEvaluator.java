package br.com.lotolab.contests.application.port.out;

/** Applies the real result to eligible, not-yet-evaluated prediction attempts. */
public interface PendingPredictionEvaluator {
    int evaluateForContest(int contestNumber);
}
