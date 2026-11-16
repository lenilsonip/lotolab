package br.com.lotolab;

import br.com.lotolab.application.LogisticModelArtifactCodec;
import br.com.lotolab.application.MachineLearningService;
import br.com.lotolab.application.port.ContestRepository;
import br.com.lotolab.application.port.MachineLearningRepository;
import br.com.lotolab.infrastructure.agent.MlTrainingJobWorker;
import br.com.lotolab.domain.Contest;
import br.com.lotolab.domain.MlTrainingJob;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(properties = "lotolab.ml.worker-delay-ms=3600000")
@AutoConfigureMockMvc
class MachineLearningPostgresIntegrationTests {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ContestRepository contests;
    @Autowired MachineLearningService service;
    @Autowired MlTrainingJobWorker worker;
    @Autowired MachineLearningRepository mlRepository;
    @Autowired LogisticModelArtifactCodec codec;
    @Autowired ObjectMapper json;

    @Test
    void queuesTrainsPersistsReloadsEvaluatesAndGeneratesAnExperimentalPrediction() throws Exception {
        seedSyntheticHistory(180);

        String accepted = mvc.perform(post("/api/ml/treinamentos").contentType("application/json")
                        .content("{\"seed\":2026}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.trainingCutoffExclusive").value(181)).andReturn()
                .getResponse().getContentAsString();
        long jobId = json.readTree(accepted).get("id").asLong();

        // HTTP only enqueues; the real worker component claims the durable job and performs fitting.
        worker.poll();

        MlTrainingJob completed = awaitSuccessfulJob(jobId);
        assertThat(completed.status()).isEqualTo("SUCCEEDED");
        assertThat(completed.modelId()).isNotNull();
        var model = mlRepository.findModel(completed.modelId()).orElseThrow();
        assertThat(model.status()).isEqualTo("EXPERIMENTAL");
        assertThat(model.artifact().trainingExampleCount()).isEqualTo(180 * 25);
        assertThat(model.artifact().numberModels()).hasSize(25);
        assertThat(model.artifact().numberModels().stream().flatMap(m -> m.coefficients().stream())
                .anyMatch(coefficient -> Math.abs(coefficient) > 1.0e-7)).isTrue();
        assertThat(codec.checksum(model.artifact())).isEqualTo(model.checksum());
        assertThat(model.evaluations()).extracting(item -> item.split()).containsExactly("VALIDATION", "HOLDOUT");
        assertThat(model.evaluations()).allSatisfy(slice -> {
            assertThat(slice.trainingLastContest()).isLessThan(slice.targetFirstContest());
            assertThat(slice.sampleCount()).isPositive();
            assertThat(slice.modelHitDistribution()).hasSize(16);
            assertThat(slice.uniformHitDistribution()).hasSize(16);
        });
        System.out.printf("ML_EVIDENCE jobId=%d modelId=%d version=%d checksum=%s trainContests=%d examples=%d firstModelWeights=%s predictionTarget=181%n",
                jobId, model.id(), model.version(), model.checksum(), model.artifact().trainingContestCount(),
                model.artifact().trainingExampleCount(), model.artifact().numberModels().getFirst());

        mvc.perform(get("/api/ml/modelos/{id}", model.id()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.checksum").value(model.checksum()))
                .andExpect(jsonPath("$.artifact.numberModels.length()").value(25))
                .andExpect(jsonPath("$.evaluations.length()").value(2));
        mvc.perform(get("/api/ml/treinamentos/{id}", jobId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.modelId").value(model.id()));

        MlTrainingJob idempotent = service.enqueueTraining(null, 2026L);
        assertThat(idempotent.id()).isEqualTo(jobId);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ml_model_artifact", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT strategy_key FROM model_version WHERE status='CHAMPION'", String.class))
                .isEqualTo("UNIFORM");

        String predictionJson = mvc.perform(post("/api/ml/previsoes-experimentais").contentType("application/json")
                        .content("{\"modelId\":" + model.id() + ",\"targetContestNumber\":181,\"evenNumbers\":8,\"oddNumbers\":7,\"seed\":99}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("EXPERIMENTAL"))
                .andExpect(jsonPath("$.modelVersion").value(model.version()))
                .andExpect(jsonPath("$.trainingCutoffExclusive").value(181))
                .andExpect(jsonPath("$.numbers.length()").value(15))
                .andExpect(jsonPath("$.rankingScores.length()").value(25))
                .andExpect(jsonPath("$.uniformBaselineNumbers.length()").value(15)).andReturn().getResponse().getContentAsString();
        System.out.printf("ML_PREDICTION_EVIDENCE %s%n", predictionJson);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ml_experimental_prediction WHERE model_id=? AND target_contest_number=181 AND training_cutoff_exclusive=181",
                Integer.class, model.id())).isEqualTo(1);

        assertConcurrentClaimAndLeaseRecovery();
        assertAtomicModelFinalizationRollback(model);
    }

    private MlTrainingJob awaitSuccessfulJob(long jobId) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        MlTrainingJob current;
        do {
            current = mlRepository.findJob(jobId).orElseThrow();
            if ("SUCCEEDED".equals(current.status())) return current;
            if ("FAILED".equals(current.status())) throw new AssertionError("Job falhou: " + current.errorCategory());
            Thread.sleep(50);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("Timeout aguardando worker processar job " + jobId + "; status=" + current.status());
    }

    private void assertConcurrentClaimAndLeaseRecovery() throws Exception {
        MlTrainingJob pending = service.enqueueTraining(null, 2027L);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var one = pool.submit(() -> claimAtSameTime("worker-a", ready, start));
            var two = pool.submit(() -> claimAtSameTime("worker-b", ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            var first = one.get(5, TimeUnit.SECONDS);
            var second = two.get(5, TimeUnit.SECONDS);
            assertThat(List.of(first, second).stream().filter(java.util.Optional::isPresent).count()).isEqualTo(1);
            var claimed = first.orElseGet(second::orElseThrow);
            assertThat(claimed.id()).isEqualTo(pending.id());
            jdbc.update("UPDATE ml_training_job SET lease_until=CURRENT_TIMESTAMP - INTERVAL '1 second' WHERE id=?", pending.id());
            var recovered = mlRepository.claimNext("recovery-worker", Instant.now().plusSeconds(300)).orElseThrow();
            assertThat(recovered.id()).isEqualTo(pending.id());
            assertThat(recovered.attempts()).isEqualTo(claimed.attempts() + 1);
        }
    }

    private java.util.Optional<MlTrainingJob> claimAtSameTime(String owner, CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("concorrência não iniciou");
            return mlRepository.claimNext(owner, Instant.now().plusSeconds(300));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt(); throw new IllegalStateException(exception);
        }
    }

    private void assertAtomicModelFinalizationRollback(br.com.lotolab.domain.StoredMlModel model) {
        MlTrainingJob queued = service.enqueueTraining(null, 2028L);
        MlTrainingJob claimed = mlRepository.claimNext("rollback-worker", Instant.now().plusSeconds(300)).orElseThrow();
        assertThat(claimed.id()).isEqualTo(queued.id());
        int modelCount = jdbc.queryForObject("SELECT COUNT(*) FROM ml_model_artifact", Integer.class);
        assertThatThrownBy(() -> mlRepository.completeJob(queued.id(), "rollback-worker", model.artifact(),
                "not-json", model.checksum(), model.evaluations())).isInstanceOf(RuntimeException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ml_model_artifact", Integer.class)).isEqualTo(modelCount);
        assertThat(mlRepository.findJob(queued.id()).orElseThrow().status()).isEqualTo("RUNNING");
        mlRepository.failJob(queued.id(), "rollback-worker", "TEST_FAILURE", null);
    }

    private void seedSyntheticHistory(int count) {
        for (int contestNumber = 1; contestNumber <= count; contestNumber++) {
            Random random = new Random(71_000L + contestNumber);
            List<Integer> numbers = new ArrayList<>();
            for (int number = 1; number <= 25; number++) numbers.add(number);
            Collections.shuffle(numbers, random);
            contests.saveIfAbsent(new Contest(contestNumber,
                    LocalDate.of(2020, 1, 1).plusDays(contestNumber), numbers.subList(0, 15)));
        }
    }
}
