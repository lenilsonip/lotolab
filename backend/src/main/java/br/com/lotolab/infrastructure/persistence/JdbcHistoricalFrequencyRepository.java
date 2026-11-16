package br.com.lotolab.infrastructure.persistence;

import br.com.lotolab.application.port.HistoricalFrequencyRepository;
import br.com.lotolab.domain.DrawnNumberFrequency;
import br.com.lotolab.domain.FrequencySnapshot;
import br.com.lotolab.domain.RecentDrawnNumberFrequency;
import br.com.lotolab.domain.RecentFrequencySnapshot;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;

@Repository
public class JdbcHistoricalFrequencyRepository implements HistoricalFrequencyRepository {

    private static final String FREQUENCY_QUERY = """
            WITH contest_total AS (
                SELECT COUNT(*) AS total_contests FROM lotofacil_contest
            ), number_range AS (
                SELECT generate_series(1, 25) AS drawn_number
            )
            SELECT number_range.drawn_number,
                   COUNT(lotofacil_drawn_number.drawn_number) AS occurrences,
                   contest_total.total_contests
            FROM number_range
            CROSS JOIN contest_total
            LEFT JOIN lotofacil_drawn_number
                   ON lotofacil_drawn_number.drawn_number = number_range.drawn_number
            GROUP BY number_range.drawn_number, contest_total.total_contests
            ORDER BY occurrences DESC, number_range.drawn_number ASC
            """;

    private static final String RECENT_FREQUENCY_QUERY = """
            WITH contest_total AS (
                SELECT COUNT(*) AS total_contests FROM lotofacil_contest
            ), recent_contests AS (
                SELECT id FROM lotofacil_contest
                ORDER BY contest_number DESC
                LIMIT ?
            ), recent_total AS (
                SELECT COUNT(*) AS recent_contests FROM recent_contests
            ), number_range AS (
                SELECT generate_series(1, 25) AS drawn_number
            ), recent_frequency AS (
                SELECT drawn_number, COUNT(*) AS occurrences
                FROM lotofacil_drawn_number
                WHERE contest_id IN (SELECT id FROM recent_contests)
                GROUP BY drawn_number
            ), historical_frequency AS (
                SELECT drawn_number, COUNT(*) AS occurrences
                FROM lotofacil_drawn_number
                GROUP BY drawn_number
            )
            SELECT number_range.drawn_number,
                   COALESCE(recent_frequency.occurrences, 0) AS recent_occurrences,
                   COALESCE(historical_frequency.occurrences, 0) AS historical_occurrences,
                   contest_total.total_contests,
                   recent_total.recent_contests
            FROM number_range
            CROSS JOIN contest_total
            CROSS JOIN recent_total
            LEFT JOIN recent_frequency USING (drawn_number)
            LEFT JOIN historical_frequency USING (drawn_number)
            ORDER BY recent_occurrences DESC, number_range.drawn_number ASC
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcHistoricalFrequencyRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate);
    }

    @Override
    public FrequencySnapshot calculateDrawnNumberFrequencies() {
        return jdbcTemplate.query(FREQUENCY_QUERY, resultSet -> {
            long totalContests = 0;
            List<DrawnNumberFrequency> frequencies = new java.util.ArrayList<>();
            while (resultSet.next()) {
                totalContests = resultSet.getLong("total_contests");
                frequencies.add(new DrawnNumberFrequency(
                        resultSet.getInt("drawn_number"), resultSet.getLong("occurrences")));
            }
            return new FrequencySnapshot(totalContests, frequencies);
        });
    }

    @Override
    public RecentFrequencySnapshot calculateRecentDrawnNumberFrequencies(int requestedRecentContests) {
        return jdbcTemplate.query(RECENT_FREQUENCY_QUERY, preparedStatement ->
                        preparedStatement.setInt(1, requestedRecentContests), resultSet -> {
                    long totalContests = 0;
                    long recentContests = 0;
                    List<RecentDrawnNumberFrequency> frequencies = new java.util.ArrayList<>();
                    while (resultSet.next()) {
                        totalContests = resultSet.getLong("total_contests");
                        recentContests = resultSet.getLong("recent_contests");
                        frequencies.add(new RecentDrawnNumberFrequency(
                                resultSet.getInt("drawn_number"),
                                resultSet.getLong("recent_occurrences"),
                                resultSet.getLong("historical_occurrences")));
                    }
                    return new RecentFrequencySnapshot(totalContests, recentContests, frequencies);
                });
    }
}
