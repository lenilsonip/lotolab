package br.com.lotolab.infrastructure.persistence;

import br.com.lotolab.contests.application.port.out.PendingPredictionEvaluator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Objects;

/** JDBC adapter for applying an official draw to pending real prediction attempts. */
@Repository
public class JdbcPendingPredictionEvaluator implements PendingPredictionEvaluator {
    private final JdbcTemplate jdbcTemplate;

    public JdbcPendingPredictionEvaluator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate);
    }

    @Override
    public int evaluateForContest(int contestNumber) {
        return jdbcTemplate.update("""
                INSERT INTO prediction_evaluation (attempt_id, actual_contest_number, hit_count)
                SELECT pa.id, c.contest_number, COUNT(actual.drawn_number)::SMALLINT
                FROM prediction_attempt pa
                JOIN lotofacil_contest c ON c.contest_number = pa.target_contest_number
                JOIN prediction_attempt_number predicted ON predicted.attempt_id = pa.id
                LEFT JOIN lotofacil_drawn_number actual
                  ON actual.contest_id = c.id AND actual.drawn_number = predicted.number
                WHERE c.contest_number = ? AND pa.is_simulation = FALSE
                GROUP BY pa.id, c.contest_number
                ON CONFLICT (attempt_id) DO NOTHING
                """, contestNumber);
    }
}
