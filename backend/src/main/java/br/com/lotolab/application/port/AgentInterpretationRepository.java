package br.com.lotolab.application.port;

import br.com.lotolab.domain.AgentInterpretation;

import java.util.List;
import java.util.Optional;

public interface AgentInterpretationRepository {
    AgentInterpretation save(AgentInterpretation interpretation);
    AgentJob enqueue(AgentInterpretation interpretation, String jobType, String origin, String correlationId);
    java.util.Optional<AgentJob> claimNext(String workerId, java.time.Instant leaseUntil);
    void finishJob(long jobId, String status, String errorCategory, java.time.Instant retryAt);
    java.util.Optional<AgentJob> findJob(long jobId);
    java.util.Optional<AgentJob> findLatestJob(String agentKey);
    java.util.Optional<AgentJob> findResearchJobForRun(long agentRunId);
    Optional<AgentInterpretation> findById(long interpretationId);
    Optional<AgentInterpretation> findLatestByAgent(String agentKey);
    Optional<AgentInterpretation> findByPredictionAttempt(long attemptId);
}
