package br.com.lotolab.application.port;

import br.com.lotolab.domain.AgentInterpretation;

public record AgentJob(long id, long interpretationId, int attempts, int maxAttempts,
                       String status, String origin, String correlationId, String runId,
                       AgentInterpretation interpretation) { }
