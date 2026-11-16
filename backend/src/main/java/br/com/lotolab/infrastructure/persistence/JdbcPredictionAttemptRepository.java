package br.com.lotolab.infrastructure.persistence;

import br.com.lotolab.application.port.PredictionAttemptRepository;
import br.com.lotolab.domain.PredictionAttempt;
import br.com.lotolab.domain.PredictionAttemptView;
import br.com.lotolab.infrastructure.logging.LogContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.List;

@Repository
public class JdbcPredictionAttemptRepository implements PredictionAttemptRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(JdbcPredictionAttemptRepository.class);
    private static final String INSERT_ATTEMPT = """
            INSERT INTO prediction_attempt (target_contest_number)
            VALUES (?)
            RETURNING id, created_at
            """;
    private static final String INSERT_NUMBER = """
            INSERT INTO prediction_attempt_number (attempt_id, number) VALUES (?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcPredictionAttemptRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate);
    }

    @Override
    @Transactional
    public PredictionAttempt save(PredictionAttempt attempt) {
        try (LogContext ignored = LogContext.component("PredictionPersistence", "save-attempt")) {
        long startedAt = System.nanoTime();
        LogContext.phase("START");
        Objects.requireNonNull(attempt, "Prediction attempt is required");
        if (attempt.id() != null || attempt.createdAt() != null) {
            throw new IllegalArgumentException("Somente uma previsão nova pode ser persistida");
        }

        PersistedAttempt persisted = jdbcTemplate.queryForObject(INSERT_ATTEMPT,
                (resultSet, rowNumber) -> new PersistedAttempt(
                        resultSet.getLong("id"),
                        resultSet.getTimestamp("created_at").toLocalDateTime()),
                attempt.targetContestNumber());
        Objects.requireNonNull(persisted, "PostgreSQL não retornou os dados da previsão");

        List<Object[]> parameters = attempt.predictedNumbers().stream()
                .map(number -> new Object[]{persisted.id(), number})
                .toList();
        jdbcTemplate.batchUpdate(INSERT_NUMBER, parameters);
        PredictionAttempt saved = new PredictionAttempt(persisted.id(), attempt.targetContestNumber(),
                persisted.createdAt(), attempt.predictedNumbers());
        LogContext.phase("SUCCESS");
        LOGGER.info("component=PredictionPersistence phase=SUCCESS targetContest={} attemptId={} numberCount={} durationMs={}",
                attempt.targetContestNumber(), persisted.id(), attempt.predictedNumbers().size(), elapsedMs(startedAt));
        return saved;
        }
    }

    @Override
    @Transactional
    public PredictionAttempt saveGenerated(PredictionAttempt attempt, int sourceCutoffContest, boolean targetWasOverridden,
                                           String strategyVersion, long experimentalScore, Long baselineScore,
                                           Long baselineSeed, int candidateBudget, String evidenceStatus,
                                           String performanceStatus,
                                           List<br.com.lotolab.domain.StrategyWalkForwardFold> folds,
                                           List<Long> evidenceIds) {
        try (LogContext ignored = LogContext.component("PredictionPersistence", "save-generated-attempt")) {
        long startedAt = System.nanoTime();
        Objects.requireNonNull(attempt, "Prediction attempt is required");
        LogContext.phase("START");
        LOGGER.info("component=PredictionPersistence phase=START targetContest={} sourceCutoff={} strategyVersion={} foldCount={} evidenceCount={} numberCount={}",
                attempt.targetContestNumber(), sourceCutoffContest, strategyVersion, folds.size(), evidenceIds.size(),
                attempt.predictedNumbers().size());
        Long evaluationId = jdbcTemplate.queryForObject("""
                INSERT INTO strategy_evaluation (strategy_key, strategy_version, target_contest_number,
                    source_cutoff_contest, random_seed, candidate_budget, evidence_status, performance_status)
                VALUES ('MUTATION_OPTIMIZER', ?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """, Long.class, strategyVersion, attempt.targetContestNumber(),
                sourceCutoffContest, baselineSeed, candidateBudget, evidenceStatus, performanceStatus);
        Objects.requireNonNull(evaluationId);
        for (var fold : folds) {
            jdbcTemplate.update("""
                    INSERT INTO strategy_evaluation_fold (evaluation_id, fold_number,
                        training_first_contest, training_last_contest, validation_first_contest,
                        validation_last_contest, training_sample_size, validation_sample_size,
                        optimized_score, random_baseline_score, random_seed, candidate_budget)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, evaluationId, fold.foldNumber(), fold.trainingFirstContest(), fold.trainingLastContest(),
                    fold.validationFirstContest(), fold.validationLastContest(), fold.trainingSampleSize(),
                    fold.validationSampleSize(), fold.strategyScore(), fold.randomBaselineScore(),
                    fold.randomSeed(), fold.candidateBudget());
        }
        for (Long evidenceId : evidenceIds) {
            jdbcTemplate.update("""
                    INSERT INTO strategy_evaluation_evidence (evaluation_id, pattern_insight_id) VALUES (?, ?)
                    """, evaluationId, evidenceId);
        }
        PersistedAttempt persisted = jdbcTemplate.queryForObject("""
                INSERT INTO prediction_attempt (target_contest_number, strategy_evaluation_id, source,
                    is_simulation, target_was_overridden, strategy_version, experimental_score,
                    baseline_score, evidence_status, performance_status, evidence_ids, model_version_id)
                VALUES (?, ?, 'OPTIMIZED', FALSE, ?, ?, ?, ?, ?, ?, CAST(? AS JSONB),
                    (SELECT id FROM model_version WHERE model_key='lotofacil-policy' AND status='CHAMPION'))
                RETURNING id, created_at
                """, (resultSet, rowNumber) -> new PersistedAttempt(resultSet.getLong("id"),
                resultSet.getTimestamp("created_at").toLocalDateTime()), attempt.targetContestNumber(), evaluationId,
                targetWasOverridden, strategyVersion, experimentalScore, baselineScore, evidenceStatus,
                performanceStatus, evidenceIds.toString());
        Objects.requireNonNull(persisted);
        List<Object[]> numberParameters = attempt.predictedNumbers().stream()
                .map(number -> new Object[]{persisted.id(), number})
                .toList();
        jdbcTemplate.batchUpdate(INSERT_NUMBER, numberParameters);
        jdbcTemplate.update("UPDATE strategy_evaluation SET evidence_status = ? WHERE id = ?",
                evidenceStatus, evaluationId);
        PredictionAttempt saved = new PredictionAttempt(persisted.id(), attempt.targetContestNumber(), persisted.createdAt(),
                attempt.predictedNumbers());
        LogContext.phase("SUCCESS");
        LOGGER.info("component=PredictionPersistence phase=SUCCESS targetContest={} attemptId={} evaluationId={} numberCount={} durationMs={}",
                attempt.targetContestNumber(), persisted.id(), evaluationId, attempt.predictedNumbers().size(),
                elapsedMs(startedAt));
        return saved;
        }
    }

    @Override
    @Transactional
    public PredictionAttempt savePolicyGenerated(PredictionAttempt attempt, boolean targetWasOverridden, String strategyVersion) {
        var persisted = jdbcTemplate.queryForObject("""
                INSERT INTO prediction_attempt(target_contest_number,source,is_simulation,target_was_overridden,
                    strategy_version,evidence_status,performance_status,model_version_id)
                VALUES (?,'RANDOM',FALSE,?,?,'NO_ELIGIBLE_EVIDENCE','NOT_EVALUATED',
                    (SELECT id FROM model_version WHERE model_key='lotofacil-policy' AND status='CHAMPION'))
                RETURNING id,created_at
                """, (rs, row) -> new PersistedAttempt(rs.getLong("id"), rs.getTimestamp("created_at").toLocalDateTime()),
                attempt.targetContestNumber(), targetWasOverridden, strategyVersion);
        Objects.requireNonNull(persisted);
        jdbcTemplate.batchUpdate(INSERT_NUMBER, attempt.predictedNumbers().stream()
                .map(number -> new Object[]{persisted.id(), number}).toList());
        return new PredictionAttempt(persisted.id(), attempt.targetContestNumber(), persisted.createdAt(), attempt.predictedNumbers());
    }

    @Override
    public List<PredictionAttemptView> findAllRecords() {
        return jdbcTemplate.query("""
                SELECT pa.id, pa.target_contest_number, pa.created_at, pa.source, pa.is_simulation,
                       pa.strategy_version, pa.experimental_score, pa.baseline_score, pa.evidence_status,
                       pa.performance_status, se.source_cutoff_contest,
                       fold_scores.strategy_score AS walk_forward_strategy_score,
                       fold_scores.baseline_score AS walk_forward_baseline_score,
                       pe.actual_contest_number, pe.hit_count,
                       ai.status AS ai_interpretation_status, ai.summary AS ai_summary,
                       ai.explanation AS ai_explanation,
                       ARRAY(SELECT jsonb_array_elements_text(ai.hypotheses)) AS ai_hypotheses,
                       COALESCE(array_agg(pan.number ORDER BY pan.number)
                           FILTER (WHERE pan.number IS NOT NULL), ARRAY[]::INTEGER[]) AS predicted_numbers
                FROM prediction_attempt pa
                LEFT JOIN prediction_attempt_number pan ON pan.attempt_id = pa.id
                LEFT JOIN prediction_evaluation pe ON pe.attempt_id = pa.id
                LEFT JOIN agent_interpretation ai ON ai.prediction_attempt_id = pa.id
                LEFT JOIN strategy_evaluation se ON se.id = pa.strategy_evaluation_id
                LEFT JOIN LATERAL (
                    SELECT SUM(optimized_score) AS strategy_score, SUM(random_baseline_score) AS baseline_score
                    FROM strategy_evaluation_fold WHERE evaluation_id = se.id
                ) fold_scores ON TRUE
                GROUP BY pa.id, pe.actual_contest_number, pe.hit_count, se.source_cutoff_contest,
                         fold_scores.strategy_score, fold_scores.baseline_score,
                         ai.status, ai.summary, ai.explanation, ai.hypotheses
                ORDER BY pa.created_at DESC, pa.id DESC
                """, (rs, row) -> {
            java.sql.Array numbersArray = rs.getArray("predicted_numbers");
            Object[] values = (Object[]) numbersArray.getArray();
            List<Integer> numbers = java.util.Arrays.stream(values).map(value -> ((Number) value).intValue()).toList();
            Long experimental = rs.getObject("experimental_score", Long.class);
            Long baseline = rs.getObject("baseline_score", Long.class);
            java.math.BigDecimal foldStrategy = rs.getBigDecimal("walk_forward_strategy_score");
            java.math.BigDecimal foldBaseline = rs.getBigDecimal("walk_forward_baseline_score");
            Integer actualContestNumber = rs.getObject("actual_contest_number", Integer.class);
            Integer hits = rs.getObject("hit_count", Integer.class);
            java.sql.Array aiArray = rs.getArray("ai_hypotheses");
            List<String> aiHypotheses = aiArray == null ? List.of() : java.util.Arrays.stream((Object[]) aiArray.getArray())
                    .map(Object::toString).toList();
            return new PredictionAttemptView(rs.getLong("id"), rs.getInt("target_contest_number"),
                    rs.getTimestamp("created_at").toLocalDateTime(), numbers, rs.getString("source"),
                    rs.getBoolean("is_simulation"), rs.getString("strategy_version"), experimental, baseline,
                    rs.getString("evidence_status"), rs.getString("performance_status"),
                    rs.getObject("source_cutoff_contest", Integer.class),
                    foldStrategy == null ? null : foldStrategy.longValue(),
                    foldBaseline == null ? null : foldBaseline.longValue(), actualContestNumber, hits,
                    rs.getString("ai_interpretation_status"), rs.getString("ai_summary"),
                    rs.getString("ai_explanation"), aiHypotheses);
        });
    }

    private record PersistedAttempt(long id, java.time.LocalDateTime createdAt) { }

    private static long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }
}
