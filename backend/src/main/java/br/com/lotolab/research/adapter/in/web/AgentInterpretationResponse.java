package br.com.lotolab.research.adapter.in.web;

import br.com.lotolab.domain.AgentInterpretation;

import java.time.Instant;
import java.util.List;

public record AgentInterpretationResponse(long id, Long agentRunId, Long predictionAttemptId,
                                          String agentKey, String provider, String model,
                                          String promptVersion, String status, String summary,
                                          List<String> hypotheses, String explanation,
                                          String statisticalInputJson, String errorCategory,
                                          Instant createdAt) {
    static AgentInterpretationResponse from(AgentInterpretation value) {
        return new AgentInterpretationResponse(value.id(), value.agentRunId(), value.predictionAttemptId(),
                value.agentKey(), value.provider(), value.model(), value.promptVersion(), value.status(),
                value.summary(), value.hypotheses(), value.explanation(), value.statisticalInputJson(),
                value.errorCategory(), value.createdAt());
    }
}
