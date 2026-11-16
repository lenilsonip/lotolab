package br.com.lotolab.application;

public record PatternMiningCompletedEvent(long runId, String executionRunId, String origin) {
    public PatternMiningCompletedEvent(long runId) {
        this(runId, null, null);
    }
}