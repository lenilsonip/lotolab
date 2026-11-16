package br.com.lotolab.infrastructure.persistence;

import br.com.lotolab.application.port.AgentInterpretationRepository;
import br.com.lotolab.domain.AgentInterpretation;
import br.com.lotolab.application.port.AgentJob;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.time.Instant;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcAgentInterpretationRepository implements AgentInterpretationRepository {
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() { };
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public JdbcAgentInterpretationRepository(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.json = Objects.requireNonNull(json);
    }

    @Override
    public AgentInterpretation save(AgentInterpretation value) {
        try {
            String conflictColumn = value.agentRunId() != null ? "agent_run_id" : "prediction_attempt_id";
            List<AgentInterpretation> rows = jdbc.query("""
                    INSERT INTO agent_interpretation (agent_run_id, prediction_attempt_id, agent_key,
                        provider, model, prompt_version, status, summary, hypotheses, explanation,
                        statistical_input, error_category)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSONB), ?, CAST(? AS JSONB), ?)
                    ON CONFLICT (%s) DO UPDATE SET
                        status = EXCLUDED.status, summary = EXCLUDED.summary, hypotheses = EXCLUDED.hypotheses,
                        explanation = EXCLUDED.explanation, statistical_input = EXCLUDED.statistical_input,
                        error_category = EXCLUDED.error_category, created_at = CURRENT_TIMESTAMP
                    RETURNING id, agent_run_id, prediction_attempt_id, agent_key, provider, model,
                              prompt_version, status, summary, hypotheses::text AS hypotheses_json,
                              explanation, statistical_input::text AS input_json, error_category, created_at
                    """.formatted(conflictColumn), (rs, row) -> map(rs.getLong("id"), (Long) rs.getObject("agent_run_id"),
                    (Long) rs.getObject("prediction_attempt_id"), rs.getString("agent_key"),
                    rs.getString("provider"), rs.getString("model"), rs.getString("prompt_version"),
                    rs.getString("status"), rs.getString("summary"), rs.getString("hypotheses_json"),
                    rs.getString("explanation"), rs.getString("input_json"), rs.getString("error_category"),
                    rs.getTimestamp("created_at").toInstant()), value.agentRunId(), value.predictionAttemptId(),
                    value.agentKey(), value.provider(), value.model(), value.promptVersion(), value.status(),
                    value.summary(), json.writeValueAsString(value.hypotheses()), value.explanation(),
                    value.statisticalInputJson(), value.errorCategory());
            return rows.getFirst();
        } catch (Exception exception) {
            throw new IllegalStateException("Could not persist agent interpretation", exception);
        }
    }

    @Override
    @Transactional
    public AgentJob enqueue(AgentInterpretation interpretation, String jobType, String origin, String correlationId) {
        AgentInterpretation saved = save(interpretation);
        Long id = jdbc.queryForObject("""
                INSERT INTO agent_job (interpretation_id, job_type, status, origin, correlation_id, run_id)
                VALUES (?, ?, 'PENDING', ?, ?, ?)
                ON CONFLICT (interpretation_id) DO UPDATE SET interpretation_id = EXCLUDED.interpretation_id
                RETURNING id
                """, Long.class, saved.id(), jobType, origin, correlationId, br.com.lotolab.infrastructure.logging.LogContext.runId());
        return new AgentJob(Objects.requireNonNull(id), saved.id(), 0, 4, "PENDING", origin, correlationId,
                br.com.lotolab.infrastructure.logging.LogContext.runId(), saved);
    }

    @Override
    @Transactional
    public Optional<AgentJob> claimNext(String workerId, Instant leaseUntil) {
        jdbc.update("UPDATE agent_interpretation ai SET status='FAILED', error_category='LEASE_EXPIRED_MAX_ATTEMPTS' "
                + "FROM agent_job j WHERE j.interpretation_id=ai.id AND j.status='RUNNING' "
                + "AND j.lease_until < CURRENT_TIMESTAMP AND j.attempts >= j.max_attempts");
        jdbc.update("UPDATE agent_job SET status = 'FAILED', error_category = 'LEASE_EXPIRED_MAX_ATTEMPTS', "
                + "lease_owner = NULL, lease_until = NULL, updated_at = CURRENT_TIMESTAMP "
                + "WHERE status = 'RUNNING' AND lease_until < CURRENT_TIMESTAMP AND attempts >= max_attempts");
        var rows = jdbc.query("""
                WITH candidate AS (
                    SELECT id FROM agent_job
                    WHERE ((status = 'PENDING' AND next_attempt_at <= CURRENT_TIMESTAMP)
                        OR (status = 'RUNNING' AND lease_until < CURRENT_TIMESTAMP))
                      AND attempts < max_attempts
                    ORDER BY next_attempt_at, id FOR UPDATE SKIP LOCKED LIMIT 1
                )
                UPDATE agent_job j SET status = 'RUNNING', attempts = attempts + 1,
                    lease_owner = ?, lease_until = ?, updated_at = CURRENT_TIMESTAMP
                FROM candidate c WHERE j.id = c.id
                RETURNING j.id, j.interpretation_id, j.attempts, j.max_attempts, j.status, j.origin, j.correlation_id, j.run_id
                """, (rs, row) -> new long[]{rs.getLong("id"), rs.getLong("interpretation_id"),
                rs.getInt("attempts"), rs.getInt("max_attempts")}, workerId, java.sql.Timestamp.from(leaseUntil));
        if (rows.isEmpty()) return Optional.empty();
        long[] row = rows.getFirst();
        var metadata = jdbc.query("SELECT origin,correlation_id,run_id FROM agent_job WHERE id=?",
                (rs, n) -> new String[]{rs.getString(1),rs.getString(2),rs.getString(3)}, row[0]);
        String[] context = metadata.getFirst();
        return findById(row[1]).map(value -> new AgentJob(row[0], row[1], (int) row[2], (int) row[3], "RUNNING",
                context[0], context[1], context[2], value));
    }

    @Override
    public void finishJob(long jobId, String status, String errorCategory, Instant retryAt) {
        jdbc.update("""
                UPDATE agent_job SET status = ?, error_category = ?, next_attempt_at = COALESCE(?, next_attempt_at),
                    lease_owner = NULL, lease_until = NULL, updated_at = CURRENT_TIMESTAMP WHERE id = ?
                """, status, errorCategory, retryAt == null ? null : java.sql.Timestamp.from(retryAt), jobId);
    }

    @Override
    public Optional<AgentJob> findJob(long jobId) {
        var rows = jdbc.query("SELECT id, interpretation_id, attempts, max_attempts, status,origin,correlation_id,run_id FROM agent_job WHERE id = ?",
                (rs, row) -> new Object[]{rs.getLong("id"), rs.getLong("interpretation_id"),
                        rs.getInt("attempts"), rs.getInt("max_attempts"), rs.getString("status"),
                        rs.getString("origin"), rs.getString("correlation_id"), rs.getString("run_id")}, jobId);
        if (rows.isEmpty()) return Optional.empty();
        Object[] row = rows.getFirst();
        return findById((Long) row[1]).map(value -> new AgentJob((Long) row[0], (Long) row[1],
                (Integer) row[2], (Integer) row[3], (String) row[4], (String) row[5], (String) row[6],
                (String) row[7], value));
    }

    @Override
    public Optional<AgentJob> findLatestJob(String agentKey) {
        var rows = jdbc.query("""
                SELECT j.id, j.interpretation_id, j.attempts, j.max_attempts, j.status,j.origin,j.correlation_id,j.run_id
                FROM agent_job j JOIN agent_interpretation ai ON ai.id=j.interpretation_id
                WHERE ai.agent_key=? ORDER BY j.created_at DESC, j.id DESC LIMIT 1
                """, (rs, row) -> new Object[]{rs.getLong(1), rs.getLong(2), rs.getInt(3),
                        rs.getInt(4), rs.getString(5),rs.getString(6),rs.getString(7),rs.getString(8)}, agentKey);
        if (rows.isEmpty()) return Optional.empty();
        Object[] row = rows.getFirst();
        return findById((Long) row[1]).map(value -> new AgentJob((Long) row[0], (Long) row[1],
                (Integer) row[2], (Integer) row[3], (String) row[4], (String) row[5], (String) row[6],
                (String) row[7], value));
    }

    @Override
    public Optional<AgentJob> findResearchJobForRun(long agentRunId) {
        var rows = jdbc.query("""
                SELECT j.id, j.interpretation_id, j.attempts, j.max_attempts, j.status,
                       j.origin, j.correlation_id, j.run_id
                FROM agent_job j
                JOIN agent_interpretation ai ON ai.id = j.interpretation_id
                WHERE ai.agent_run_id = ? AND ai.agent_key = 'PATTERN_RESEARCHER'
                  AND j.job_type = 'RESEARCH_INTERPRETATION'
                ORDER BY j.id DESC LIMIT 1
                """, (rs, row) -> new Object[]{rs.getLong(1), rs.getLong(2), rs.getInt(3),
                rs.getInt(4), rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8)}, agentRunId);
        if (rows.isEmpty()) return Optional.empty();
        Object[] row = rows.getFirst();
        return findById((Long) row[1]).map(value -> new AgentJob((Long) row[0], (Long) row[1],
                (Integer) row[2], (Integer) row[3], (String) row[4], (String) row[5], (String) row[6],
                (String) row[7], value));
    }

    @Override
    public Optional<AgentInterpretation> findById(long interpretationId) {
        return query("SELECT * FROM agent_interpretation WHERE id = ?", interpretationId);
    }

    @Override
    public Optional<AgentInterpretation> findLatestByAgent(String agentKey) {
        return query("SELECT * FROM agent_interpretation WHERE agent_key = ? ORDER BY created_at DESC, id DESC LIMIT 1", agentKey);
    }

    @Override
    public Optional<AgentInterpretation> findByPredictionAttempt(long attemptId) {
        return query("SELECT * FROM agent_interpretation WHERE prediction_attempt_id = ?", attemptId);
    }

    private Optional<AgentInterpretation> query(String sql, Object parameter) {
        return jdbc.query(sql.replace("SELECT *", "SELECT id, agent_run_id, prediction_attempt_id, agent_key, provider, model, "
                        + "prompt_version, status, summary, hypotheses::text AS hypotheses_json, explanation, "
                        + "statistical_input::text AS input_json, error_category, created_at"), rs -> rs.next() ? Optional.of(map(rs.getLong("id"),
                (Long) rs.getObject("agent_run_id"), (Long) rs.getObject("prediction_attempt_id"),
                rs.getString("agent_key"), rs.getString("provider"), rs.getString("model"),
                rs.getString("prompt_version"), rs.getString("status"), rs.getString("summary"),
                rs.getString("hypotheses_json"), rs.getString("explanation"),
                rs.getString("input_json"), rs.getString("error_category"),
                rs.getTimestamp("created_at").toInstant())) : Optional.empty(), parameter);
    }

    private AgentInterpretation map(long id, Long runId, Long attemptId, String key, String provider,
                                    String model, String promptVersion, String status, String summary,
                                    String hypothesesJson, String explanation, String inputJson,
                                    String errorCategory, Instant createdAt) {
        try {
            return new AgentInterpretation(id, runId, attemptId, key, provider, model, promptVersion,
                    status, summary, json.readValue(hypothesesJson, STRING_LIST), explanation,
                    inputJson, errorCategory, createdAt);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not decode stored agent interpretation", exception);
        }
    }
}
