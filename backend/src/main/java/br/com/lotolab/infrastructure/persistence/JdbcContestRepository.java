package br.com.lotolab.infrastructure.persistence;

import br.com.lotolab.application.port.ContestRepository;
import br.com.lotolab.application.PersistedDataInconsistencyException;
import br.com.lotolab.domain.Contest;
import br.com.lotolab.domain.ContestConflictException;
import br.com.lotolab.infrastructure.logging.LogContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class JdbcContestRepository implements ContestRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(JdbcContestRepository.class);

    private static final String INSERT_CONTEST = """
            INSERT INTO lotofacil_contest (contest_number, draw_date)
            VALUES (?, ?)
            ON CONFLICT (contest_number) DO NOTHING
            RETURNING id
            """;
    private static final String INSERT_DRAWN_NUMBER = """
            INSERT INTO lotofacil_drawn_number (contest_id, drawn_number) VALUES (?, ?)
            """;
    private static final String SELECT_CONTEST = """
            SELECT c.contest_number, c.draw_date, n.drawn_number
            FROM lotofacil_contest c
            LEFT JOIN lotofacil_drawn_number n ON n.contest_id = c.id
            WHERE c.contest_number = ?
            ORDER BY n.drawn_number
            """;
    private static final String SELECT_PAGE = """
            SELECT c.contest_number, c.draw_date, n.drawn_number
            FROM (SELECT id, contest_number, draw_date FROM lotofacil_contest
                  ORDER BY contest_number LIMIT ? OFFSET ?) c
            LEFT JOIN lotofacil_drawn_number n ON n.contest_id = c.id
            ORDER BY c.contest_number, n.drawn_number
            """;
    private static final String SELECT_LATEST = """
            SELECT c.contest_number, c.draw_date, n.drawn_number
            FROM (SELECT id, contest_number, draw_date FROM lotofacil_contest
                  ORDER BY contest_number DESC LIMIT 1) c
            LEFT JOIN lotofacil_drawn_number n ON n.contest_id = c.id
            ORDER BY c.contest_number DESC, n.drawn_number
            """;
    private static final String SELECT_ALL = """
            SELECT c.contest_number, c.draw_date, n.drawn_number
            FROM lotofacil_contest c
            LEFT JOIN lotofacil_drawn_number n ON n.contest_id = c.id
            ORDER BY c.contest_number, n.drawn_number
            """;

    private final JdbcTemplate jdbcTemplate;
    public JdbcContestRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate);
    }

    @Override
    public boolean saveIfAbsent(Contest contest) {
        try (LogContext ignored = LogContext.component("ContestRepository", "save-if-absent")) {
        long startedAt = System.nanoTime();
        Objects.requireNonNull(contest, "Contest is required");
        LogContext.phase("START");
        LOGGER.info("component=ContestRepository phase=START contestNumber={} drawnNumberCount={}",
                contest.number(), contest.drawnNumbers().size());

        List<Long> insertedIds = jdbcTemplate.query(
                INSERT_CONTEST,
                (resultSet, rowNumber) -> resultSet.getLong("id"),
                contest.number(),
                contest.drawDate());

        if (insertedIds.isEmpty()) {
            ensureExistingContestMatches(contest);
            LOGGER.info("component=ContestRepository phase=SUCCESS contestNumber={} disposition=IDENTICAL durationMs={}",
                    contest.number(), elapsedMs(startedAt));
            return false;
        }

        long contestId = insertedIds.getFirst();
        List<Object[]> drawnNumberParameters = contest.drawnNumbers().stream()
                .map(drawnNumber -> new Object[]{contestId, drawnNumber})
                .toList();
        jdbcTemplate.batchUpdate(INSERT_DRAWN_NUMBER, drawnNumberParameters);
        LogContext.phase("SUCCESS");
        LOGGER.info("component=ContestRepository phase=SUCCESS contestNumber={} disposition=CREATED drawnNumberCount={} durationMs={}",
                contest.number(), contest.drawnNumbers().size(), elapsedMs(startedAt));
        return true;
        }
    }

    @Override
    public Optional<Contest> findByNumber(int number) {
        return findContests(SELECT_CONTEST, number).stream().findFirst();
    }

    @Override
    public Optional<Contest> findLatest() {
        return findContests(SELECT_LATEST).stream().findFirst();
    }

    @Override
    public List<Contest> findAll() {
        return findContests(SELECT_ALL);
    }

    @Override
    public long count() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM lotofacil_contest", Long.class);
        return Objects.requireNonNull(count);
    }

    @Override
    public List<Contest> findPage(long offset, int limit) {
        return findContests(SELECT_PAGE, limit, offset);
    }

    private List<Contest> findContests(String sql, Object... parameters) {
        return jdbcTemplate.query(sql, resultSet -> {
            List<Contest> contests = new java.util.ArrayList<>();
            int currentNumber = -1;
            LocalDate drawDate = null;
            List<Integer> drawnNumbers = new java.util.ArrayList<>();
            while (resultSet.next()) {
                int number = resultSet.getInt("contest_number");
                if (currentNumber != -1 && number != currentNumber) {
                    contests.add(reconstructContest(currentNumber, drawDate, drawnNumbers));
                    drawnNumbers = new java.util.ArrayList<>();
                }
                currentNumber = number;
                drawDate = resultSet.getObject("draw_date", LocalDate.class);
                int drawnNumber = resultSet.getInt("drawn_number");
                if (!resultSet.wasNull()) {
                    drawnNumbers.add(drawnNumber);
                }
            }
            if (currentNumber != -1) {
                contests.add(reconstructContest(currentNumber, drawDate, drawnNumbers));
            }
            return List.copyOf(contests);
        }, parameters);
    }

    private Contest reconstructContest(int number, LocalDate drawDate, List<Integer> drawnNumbers) {
        try {
            return new Contest(number, drawDate, drawnNumbers);
        } catch (IllegalArgumentException | NullPointerException exception) {
            LOGGER.error("component=ContestRepository phase=ERROR contestNumber={} failure={}", number,
                    exception.getClass().getSimpleName());
            throw new PersistedDataInconsistencyException(number, exception);
        }
    }

    private void ensureExistingContestMatches(Contest contest) {
        Contest storedContest = findContests(SELECT_CONTEST, contest.number()).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Contest disappeared while checking idempotent registration"));

        if (!contest.equals(storedContest)) {
            LogContext.phase("CONFLICT");
            LOGGER.warn("component=ContestRepository phase=CONFLICT contestNumber={} reason=content-differs-from-persisted",
                    contest.number());
            throw new ContestConflictException(contest.number());
        }
    }

    private static long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }
}
