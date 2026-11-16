package br.com.lotolab.infrastructure.persistence;

import br.com.lotolab.application.port.LearningPolicyRepository;
import br.com.lotolab.domain.LearningModel;
import br.com.lotolab.domain.PredictionOutcome;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;

@Repository
public class JdbcLearningPolicyRepository implements LearningPolicyRepository {
    private final JdbcTemplate jdbc;
    public JdbcLearningPolicyRepository(JdbcTemplate jdbc) { this.jdbc = Objects.requireNonNull(jdbc); }

    @Override public List<PredictionOutcome> findUnlearnedOutcomes(int contestNumber) {
        return jdbc.query("""
                SELECT pe.attempt_id, pe.actual_contest_number, pe.hit_count, pa.model_version_id
                FROM prediction_evaluation pe JOIN prediction_attempt pa ON pa.id = pe.attempt_id
                JOIN lotofacil_contest c ON c.contest_number = pe.actual_contest_number
                WHERE pe.actual_contest_number = ? AND pa.source = 'OPTIMIZED' AND pa.is_simulation = FALSE
                  AND pa.model_version_id IS NOT NULL AND pa.created_at < c.draw_date::timestamp
                  AND NOT EXISTS (SELECT 1 FROM learning_event le WHERE le.attempt_id = pe.attempt_id)
                ORDER BY pe.attempt_id
                """, (rs, row) -> new PredictionOutcome(rs.getLong(1), rs.getInt(2), rs.getInt(3), rs.getLong(4)), contestNumber);
    }

    @Override public LearningModel champion() {
        return jdbc.query("SELECT id, version, status, strategy_key, sample_size, mean_hits, variance_hits, training_first_contest, training_last_contest FROM model_version WHERE model_key='lotofacil-policy' AND status='CHAMPION'",
                rs -> rs.next() ? map(rs) : null);
    }

    @Override public LearningModel candidate() {
        return jdbc.query("SELECT id, version, status, strategy_key, sample_size, mean_hits, variance_hits, training_first_contest, training_last_contest FROM model_version WHERE model_key='lotofacil-policy' AND status='CANDIDATE' ORDER BY version DESC LIMIT 1",
                rs -> rs.next() ? map(rs) : null);
    }

    @Override public LearningModel createVersion(String status, String strategy, int samples, double mean, double variance,
                                                  Integer first, Integer last, String reason) {
        Integer version = jdbc.queryForObject("SELECT COALESCE(MAX(version),0)+1 FROM model_version WHERE model_key='lotofacil-policy'", Integer.class);
        Long id = jdbc.queryForObject("""
                INSERT INTO model_version(model_key,version,status,strategy_key,parameters,training_first_contest,
                    training_last_contest,sample_size,mean_hits,variance_hits,decision_reason)
                VALUES ('lotofacil-policy',?,?,?,jsonb_build_object('minSamples',100,'expectedMeanHits',9.0,'confidenceZ',1.96),?,?,?,?,?,?)
                RETURNING id
                """, Long.class, Objects.requireNonNull(version), status, strategy, first, last, samples, mean, variance, reason);
        return new LearningModel(Objects.requireNonNull(id), version, status, strategy, samples, mean, variance, first, last);
    }

    @Override public void changeStatus(long modelId, String status) {
        jdbc.update("UPDATE model_version SET status=? WHERE id=?", status, modelId);
    }

    @Override public void record(long attemptId, int contestNumber, long beforeModel, Long afterModel, int hits,
                                 int n, double meanBefore, double meanAfter, double lower, double upper,
                                 String decision, String reason) {
        jdbc.update("""
                INSERT INTO learning_event(attempt_id,actual_contest_number,model_version_before,model_version_after,
                    observed_hits,sample_size,mean_hits_before,mean_hits_after,lower_95,upper_95,decision,reason)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(attempt_id) DO NOTHING
                """, attemptId, contestNumber, beforeModel, afterModel, hits, n, meanBefore, meanAfter,
                lower, upper, decision, reason);
    }

    @Override public List<br.com.lotolab.domain.LearningEventSummary> latestEvents(int limit) {
        return jdbc.query("""
                SELECT id,attempt_id,actual_contest_number,observed_hits,sample_size,mean_hits_before,
                       mean_hits_after,lower_95,upper_95,decision,reason,created_at
                FROM learning_event ORDER BY created_at DESC,id DESC LIMIT ?
                """, (rs, row) -> new br.com.lotolab.domain.LearningEventSummary(rs.getLong("id"),
                rs.getLong("attempt_id"), rs.getInt("actual_contest_number"), rs.getInt("observed_hits"),
                rs.getInt("sample_size"), rs.getDouble("mean_hits_before"), rs.getDouble("mean_hits_after"),
                rs.getDouble("lower_95"), rs.getDouble("upper_95"), rs.getString("decision"),
                rs.getString("reason"), rs.getTimestamp("created_at").toInstant()), Math.min(Math.max(limit, 1), 100));
    }

    private static LearningModel map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new LearningModel(rs.getLong("id"), rs.getInt("version"), rs.getString("status"),
                rs.getString("strategy_key"), rs.getInt("sample_size"), rs.getDouble("mean_hits"),
                rs.getDouble("variance_hits"), (Integer) rs.getObject("training_first_contest"),
                (Integer) rs.getObject("training_last_contest"));
    }
}
