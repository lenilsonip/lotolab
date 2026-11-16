package br.com.lotolab.domain;

import java.time.LocalDateTime;
import java.util.List;

public record PredictionAttemptView(long id, int targetContestNumber, LocalDateTime createdAt,
                                    List<Integer> predictedNumbers, String source, boolean simulation,
                                    String strategyVersion, Long experimentalScore, Long baselineScore,
                                    String evidenceStatus, String performanceStatus,
                                    Integer sourceCutoffContest,
                                    Long walkForwardStrategyScore, Long walkForwardBaselineScore,
                                    Integer actualContestNumber, Integer hits,
                                    String aiInterpretationStatus, String aiSummary, String aiExplanation,
                                    List<String> aiHypotheses) {
    public PredictionAttemptView { predictedNumbers = List.copyOf(predictedNumbers); }

    public String status() { return simulation ? "SIMULATED" : hits == null ? "PENDING" : "EVALUATED"; }
}
