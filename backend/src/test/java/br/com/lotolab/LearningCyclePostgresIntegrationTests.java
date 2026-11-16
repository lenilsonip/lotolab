package br.com.lotolab;

import br.com.lotolab.application.LearningPolicyService;
import br.com.lotolab.application.SavePredictionAttemptService;
import br.com.lotolab.application.port.ContestRepository;
import br.com.lotolab.contests.application.port.in.PersistContestUseCase;
import br.com.lotolab.domain.Contest;
import org.junit.jupiter.api.Test;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end learning test against a disposable PostgreSQL database only. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "lotolab.agents.gemini.enabled=false",
        "lotolab.agents.worker.enabled=false",
        "lotolab.ml.worker.enabled=false",
        "lotolab.pattern-miner.cron=0 0 0 1 1 *"
})
@AutoConfigureMockMvc
class LearningCyclePostgresIntegrationTests {
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
    @Autowired private ContestRepository contests;
    @Autowired private PersistContestUseCase persistContest;
    @Autowired private SavePredictionAttemptService attempts;
    @Autowired private LearningPolicyService learning;

    @Test
    void prospectivePredictionIsEvaluatedAndLearnedOnceWithoutContaminatingPolicy() throws Exception {
        try (var connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getURL()).isEqualTo(POSTGRES.getJdbcUrl());
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE version='8'", Integer.class))
                .isEqualTo(1);
        mockMvc.perform(get("/api/agentes/aprendizagem"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.strategyKey").value("UNIFORM"))
                .andExpect(jsonPath("$.sampleSize").value(0))
                .andExpect(jsonPath("$.learningSampleSize").value(0))
                .andExpect(jsonPath("$.recentEvents.length()").value(0));

        seedContest(90001, LocalDate.of(2030, 1, 1));
        JsonNode eligible = generate(101);
        JsonNode late = generate(102);
        JsonNode simulated = generate(103);
        long eligibleId = eligible.path("predictionId").asLong();
        long lateId = late.path("predictionId").asLong();
        long simulatedId = simulated.path("predictionId").asLong();
        List<Integer> predicted = json.convertValue(eligible.path("game"),
                json.getTypeFactory().constructCollectionType(List.class, Integer.class));
        assertThat(predicted).hasSize(15).doesNotHaveDuplicates();
        assertThat(jdbc.queryForObject("SELECT model_version_id FROM prediction_attempt WHERE id=?", Long.class, eligibleId))
                .isNotNull();
        assertThat(jdbc.queryForObject("SELECT created_at < TIMESTAMP '2030-01-02 00:00:00' FROM prediction_attempt WHERE id=?",
                Boolean.class, eligibleId)).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM prediction_attempt_number WHERE attempt_id=?", Integer.class, eligibleId))
                .isEqualTo(15);

        // These rows model invalid feedback conditions inside the disposable test fixture.
        jdbc.update("UPDATE prediction_attempt SET created_at=TIMESTAMP '2030-01-03 12:00:00' WHERE id=?", lateId);
        jdbc.update("UPDATE prediction_attempt SET is_simulation=TRUE WHERE id=?", simulatedId);
        long manualId = attempts.save(90002, predicted).id();
        assertThat(jdbc.queryForObject("SELECT model_version_id FROM prediction_attempt WHERE id=?", Long.class, manualId))
                .isNull();

        List<Integer> actual = new ArrayList<>(predicted.subList(0, 11));
        IntStream.rangeClosed(1, 25).filter(number -> !predicted.contains(number)).forEach(number -> {
            if (actual.size() < 15) actual.add(number);
        });
        actual.sort(Integer::compareTo);
        assertThat(actual).hasSize(15).doesNotHaveDuplicates();
        assertThat(persistContest.persist(new Contest(90002, LocalDate.of(2030, 1, 2), actual))).isTrue();

        assertThat(jdbc.queryForObject("SELECT hit_count FROM prediction_evaluation WHERE attempt_id=?", Integer.class, eligibleId))
                .isEqualTo(11);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM prediction_evaluation WHERE attempt_id IN (?,?,?)",
                Integer.class, eligibleId, lateId, manualId)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM prediction_evaluation WHERE attempt_id=?", Integer.class, simulatedId))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM learning_event", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT attempt_id FROM learning_event", Long.class)).isEqualTo(eligibleId);
        assertThat(jdbc.queryForObject("SELECT observed_hits FROM learning_event", Integer.class)).isEqualTo(11);
        assertThat(jdbc.queryForObject("SELECT sample_size FROM learning_event", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT decision FROM learning_event", String.class)).isEqualTo("RETAINED");

        mockMvc.perform(get("/api/agentes/aprendizagem"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.strategyKey").value("UNIFORM"))
                .andExpect(jsonPath("$.sampleSize").value(0))
                .andExpect(jsonPath("$.learningSampleSize").value(1))
                .andExpect(jsonPath("$.learningMeanHits").value(11.0))
                .andExpect(jsonPath("$.candidateStatus").value("CANDIDATE"))
                .andExpect(jsonPath("$.recentEvents[0].attemptId").value(eligibleId))
                .andExpect(jsonPath("$.recentEvents[0].contestNumber").value(90002));

        assertThat(persistContest.persist(new Contest(90002, LocalDate.of(2030, 1, 2), actual))).isFalse();
        assertThat(learning.observeContest(90002)).isZero();
        assertThat(learning.observeContest(99999)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM learning_event", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT sample_size FROM model_version WHERE status='CANDIDATE'", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM model_version WHERE status='CHAMPION'", Integer.class))
                .isEqualTo(1);
        assertThatThrownBy(() -> new Contest(90003, LocalDate.of(2030, 1, 3), invalidNumbers()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private JsonNode generate(long seed) throws Exception {
        var response = mockMvc.perform(post("/api/experimentos/gerar-otimizado")
                        .contentType("application/json")
                        .content("{\"evenNumbers\":8,\"oddNumbers\":7,\"seed\":" + seed
                                + ",\"targetContestNumber\":90002}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.game.length()").value(15))
                .andReturn();
        return json.readTree(response.getResponse().getContentAsString());
    }

    private void seedContest(int number, LocalDate date) {
        Long id = jdbc.queryForObject("INSERT INTO lotofacil_contest(contest_number,draw_date) VALUES (?,?) RETURNING id",
                Long.class, number, date);
        jdbc.batchUpdate("INSERT INTO lotofacil_drawn_number(contest_id,drawn_number) VALUES (?,?)",
                IntStream.rangeClosed(1, 15).mapToObj(value -> new Object[]{id, value}).toList());
    }

    private List<Integer> invalidNumbers() {
        var values = new ArrayList<>(IntStream.rangeClosed(1, 14).boxed().toList());
        values.add(14);
        return values;
    }
}
