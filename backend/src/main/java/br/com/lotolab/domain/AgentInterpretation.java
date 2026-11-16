package br.com.lotolab.domain;

import java.time.Instant;
import java.util.List;

/** Text-only LLM interpretation attached to deterministic, persisted statistical evidence. */
public record AgentInterpretation(long id, Long agentRunId, Long predictionAttemptId, String agentKey,
                                  String provider, String model, String promptVersion, String status,
                                  String summary, List<String> hypotheses, String explanation,
                                  String statisticalInputJson, String errorCategory, Instant createdAt) {
    public AgentInterpretation {
        hypotheses = hypotheses == null ? List.of() : List.copyOf(hypotheses);
        if ((agentRunId == null) == (predictionAttemptId == null)) {
            throw new IllegalArgumentException("A interpretação deve pertencer a uma execução ou previsão");
        }
        if (status == null || status.isBlank()) throw new IllegalArgumentException("O status é obrigatório");
    }
}
