package br.com.lotolab.research.adapter.out.persistence;

import br.com.lotolab.application.port.PatternInsightRepository;
import br.com.lotolab.research.application.port.out.PatternResearchRepository;
import br.com.lotolab.domain.PatternInsight;
import br.com.lotolab.infrastructure.logging.LogContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;

@Repository
public class JdbcPatternInsightRepository implements PatternInsightRepository, PatternResearchRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(JdbcPatternInsightRepository.class);
    private final JdbcTemplate jdbcTemplate;

    public JdbcPatternInsightRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate);
    }

    @Override
    public void save(PatternInsight insight) {
        Objects.requireNonNull(insight, "O insight é obrigatório");
        jdbcTemplate.update("""
                INSERT INTO pattern_insight (pattern_type, description, confidence_score)
                VALUES (?, ?, ?)
                """, insight.patternType(), insight.description(), insight.confidenceScore());
    }

    @Override
    public OptionalLong startRun(String agentKey, String algorithmVersion, String idempotencyKey,
                                 Integer sourceFirstContest, Integer sourceLastContest) {
        try (LogContext ignored = LogContext.component("PatternInsightRepository", "start-run")) {
        long startedAt = System.nanoTime();
        LogContext.phase("START");
        List<Long> ids = jdbcTemplate.query("""
                INSERT INTO agent_run (agent_key, algorithm_version, idempotency_key,
                                       source_first_contest, source_last_contest, status)
                VALUES (?, ?, ?, ?, ?, 'RUNNING')
                ON CONFLICT (idempotency_key) DO NOTHING
                RETURNING id
                """, (rs, row) -> rs.getLong(1), agentKey, algorithmVersion, idempotencyKey,
                sourceFirstContest, sourceLastContest);
        LogContext.phase(ids.isEmpty() ? "SKIP" : "SUCCESS");
        LOGGER.info("component=PatternInsightRepository step=start-run agent={} algorithmVersion={} sourceRange={}..{} disposition={} runId={} durationMs={}",
                agentKey, algorithmVersion, sourceFirstContest, sourceLastContest,
                ids.isEmpty() ? "IDEMPOTENT_SKIP" : "CREATED", ids.isEmpty() ? null : ids.getFirst(), elapsedMs(startedAt));
        return ids.isEmpty() ? OptionalLong.empty() : OptionalLong.of(ids.getFirst());
        }
    }

    @Override
    public void save(long runId, PatternInsight insight) {
        Objects.requireNonNull(insight, "O insight é obrigatório");
        jdbcTemplate.update("""
                INSERT INTO pattern_insight (agent_run_id, hypothesis_key, algorithm_version,
                    training_first_contest, training_last_contest, validation_first_contest,
                    validation_last_contest, sample_size, observed_metric, baseline_metric,
                    evidence_status, evidence, pattern_type, description, confidence_score)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSONB), ?, ?, ?)
                """, runId, insight.hypothesisKey(), insight.algorithmVersion(),
                insight.trainingFirstContest(), insight.trainingLastContest(),
                insight.validationFirstContest(), insight.validationLastContest(), insight.sampleSize(),
                insight.observedMetric(), insight.baselineMetric(), insight.evidenceStatus(),
                insight.evidenceJson(), insight.patternType(), insight.description(), insight.confidenceScore());
    }

    @Override
    public void completeRun(long runId) {
        long startedAt = System.nanoTime();
        jdbcTemplate.update("UPDATE agent_run SET status = 'COMPLETED', completed_at = CURRENT_TIMESTAMP WHERE id = ?",
                runId);
        LogContext.phase("SUCCESS");
        LOGGER.info("component=PatternInsightRepository step=complete-run runId={} durationMs={}",
                runId, elapsedMs(startedAt));
    }

    @Override
    public List<PatternInsight> findEligible(int targetContestNumber) {
        try (LogContext ignored = LogContext.component("PatternInsightRepository", "find-eligible")) {
        long startedAt = System.nanoTime();
        LogContext.phase("START");
        List<PatternInsight> eligible = jdbcTemplate.query("""
                SELECT DISTINCT ON (regexp_replace(pi.hypothesis_key, ':fold=[0-9]+$', ''))
                       pi.id, pi.agent_run_id, pi.hypothesis_key, pi.algorithm_version, pi.pattern_type, pi.description,
                       pi.confidence_score, pi.training_first_contest, pi.training_last_contest,
                       pi.validation_first_contest, pi.validation_last_contest, pi.sample_size,
                       pi.observed_metric, pi.baseline_metric, pi.evidence_status, pi.evidence::text AS evidence_json
                FROM pattern_insight pi
                JOIN agent_run ar ON ar.id = pi.agent_run_id AND ar.status = 'COMPLETED'
                WHERE pi.evidence_status = 'EXPERIMENTAL'
                  AND pi.validation_last_contest < ?
                  AND pi.agent_run_id IS NOT NULL
                ORDER BY regexp_replace(pi.hypothesis_key, ':fold=[0-9]+$', ''),
                         pi.validation_last_contest DESC, pi.id DESC
                """, (rs, row) -> new PatternInsight(
                rs.getLong("id"), rs.getLong("agent_run_id"), rs.getString("hypothesis_key"),
                rs.getString("algorithm_version"), rs.getString("pattern_type"), rs.getString("description"),
                rs.getBigDecimal("confidence_score"), (Integer) rs.getObject("training_first_contest"),
                (Integer) rs.getObject("training_last_contest"), (Integer) rs.getObject("validation_first_contest"),
                (Integer) rs.getObject("validation_last_contest"), (Integer) rs.getObject("sample_size"),
                rs.getBigDecimal("observed_metric"), rs.getBigDecimal("baseline_metric"),
                rs.getString("evidence_status"), rs.getString("evidence_json")), targetContestNumber);
        LogContext.phase("SUCCESS");
        LOGGER.info("component=PatternInsightRepository step=find-eligible targetContest={} evidenceCount={} durationMs={}",
                targetContestNumber, eligible.size(), elapsedMs(startedAt));
        return eligible;
        }
    }

    @Override
    public List<PatternInsight> findLatestRunInsights() {
        return jdbcTemplate.query("""
                SELECT id, agent_run_id, hypothesis_key, algorithm_version, pattern_type, description,
                       confidence_score, training_first_contest, training_last_contest,
                       validation_first_contest, validation_last_contest, sample_size,
                       observed_metric, baseline_metric, evidence_status, evidence::text AS evidence_json
                FROM pattern_insight
                WHERE agent_run_id = (SELECT id FROM agent_run ORDER BY id DESC LIMIT 1)
                ORDER BY validation_first_contest, pattern_type
                """, (rs, row) -> new PatternInsight(
                rs.getLong("id"), rs.getLong("agent_run_id"), rs.getString("hypothesis_key"),
                rs.getString("algorithm_version"), rs.getString("pattern_type"), rs.getString("description"),
                rs.getBigDecimal("confidence_score"), (Integer) rs.getObject("training_first_contest"),
                (Integer) rs.getObject("training_last_contest"), (Integer) rs.getObject("validation_first_contest"),
                (Integer) rs.getObject("validation_last_contest"), (Integer) rs.getObject("sample_size"),
                rs.getBigDecimal("observed_metric"), rs.getBigDecimal("baseline_metric"),
                rs.getString("evidence_status"), rs.getString("evidence_json")));
    }

    @Override
    public List<PatternInsight> findRunInsights(long runId) {
        return jdbcTemplate.query("""
                SELECT id, agent_run_id, hypothesis_key, algorithm_version, pattern_type, description,
                       confidence_score, training_first_contest, training_last_contest,
                       validation_first_contest, validation_last_contest, sample_size,
                       observed_metric, baseline_metric, evidence_status, evidence::text AS evidence_json
                FROM pattern_insight
                WHERE agent_run_id = ?
                ORDER BY validation_first_contest, pattern_type, hypothesis_key
                """, (rs, row) -> new PatternInsight(
                rs.getLong("id"), rs.getLong("agent_run_id"), rs.getString("hypothesis_key"),
                rs.getString("algorithm_version"), rs.getString("pattern_type"), rs.getString("description"),
                rs.getBigDecimal("confidence_score"), (Integer) rs.getObject("training_first_contest"),
                (Integer) rs.getObject("training_last_contest"), (Integer) rs.getObject("validation_first_contest"),
                (Integer) rs.getObject("validation_last_contest"), (Integer) rs.getObject("sample_size"),
                rs.getBigDecimal("observed_metric"), rs.getBigDecimal("baseline_metric"),
                rs.getString("evidence_status"), rs.getString("evidence_json")), runId);
    }

    @Override
    public List<PatternInsight> findByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        return jdbcTemplate.query("""
                SELECT id, agent_run_id, hypothesis_key, algorithm_version, pattern_type, description,
                       confidence_score, training_first_contest, training_last_contest,
                       validation_first_contest, validation_last_contest, sample_size,
                       observed_metric, baseline_metric, evidence_status, evidence::text AS evidence_json
                FROM pattern_insight
                WHERE id IN (%s)
                ORDER BY validation_last_contest DESC, pattern_type, hypothesis_key
                """.formatted(placeholders), (rs, row) -> new PatternInsight(
                rs.getLong("id"), rs.getLong("agent_run_id"), rs.getString("hypothesis_key"),
                rs.getString("algorithm_version"), rs.getString("pattern_type"), rs.getString("description"),
                rs.getBigDecimal("confidence_score"), (Integer) rs.getObject("training_first_contest"),
                (Integer) rs.getObject("training_last_contest"), (Integer) rs.getObject("validation_first_contest"),
                (Integer) rs.getObject("validation_last_contest"), (Integer) rs.getObject("sample_size"),
                rs.getBigDecimal("observed_metric"), rs.getBigDecimal("baseline_metric"),
                rs.getString("evidence_status"), rs.getString("evidence_json")), ids.toArray());
    }

    private static long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }
}
