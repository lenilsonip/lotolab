package br.com.lotolab.research.application.port.in;

public record PatternMiningResult(Outcome outcome, int insightsWritten, Long runId,
                                  String algorithmVersion, Integer firstContest,
                                  Integer lastContest, int contestCount, int foldCount) {
    public enum Outcome { INSUFFICIENT_HISTORY, ALREADY_COMPLETED, COMPLETED }

    public PatternMiningResult {
        if (outcome == null || insightsWritten < 0 || contestCount < 0 || foldCount < 0) {
            throw new IllegalArgumentException("Invalid mining result");
        }
    }

    public boolean completedNow() { return outcome == Outcome.COMPLETED; }
}
