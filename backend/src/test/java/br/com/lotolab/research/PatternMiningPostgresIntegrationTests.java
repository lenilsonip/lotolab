package br.com.lotolab.research;

import br.com.lotolab.research.application.port.in.MinePatternsCommand;
import br.com.lotolab.research.application.port.in.MinePatternsUseCase;
import br.com.lotolab.research.application.port.in.PatternMiningResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end coverage for research persistence on a disposable PostgreSQL instance only. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "lotolab.agents.gemini.enabled=false",
        "lotolab.agents.worker.enabled=false",
        "lotolab.ml.worker.enabled=false",
        "lotolab.pattern-miner.cron=0 0 0 1 1 *"
})
@AutoConfigureMockMvc
class PatternMiningPostgresIntegrationTests {
    private static final Logger LOGGER = LoggerFactory.getLogger(PatternMiningPostgresIntegrationTests.class);
    private static final int FIRST_CONTEST = 80_001;

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:17-alpine"));

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("DB_URL", POSTGRES::getJdbcUrl);
        registry.add("DB_USER", POSTGRES::getUsername);
        registry.add("DB_PASSWORD", POSTGRES::getPassword);
    }

    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mvc;
    @Autowired private MinePatternsUseCase mining;

    @BeforeEach
    void clearDisposableDatabase() {
        jdbc.update("DELETE FROM agent_job");
        jdbc.update("DELETE FROM agent_interpretation");
        jdbc.update("DELETE FROM pattern_insight");
        jdbc.update("DELETE FROM agent_run");
        jdbc.update("DELETE FROM lotofacil_drawn_number");
        jdbc.update("DELETE FROM lotofacil_contest");
    }

    @Test
    void manualHttpMiningPersistsWalkForwardEvidenceQueuesJobAndIsIdempotent() throws Exception {
        seedContests(FIRST_CONTEST, 30);

        mvc.perform(post("/api/agentes/pesquisador/minerar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.insightsWritten").value(360))
                .andExpect(jsonPath("$.message").value("Mineração concluída."))
                .andExpect(jsonPath("$.insights.length()").value(360))
                .andExpect(jsonPath("$.interpretationJobId").isNumber())
                .andExpect(jsonPath("$.interpretationJobStatus").value("PENDING"));

        Long runId = jdbc.queryForObject("SELECT id FROM agent_run WHERE agent_key='PATTERN_RESEARCHER'", Long.class);
        assertThat(jdbc.queryForObject("SELECT status FROM agent_run WHERE id=?", String.class, runId))
                .isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT algorithm_version FROM agent_run WHERE id=?", String.class, runId))
                .isEqualTo("walk-forward-2");
        assertThat(jdbc.queryForObject("SELECT idempotency_key FROM agent_run WHERE id=?", String.class, runId))
                .isEqualTo("pattern-miner:walk-forward-2:" + FIRST_CONTEST + ":" + (FIRST_CONTEST + 29));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pattern_insight WHERE agent_run_id=?", Long.class, runId))
                .isEqualTo(360L);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM pattern_insight
                WHERE agent_run_id=? AND training_last_contest >= validation_first_contest
                """, Long.class, runId)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(DISTINCT validation_first_contest) FROM pattern_insight WHERE agent_run_id=?
                """, Long.class, runId)).isEqualTo(5L);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM agent_interpretation WHERE agent_run_id=? AND status='PENDING'
                """, Long.class, runId)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM agent_job j JOIN agent_interpretation i ON i.id=j.interpretation_id
                WHERE i.agent_run_id=? AND j.job_type='RESEARCH_INTERPRETATION' AND j.status='PENDING'
                """, Long.class, runId)).isEqualTo(1L);

        mvc.perform(get("/api/agentes/pesquisador/insights"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(360))
                .andExpect(jsonPath("$[0].algorithmVersion").value("walk-forward-2"));
        mvc.perform(get("/api/agentes/pesquisador/interpretacao"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agentRunId").value(runId))
                .andExpect(jsonPath("$.status").value("PENDING"));

        mvc.perform(post("/api/agentes/pesquisador/minerar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.insightsWritten").value(0))
                .andExpect(jsonPath("$.message").value("Sem novo intervalo elegível ou execução já registrada."))
                .andExpect(jsonPath("$.interpretationJobStatus").value("NOT_QUEUED"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM agent_run WHERE agent_key='PATTERN_RESEARCHER'", Long.class))
                .isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pattern_insight WHERE agent_run_id=?", Long.class, runId))
                .isEqualTo(360L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM agent_job", Long.class)).isEqualTo(1L);
    }

    @Test
    void manualHttpResponseUsesJobFromItsRunEvenWhenAnotherRunHasTheGloballyNewestJob() throws Exception {
        seedContests(FIRST_CONTEST, 30);
        Long unrelatedRunId = jdbc.queryForObject("""
                INSERT INTO agent_run(agent_key, algorithm_version, idempotency_key,
                    source_first_contest, source_last_contest, status)
                VALUES ('PATTERN_RESEARCHER', 'walk-forward-2', 'previous-unrelated-run', 90001, 90030, 'COMPLETED')
                RETURNING id
                """, Long.class);
        Long unrelatedInterpretationId = jdbc.queryForObject("""
                INSERT INTO agent_interpretation(agent_run_id, agent_key, provider, model, prompt_version, status)
                VALUES (?, 'PATTERN_RESEARCHER', 'TEST', 'test-model', 'test-v1', 'PENDING') RETURNING id
                """, Long.class, unrelatedRunId);
        Long unrelatedJobId = jdbc.queryForObject("""
                INSERT INTO agent_job(interpretation_id, job_type, status, origin)
                VALUES (?, 'RESEARCH_INTERPRETATION', 'PENDING', 'TEST') RETURNING id
                """, Long.class, unrelatedInterpretationId);
        jdbc.update("UPDATE agent_job SET created_at = CURRENT_TIMESTAMP + INTERVAL '1 day' WHERE id = ?",
                unrelatedJobId);

        String body = mvc.perform(post("/api/agentes/pesquisador/minerar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.interpretationJobStatus").value("PENDING"))
                .andReturn().getResponse().getContentAsString();

        Long currentRunId = jdbc.queryForObject("""
                SELECT id FROM agent_run WHERE idempotency_key = ?
                """, Long.class, "pattern-miner:walk-forward-2:" + FIRST_CONTEST + ":" + (FIRST_CONTEST + 29));
        Long associatedJobId = jdbc.queryForObject("""
                SELECT j.id FROM agent_job j JOIN agent_interpretation i ON i.id = j.interpretation_id
                WHERE i.agent_run_id = ? AND i.agent_key = 'PATTERN_RESEARCHER'
                  AND j.job_type = 'RESEARCH_INTERPRETATION'
                """, Long.class, currentRunId);

        assertThat(body).contains("\"interpretationJobId\":" + associatedJobId)
                .doesNotContain("\"interpretationJobId\":" + unrelatedJobId);
        assertThat(associatedJobId).isNotEqualTo(unrelatedJobId);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM agent_interpretation WHERE agent_run_id = ?",
                Long.class, currentRunId)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM agent_job WHERE interpretation_id = ?",
                Long.class, unrelatedInterpretationId)).isEqualTo(1L);
    }

    @Test
    void competingHttpMiningRequestsExposeAJobOnlyForTheExecutionThatCreatedTheRun() throws Exception {
        seedContests(FIRST_CONTEST, 30);
        var pool = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try {
            var calls = List.of(1, 2).stream().map(ignored -> pool.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("concurrent HTTP start timed out");
                return mvc.perform(post("/api/agentes/pesquisador/minerar")).andReturn()
                        .getResponse().getContentAsString();
            })).toList();
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<String> responses = List.of(calls.get(0).get(60, TimeUnit.SECONDS),
                    calls.get(1).get(60, TimeUnit.SECONDS));
            assertThat(responses).filteredOn(value -> value.contains("\"insightsWritten\":360")).hasSize(1);
            assertThat(responses).filteredOn(value -> value.contains("\"insightsWritten\":0")).hasSize(1);
            assertThat(responses.stream().filter(value -> value.contains("\"insightsWritten\":360"))
                    .findFirst().orElseThrow()).contains("\"interpretationJobId\":");
            assertThat(responses.stream().filter(value -> value.contains("\"insightsWritten\":0"))
                    .findFirst().orElseThrow()).contains("\"interpretationJobId\":null",
                    "\"interpretationJobStatus\":\"NOT_QUEUED\"");
        } finally {
            start.countDown();
            pool.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM agent_run WHERE agent_key='PATTERN_RESEARCHER'", Long.class))
                .isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM agent_interpretation WHERE agent_key='PATTERN_RESEARCHER'", Long.class))
                .isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM agent_job WHERE job_type='RESEARCH_INTERPRETATION'", Long.class))
                .isEqualTo(1L);
    }

    @Test
    void competingMiningRequestsCreateExactlyOneRunAndOneOutboxJob() throws Exception {
        seedContests(FIRST_CONTEST, 30);
        var pool = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try {
            var calls = List.of(1, 2).stream().map(ignored -> pool.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("concurrent start timed out");
                return mining.execute(new MinePatternsCommand("CONCURRENT_TEST"));
            })).toList();
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<PatternMiningResult> results = List.of(calls.get(0).get(60, TimeUnit.SECONDS),
                    calls.get(1).get(60, TimeUnit.SECONDS));
            assertThat(results).extracting(PatternMiningResult::outcome)
                    .containsExactlyInAnyOrder(PatternMiningResult.Outcome.COMPLETED,
                            PatternMiningResult.Outcome.ALREADY_COMPLETED);
        } finally {
            start.countDown();
            pool.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM agent_run WHERE agent_key='PATTERN_RESEARCHER'", Long.class))
                .isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pattern_insight", Long.class)).isEqualTo(360L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM agent_interpretation WHERE agent_key='PATTERN_RESEARCHER'", Long.class))
                .isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM agent_job WHERE job_type='RESEARCH_INTERPRETATION'", Long.class))
                .isEqualTo(1L);
    }

    @Test
    void eventOrOutboxFailureRollsBackRunInsightsInterpretationAndJob() {
        seedContests(FIRST_CONTEST, 30);
        jdbc.execute("ALTER TABLE agent_job ADD CONSTRAINT ck_test_reject_research_job "
                + "CHECK (job_type <> 'RESEARCH_INTERPRETATION')");
        try {
            assertThatThrownBy(() -> mining.execute(new MinePatternsCommand("ROLLBACK_TEST")))
                    .isInstanceOf(RuntimeException.class);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM agent_run WHERE agent_key='PATTERN_RESEARCHER'", Long.class))
                    .isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pattern_insight", Long.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM agent_interpretation WHERE agent_key='PATTERN_RESEARCHER'", Long.class))
                    .isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM agent_job WHERE job_type='RESEARCH_INTERPRETATION'", Long.class))
                    .isZero();
        } finally {
            jdbc.execute("ALTER TABLE agent_job DROP CONSTRAINT IF EXISTS ck_test_reject_research_job");
        }
    }

    @Test
    void fullScaleSyntheticHistoryRunsSixTemporalFoldsAndPersistsExpectedEvidence() {
        seedContests(FIRST_CONTEST, 3_802);
        long started = System.nanoTime();

        PatternMiningResult result = mining.execute(new MinePatternsCommand("SCALE_TEST"));

        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        assertThat(result.outcome()).isEqualTo(PatternMiningResult.Outcome.COMPLETED);
        assertThat(result.contestCount()).isEqualTo(3_802);
        assertThat(result.firstContest()).isEqualTo(FIRST_CONTEST);
        assertThat(result.lastContest()).isEqualTo(FIRST_CONTEST + 3_801);
        assertThat(result.foldCount()).isEqualTo(6);
        assertThat(result.insightsWritten()).isEqualTo(882);
        Long runId = result.runId();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pattern_insight WHERE agent_run_id=?", Long.class, runId))
                .isEqualTo(882L);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM pattern_insight
                WHERE agent_run_id=? AND training_last_contest >= validation_first_contest
                """, Long.class, runId)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(DISTINCT validation_first_contest) FROM pattern_insight WHERE agent_run_id=?
                """, Long.class, runId)).isEqualTo(6L);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM pattern_insight WHERE agent_run_id=? AND evidence_status='INSUFFICIENT_DATA'
                """, Long.class, runId)).isEqualTo(147L);
        LOGGER.info("pattern-research-scale dataset=synthetic contests={} range={}..{} folds={} insights={} durationMs={} algorithmVersion={}",
                result.contestCount(), result.firstContest(), result.lastContest(), result.foldCount(),
                result.insightsWritten(), durationMs, result.algorithmVersion());
    }

    private void seedContests(int firstContest, int count) {
        jdbc.update("""
                INSERT INTO lotofacil_contest (contest_number, draw_date)
                SELECT ? + value, DATE '2020-01-01' + value
                FROM generate_series(0, ? - 1) AS series(value)
                """, firstContest, count);
        jdbc.update("""
                INSERT INTO lotofacil_drawn_number (contest_id, drawn_number)
                SELECT contest.id, ((contest.contest_number * 7 + draw.number_offset) % 25) + 1
                FROM lotofacil_contest contest
                CROSS JOIN generate_series(0, 14) AS draw(number_offset)
                WHERE contest.contest_number BETWEEN ? AND ?
                """, firstContest, firstContest + count - 1);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM lotofacil_drawn_number n
                JOIN lotofacil_contest c ON c.id=n.contest_id
                WHERE c.contest_number BETWEEN ? AND ?
                """, Long.class, firstContest, firstContest + count - 1)).isEqualTo(count * 15L);
    }
}
