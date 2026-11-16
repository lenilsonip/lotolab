package br.com.lotolab;

import br.com.lotolab.application.AgentInterpretationService;
import br.com.lotolab.application.AiIntegrationException;
import br.com.lotolab.application.OptimizedGameGeneratorService;
import br.com.lotolab.application.SavePredictionAttemptService;
import br.com.lotolab.application.port.AiFeedbackGenerator;
import br.com.lotolab.application.port.AgentInterpretationRepository;
import br.com.lotolab.application.port.AgentJob;
import br.com.lotolab.domain.GameGenerationCriteria;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.sql.DataSource;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Exercises the durable outbox using only disposable PostgreSQL and a mocked Gemini gateway. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "lotolab.agents.gemini.enabled=true",
        "lotolab.agents.worker.enabled=false",
        "lotolab.ml.worker.enabled=false",
        "lotolab.pattern-miner.cron=0 0 0 1 1 *"
})
@AutoConfigureMockMvc
class DurableAgentJobPostgresIntegrationTests {
    private static final String VALID_FEEDBACK = """
            {"summary":"As métricas observadas são descritivas e têm amostra limitada.",
             "hypotheses":["Comparar prospectivamente com a baseline uniforme."],
             "explanation":"Os resultados passados não demonstram vantagem em sorteios futuros."}
            """;

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:17-alpine"));

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("DB_URL", POSTGRES::getJdbcUrl);
        registry.add("DB_USER", POSTGRES::getUsername);
        registry.add("DB_PASSWORD", POSTGRES::getPassword);
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private DataSource dataSource;
    @Autowired private ObjectMapper json;
    @Autowired private AgentInterpretationRepository jobs;
    @Autowired private AgentInterpretationService interpretationService;
    @Autowired private OptimizedGameGeneratorService optimizer;
    @Autowired private SavePredictionAttemptService attempts;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockitoBean private AiFeedbackGenerator gemini;

