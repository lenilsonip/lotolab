package br.com.lotolab.api;

import br.com.lotolab.domain.PredictionAttemptView;

import java.time.LocalDateTime;
import java.util.List;

public record PredictionHistoryResponse(long id, int targetContestNumber, LocalDateTime createdAt,
                                        List<Integer> predictedNumbers, String source, boolean simulation,
                                        String strategyVersion, Long experimentalScore, Long baselineScore,
                                        String evidenceStatus, String performanceStatus,
                                        Integer sourceCutoffContest,
                                        Long walkForwardStrategyScore, Long walkForwardBaselineScore,
                                        Integer actualContestNumber, Integer hits,
                                        String aiInterpretationStatus, String aiSummary, String aiExplanation,
                                        List<String> aiHypotheses,
                                        String status) {
    static PredictionHistoryResponse from(PredictionAttemptView value) {
        return new PredictionHistoryResponse(value.id(), value.targetContestNumber(), value.createdAt(),
                value.predictedNumbers(), value.source(), value.simulation(), value.strategyVersion(),
                value.experimentalScore(), value.baselineScore(), value.evidenceStatus(),
                value.performanceStatus(), value.sourceCutoffContest(), value.walkForwardStrategyScore(),
                value.walkForwardBaselineScore(), value.actualContestNumber(), value.hits(),
                value.aiInterpretationStatus(), value.aiSummary(), value.aiExplanation(),
                value.aiHypotheses(), value.status());
    }
}