    @Test
    void outboxIsIdempotentAsyncRecoverableRetryableAndSafeUnderCompetingWorkers() throws Exception {
        try (var connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getURL()).isEqualTo(POSTGRES.getJdbcUrl());
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE version='8'", Integer.class))
                .isEqualTo(1);
        seedContest(91001);
        mockMvc.perform(get("/api/agentes/jobs/latest").param("agentKey", "GAME_EVALUATOR"))
                .andExpect(status().isNotFound());

        var worker = new br.com.lotolab.infrastructure.agent.AgentJobWorker(interpretationService);
        reset(gemini);
        when(gemini.modelName()).thenReturn("fake-gemini");
        when(gemini.isAvailable()).thenReturn(true);
        var providerStarted = new CountDownLatch(1);
        var allowProviderToFinish = new CountDownLatch(1);
        var providerStartedAt = new AtomicLong();
        when(gemini.generateFeedback(anyString())).thenAnswer(invocation -> {
            providerStartedAt.set(System.nanoTime());
            providerStarted.countDown();
            if (!allowProviderToFinish.await(10, TimeUnit.SECONDS))
                throw new IllegalStateException("test did not release fake Gemini");
            return VALID_FEEDBACK;
        });
        long requestStartedAt = System.nanoTime();
        JsonNode first = generateJob(91002, 201);
        long requestElapsedNanos = System.nanoTime() - requestStartedAt;
        long firstAttempt = first.path("predictionId").asLong();
        long firstJob = first.path("interpretationJobId").asLong();
        assertThat(firstJob).isPositive();
        assertThat(jdbc.queryForObject("SELECT status FROM agent_job WHERE id=?", String.class, firstJob)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT attempts FROM agent_job WHERE id=?", Integer.class, firstJob)).isZero();
        assertThat(jdbc.queryForObject("SELECT correlation_id FROM agent_job WHERE id=?", String.class, firstJob)).isNotBlank();
        assertThat(jdbc.queryForObject("SELECT run_id FROM agent_job WHERE id=?", String.class, firstJob)).isNotBlank();
        verify(gemini, never()).generateFeedback(anyString());
        mockMvc.perform(get("/api/agentes/jobs/{id}", firstJob))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING"));
        mockMvc.perform(get("/api/agentes/jobs/latest").param("agentKey", "GAME_EVALUATOR"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(firstJob));

        var existingInterpretation = jobs.findByPredictionAttempt(firstAttempt).orElseThrow();
        AgentJob repeated = jobs.enqueue(existingInterpretation, "PREDICTION_INTERPRETATION", "TEST", "repeat-trace");
        assertThat(repeated.id()).isEqualTo(firstJob);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM agent_job WHERE interpretation_id=?", Integer.class,
                existingInterpretation.id())).isEqualTo(1);
        var asyncWorker = Executors.newSingleThreadExecutor();
        AtomicLong workerStartedAt = new AtomicLong();
        long workerElapsedNanos;
        try {
            var future = asyncWorker.submit(() -> {
                workerStartedAt.set(System.nanoTime());
                worker.poll();
            });
            assertThat(providerStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(future.isDone()).isFalse();
            mockMvc.perform(get("/api/agentes/jobs/{id}", firstJob))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RUNNING"));
            long providerBlockedAt = System.nanoTime();
            allowProviderToFinish.countDown();
            future.get(5, TimeUnit.SECONDS);
            workerElapsedNanos = System.nanoTime() - workerStartedAt.get();
            long providerElapsedNanos = System.nanoTime() - providerStartedAt.get();
            assertThat(requestElapsedNanos).isPositive();
            assertThat(workerElapsedNanos).isGreaterThanOrEqualTo(providerElapsedNanos);
            assertThat(providerBlockedAt).isGreaterThan(providerStartedAt.get());
        } finally {
            allowProviderToFinish.countDown();
            asyncWorker.shutdownNow();
        }
        assertJob(firstJob, "SUCCEEDED", 1, "SUCCEEDED", null);
        verify(gemini, times(1)).generateFeedback(anyString());

        reset(gemini);
        when(gemini.modelName()).thenReturn("fake-gemini");
        when(gemini.isAvailable()).thenReturn(true);
        when(gemini.generateFeedback(anyString()))
                .thenThrow(AiIntegrationException.temporarilyUnavailable(new IllegalStateException("fake 503")))
                .thenReturn(VALID_FEEDBACK);
        long retryJob = generateJob(91003, 202).path("interpretationJobId").asLong();
        worker.poll();
        assertJob(retryJob, "PENDING", 1, "PENDING", "AI_TEMPORARILY_UNAVAILABLE");
        jdbc.update("UPDATE agent_job SET next_attempt_at=CURRENT_TIMESTAMP - INTERVAL '1 second' WHERE id=?", retryJob);
        worker.poll();
        assertJob(retryJob, "SUCCEEDED", 2, "SUCCEEDED", null);

        reset(gemini);
        when(gemini.modelName()).thenReturn("fake-gemini");
        when(gemini.isAvailable()).thenReturn(true);
        when(gemini.generateFeedback(anyString())).thenThrow(new AiIntegrationException("fake permanent provider error"));
        long failedJob = generateJob(91004, 203).path("interpretationJobId").asLong();
        worker.poll();
        assertJob(failedJob, "FAILED", 1, "FAILED", "AI_PROVIDER_ERROR");
        assertThat(jdbc.queryForObject("SELECT error_category FROM agent_job WHERE id=?", String.class, failedJob))
                .doesNotContain("permanent", "credential", "secret");

        configureSuccessfulGemini();
        long recoveredJob = generateJob(91005, 204).path("interpretationJobId").asLong();
        AgentJob interrupted = jobs.claimNext("interrupted-worker", Instant.now().plusSeconds(60)).orElseThrow();
        assertThat(interrupted.id()).isEqualTo(recoveredJob);
        jdbc.update("UPDATE agent_job SET lease_until=CURRENT_TIMESTAMP - INTERVAL '1 second' WHERE id=?", recoveredJob);
        worker.poll();
        assertJob(recoveredJob, "SUCCEEDED", 2, "SUCCEEDED", null);

        long competingJob = generateJob(91006, 205).path("interpretationJobId").asLong();
        var pool = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try {
            var claims = List.of("worker-a", "worker-b").stream().map(id -> pool.submit(() -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("workers did not synchronize");
                return jobs.claimNext(id, Instant.now().plusSeconds(60));
            })).toList();
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Optional<AgentJob>> results = List.of(claims.get(0).get(5, TimeUnit.SECONDS),
                    claims.get(1).get(5, TimeUnit.SECONDS));
            assertThat(results.stream().filter(Optional::isPresent)).hasSize(1);
            assertThat(results.stream().flatMap(Optional::stream).map(AgentJob::id)).containsExactly(competingJob);
            assertThat(jdbc.queryForObject("SELECT attempts FROM agent_job WHERE id=?", Integer.class, competingJob)).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }

        reset(gemini);
        when(gemini.modelName()).thenReturn("fake-gemini");
        when(gemini.isAvailable()).thenReturn(false);
        long unavailableJob = generateJob(91007, 206).path("interpretationJobId").asLong();
        worker.poll();
        assertJob(unavailableJob, "SKIPPED", 1, "SKIPPED_NO_API_KEY", "API_KEY_NOT_CONFIGURED");
        verify(gemini, never()).generateFeedback(anyString());

        // The prediction and its outbox job share one transaction: a downstream failure rolls both back.
        var recommendation = optimizer.generateForTarget(new GameGenerationCriteria(1, 8, 7, 207L), 91008);
        int attemptsBefore = jdbc.queryForObject("SELECT COUNT(*) FROM prediction_attempt", Integer.class);
        int jobsBefore = jdbc.queryForObject("SELECT COUNT(*) FROM agent_job", Integer.class);
        int interpretationsBefore = jdbc.queryForObject("SELECT COUNT(*) FROM agent_interpretation", Integer.class);
        var transaction = new TransactionTemplate(transactionManager);
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            attempts.saveGeneratedAndQueue(recommendation, true);
            throw new IllegalStateException("intentional test rollback");
        })).isInstanceOf(IllegalStateException.class).hasMessage("intentional test rollback");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM prediction_attempt", Integer.class)).isEqualTo(attemptsBefore);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM agent_job", Integer.class)).isEqualTo(jobsBefore);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM agent_interpretation", Integer.class)).isEqualTo(interpretationsBefore);
    }

    private void configureSuccessfulGemini() {
        reset(gemini);
        when(gemini.modelName()).thenReturn("fake-gemini");
        when(gemini.isAvailable()).thenReturn(true);
        when(gemini.generateFeedback(anyString())).thenReturn(VALID_FEEDBACK);
    }

    private JsonNode generateJob(int target, long seed) throws Exception {
        var response = mockMvc.perform(post("/api/experimentos/gerar-otimizado")
                        .contentType("application/json")
                        .content("{\"evenNumbers\":8,\"oddNumbers\":7,\"seed\":" + seed
                                + ",\"targetContestNumber\":" + target + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.interpretationJobId").isNumber())
                .andReturn();
        return json.readTree(response.getResponse().getContentAsString());
    }

    private void assertJob(long id, String jobStatus, int attempts, String interpretationStatus, String errorCategory)
            throws Exception {
        var result = mockMvc.perform(get("/api/agentes/jobs/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(jobStatus))
                .andExpect(jsonPath("$.attempts").value(attempts))
                .andExpect(jsonPath("$.interpretationStatus").value(interpretationStatus))
                .andReturn();
        JsonNode response = json.readTree(result.getResponse().getContentAsString());
        if (errorCategory == null) assertThat(response.path("errorCategory").isNull()).isTrue();
        else assertThat(response.path("errorCategory").asText()).isEqualTo(errorCategory);
    }

    private void seedContest(int number) {
        Long id = jdbc.queryForObject("INSERT INTO lotofacil_contest(contest_number,draw_date) VALUES (?,?) RETURNING id",
                Long.class, number, LocalDate.of(2030, 1, 1));
        jdbc.batchUpdate("INSERT INTO lotofacil_drawn_number(contest_id,drawn_number) VALUES (?,?)",
                IntStream.rangeClosed(1, 15).mapToObj(value -> new Object[]{id, value}).toList());
    }

}
