package br.com.lotolab;

import br.com.lotolab.application.CalculateDrawnNumberFrequencyService;
import br.com.lotolab.application.CalculateRecentNumberFrequencyService;
import br.com.lotolab.research.application.port.in.MinePatternsCommand;
import br.com.lotolab.research.application.port.in.MinePatternsUseCase;
import br.com.lotolab.application.SavePredictionAttemptService;
import br.com.lotolab.application.DrawnNumberFrequencyAnalysis;
import br.com.lotolab.contests.application.ContestImportReport;
import br.com.lotolab.application.importing.HistoricalContestImportService;
import br.com.lotolab.application.port.ContestRepository;
import br.com.lotolab.application.port.PatternInsightRepository;
import br.com.lotolab.contests.application.port.in.PersistContestUseCase;
import br.com.lotolab.domain.PatternInsight;
import br.com.lotolab.infrastructure.persistence.JdbcHistoricalFrequencyRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDate;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.math.BigDecimal;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"lotolab.agents.gemini.enabled=false", "lotolab.agents.worker.enabled=false", "lotolab.ml.worker.enabled=false"})
@AutoConfigureMockMvc
class FrequencyAnalysisIntegrationTests {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:17-alpine"));

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JdbcHistoricalFrequencyRepository repository;

    @Autowired
    private ContestRepository contestRepository;

    @Autowired
    private PersistContestUseCase persistContest;

    @Autowired
    private PatternInsightRepository patternInsightRepository;

    @Autowired
    private MinePatternsUseCase minePatterns;

    @Autowired
    private CalculateDrawnNumberFrequencyService frequencyService;

    @Autowired
    private CalculateRecentNumberFrequencyService recentFrequencyService;

    @Autowired
    private SavePredictionAttemptService savePredictionAttemptService;

    @Autowired
    private MockMvc mockMvc;

    @Value("${local.server.port}")
    private int serverPort;

    @Autowired
    private HistoricalContestImportService importService;

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("DB_URL", POSTGRES::getJdbcUrl);
        registry.add("DB_USER", POSTGRES::getUsername);
        registry.add("DB_PASSWORD", POSTGRES::getPassword);
        registry.add("LOTOLAB_AGENTS_GEMINI_ENABLED", () -> "false");
    }

    @BeforeEach
    void resetDatabaseAndSeedOneContest() {
        jdbcTemplate.update("DELETE FROM prediction_evaluation");
        jdbcTemplate.update("DELETE FROM lotofacil_drawn_number");
        jdbcTemplate.update("DELETE FROM lotofacil_contest");
        Long contestId = jdbcTemplate.queryForObject(
                "INSERT INTO lotofacil_contest (contest_number, draw_date) VALUES (?, ?) RETURNING id",
                Long.class, 90001, LocalDate.of(2026, 10, 30));
        jdbcTemplate.batchUpdate("INSERT INTO lotofacil_drawn_number (contest_id, drawn_number) VALUES (?, ?)",
                IntStream.rangeClosed(1, 15).mapToObj(number -> new Object[]{contestId, number}).toList());
    }

    @Test
    void readsAllContestsAndPersistsInsightInV4Schema() {
        assertThat(contestRepository.findAll()).singleElement()
                .satisfies(contest -> assertThat(contest.number()).isEqualTo(90001));
        long before = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pattern_insight", Long.class);

        patternInsightRepository.save(new PatternInsight("DAY_OF_WEEK_PARITY", "Média observada de teste", BigDecimal.ZERO));

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pattern_insight", Long.class))
                .isEqualTo(before + 1);
    }

    @Test
    void optimizedEndpointEvaluatesCurrentHistoryAndReturnsOneValidGame() throws Exception {
        mockMvc.perform(post("/api/experimentos/gerar-otimizado")
                        .contentType("application/json")
                        .content("{\"evenNumbers\":8,\"oddNumbers\":7,\"seed\":812}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.game.length()").value(15))
                .andExpect(jsonPath("$.contestsEvaluated").value(0))
                .andExpect(jsonPath("$.trainingContestsEvaluated").value(1))
                .andExpect(jsonPath("$.searchExhausted").value(false))
                .andExpect(jsonPath("$.iterations").value(500))
                .andExpect(jsonPath("$.statisticalNotice").exists());
    }

    @Test
    void policyEndpointUsesPersistedUniformChampionAndFreezesVersion() throws Exception {
        var response = mockMvc.perform(post("/api/experimentos/gerar-politica")
                        .contentType("application/json")
                        .content("{\"evenNumbers\":8,\"oddNumbers\":7,\"seed\":44}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.game.length()").value(15))
                .andExpect(jsonPath("$.modelVersion").value(1))
                .andExpect(jsonPath("$.strategyKey").value("UNIFORM"))
                .andExpect(jsonPath("$.evaluationStatus").value("NO_DEMONSTRATED_ADVANTAGE"))
                .andReturn();
        var json = objectMapper.readTree(response.getResponse().getContentAsString());
        long attemptId = json.path("predictionId").asLong();
        assertThat(jdbcTemplate.queryForObject("SELECT source FROM prediction_attempt WHERE id=?", String.class, attemptId))
                .isEqualTo("RANDOM");
        assertThat(jdbcTemplate.queryForObject("SELECT model_version_id FROM prediction_attempt WHERE id=?", Long.class, attemptId))
                .isNotNull();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM prediction_attempt_number WHERE attempt_id=?", Integer.class, attemptId))
                .isEqualTo(15);
    }

    @Test
    void patternMinerStoresWalkForwardInsightsInDatabaseAndIsIdempotent() throws Exception {
        for (int contestNumber = 90002; contestNumber <= 90003; contestNumber++) {
            Long id = jdbcTemplate.queryForObject(
                    "INSERT INTO lotofacil_contest (contest_number, draw_date) VALUES (?, ?) RETURNING id",
                    Long.class, contestNumber, LocalDate.of(2026, 10, 30).plusDays(contestNumber - 90001));
            jdbcTemplate.batchUpdate("INSERT INTO lotofacil_drawn_number (contest_id, drawn_number) VALUES (?, ?)",
                    IntStream.rangeClosed(1, 15).mapToObj(number -> new Object[]{id, number}).toList());
        }
        long before = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pattern_insight", Long.class);

        assertThat(minePatterns.execute(new MinePatternsCommand("TEST")).insightsWritten()).isEqualTo(72);
        assertThat(minePatterns.execute(new MinePatternsCommand("TEST")).insightsWritten()).isZero();

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pattern_insight", Long.class))
                .isEqualTo(before + 72);
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM agent_run ORDER BY id DESC LIMIT 1", String.class))
                .isEqualTo("COMPLETED");
        assertThat(jdbcTemplate.queryForObject("SELECT confidence_score FROM pattern_insight ORDER BY id DESC LIMIT 1",
                BigDecimal.class)).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pattern_insight WHERE evidence_status = 'INSUFFICIENT_DATA'",
                Long.class)).isEqualTo(72L);
        mockMvc.perform(get("/api/agentes/pesquisador/insights"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(72))
                .andExpect(jsonPath("$[0].algorithmVersion").value("walk-forward-2"));
        mockMvc.perform(get("/api/agentes/pesquisador/interpretacao"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
        long jobsBeforeNoOpMining = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM agent_job", Long.class);
        mockMvc.perform(post("/api/agentes/pesquisador/minerar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.insightsWritten").value(0))
                .andExpect(jsonPath("$.interpretationJobId").doesNotExist())
                .andExpect(jsonPath("$.interpretationJobStatus").value("NOT_QUEUED"));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM agent_job", Long.class))
                .isEqualTo(jobsBeforeNoOpMining);
    }

    @Test
    void jdbcQueryReturnsAllTwentyFiveNumbersIncludingZerosInDeterministicOrder() {
        var snapshot = repository.calculateDrawnNumberFrequencies();

        assertThat(snapshot.totalContests()).isEqualTo(1);
        assertThat(snapshot.frequencies()).hasSize(25);
        assertThat(snapshot.frequencies()).extracting(frequency -> frequency.number())
                .containsExactlyElementsOf(IntStream.rangeClosed(1, 25).boxed().toList());
        assertThat(snapshot.frequencies().subList(0, 15)).allSatisfy(frequency ->
                assertThat(frequency.occurrences()).isEqualTo(1));
        assertThat(snapshot.frequencies().subList(15, 25)).allSatisfy(frequency ->
                assertThat(frequency.occurrences()).isZero());
        assertThat(snapshot.frequencies().stream().mapToLong(frequency -> frequency.occurrences()).sum())
                .isEqualTo(15L * snapshot.totalContests());
    }

    @Test
    void jdbcRepositoryReconstructsTheContestWithTheHighestNumber() {
        Long latestId = jdbcTemplate.queryForObject(
                "INSERT INTO lotofacil_contest (contest_number, draw_date) VALUES (?, ?) RETURNING id",
                Long.class, 90002, LocalDate.of(2026, 10, 31));
        jdbcTemplate.batchUpdate("INSERT INTO lotofacil_drawn_number (contest_id, drawn_number) VALUES (?, ?)",
                IntStream.rangeClosed(11, 25).mapToObj(number -> new Object[]{latestId, number}).toList());

        var latest = contestRepository.findLatest().orElseThrow();

        assertThat(latest.number()).isEqualTo(90002);
        assertThat(latest.drawDate()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(latest.drawnNumbers()).containsExactlyElementsOf(IntStream.rangeClosed(11, 25).boxed().toList());
    }

    @Test
    void savesPredictionAttemptAndItsFifteenNumbersWithoutRequiringOfficialContestRow() {
        var numbers = IntStream.rangeClosed(1, 15).boxed().toList();

        var saved = savePredictionAttemptService.save(99001, numbers);

        assertThat(saved.id()).isPositive();
        assertThat(saved.targetContestNumber()).isEqualTo(99001);
        assertThat(saved.createdAt()).isNotNull();
        assertThat(saved.predictedNumbers()).containsExactlyElementsOf(numbers);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM prediction_attempt_number WHERE attempt_id = ?", Long.class, saved.id()))
                .isEqualTo(15L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM lotofacil_contest WHERE contest_number = ?", Long.class, 99001))
                .isZero();
    }

    @Test
    void contestRegistrationEvaluatesPendingPredictionOnceAndHistoryReportsItsOutcome() throws Exception {
        var numbers = IntStream.rangeClosed(1, 15).boxed().toList();
        var attempt = savePredictionAttemptService.save(90002, numbers);
        var actual = new br.com.lotolab.domain.Contest(90002, LocalDate.of(2026, 10, 31), numbers);

        assertThat(persistContest.persist(actual)).isTrue();
        assertThat(persistContest.persist(actual)).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM prediction_evaluation WHERE attempt_id = ?", Long.class, attempt.id()))
                .isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT hit_count FROM prediction_evaluation WHERE attempt_id = ?", Integer.class, attempt.id()))
                .isEqualTo(15);

        mockMvc.perform(get("/api/previsoes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].targetContestNumber").value(90002))
                .andExpect(jsonPath("$[0].hits").value(15))
                .andExpect(jsonPath("$[0].status").value("EVALUATED"));
    }

    @Test
    void optimizedApiReadsEligibleEvidenceAndPersistsGeneratedAttemptForNextContest() throws Exception {
        for (int number = 90002; number <= 90005; number++) {
            Long id = jdbcTemplate.queryForObject(
                    "INSERT INTO lotofacil_contest (contest_number, draw_date) VALUES (?, ?) RETURNING id",
                    Long.class, number, LocalDate.of(2026, 10, 30).plusDays(number - 90001));
            jdbcTemplate.batchUpdate("INSERT INTO lotofacil_drawn_number (contest_id, drawn_number) VALUES (?, ?)",
                    IntStream.rangeClosed(1, 15).mapToObj(draw -> new Object[]{id, draw}).toList());
        }
        mockMvc.perform(post("/api/experimentos/gerar-otimizado")
                        .contentType("application/json")
                        .content("{\"evenNumbers\":8,\"oddNumbers\":7,\"seed\":44}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.predictionId").isNumber())
                .andExpect(jsonPath("$.targetContestNumber").value(90006))
                .andExpect(jsonPath("$.sourceCutoffContest").value(90005))
                .andExpect(jsonPath("$.targetWasOverridden").value(false))
                .andExpect(jsonPath("$.evidenceStatus").value("NO_ELIGIBLE_EVIDENCE"))
                .andExpect(jsonPath("$.walkForwardFolds.length()").value(3));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM prediction_attempt WHERE target_contest_number = 90006 AND source = 'OPTIMIZED'",
                Long.class)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM strategy_evaluation_fold", Long.class))
                .isEqualTo(3L);
        Long generatedAttemptId = jdbcTemplate.queryForObject(
                "SELECT id FROM prediction_attempt WHERE target_contest_number = 90006 AND source = 'OPTIMIZED'",
                Long.class);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM prediction_attempt_number WHERE attempt_id = ?",
                Long.class, generatedAttemptId)).isEqualTo(15L);
        mockMvc.perform(get("/api/previsoes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(generatedAttemptId))
                .andExpect(jsonPath("$[0].predictedNumbers.length()").value(15));
    }

    @Test
    void applicationCalculationReturnsFifteenOccurrencesPerContestAndOneHundredPercent() {
        DrawnNumberFrequencyAnalysis analysis = frequencyService.calculate();

        assertThat(analysis.totalContests()).isEqualTo(1);
        assertThat(analysis.frequencies()).hasSize(25);
        assertThat(analysis.frequencies().subList(0, 15)).allSatisfy(frequency -> {
            assertThat(frequency.occurrences()).isEqualTo(1);
            assertThat(frequency.percentage()).hasToString("100.0000");
        });
        assertThat(analysis.frequencies().subList(15, 25)).allSatisfy(frequency -> {
            assertThat(frequency.occurrences()).isZero();
            assertThat(frequency.percentage()).hasToString("0.0000");
        });
        assertThat(analysis.frequencies().stream()
                .mapToLong(DrawnNumberFrequencyAnalysis.Frequency::occurrences).sum())
                .isEqualTo(15L * analysis.totalContests());
    }

    @Test
    void returnsAllNumbersWithZeroPercentWhenThereAreNoContests() {
        jdbcTemplate.update("DELETE FROM lotofacil_drawn_number");
        jdbcTemplate.update("DELETE FROM lotofacil_contest");

        DrawnNumberFrequencyAnalysis analysis = frequencyService.calculate();

        assertThat(analysis.totalContests()).isZero();
        assertThat(analysis.frequencies()).hasSize(25)
                .allSatisfy(frequency -> {
                    assertThat(frequency.occurrences()).isZero();
                    assertThat(frequency.percentage()).hasToString("0.0000");
                });
    }

    @Test
    void endpointReturnsTotalOccurrencesPercentagesAndSortedFrequencies() throws Exception {
        mockMvc.perform(get("/api/analises/frequencia-dezenas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalContests").value(1))
                .andExpect(jsonPath("$.frequencies.length()").value(25))
                .andExpect(jsonPath("$.frequencies[0].number").value(1))
                .andExpect(jsonPath("$.frequencies[0].occurrences").value(1))
                .andExpect(jsonPath("$.frequencies[0].percentage").value(100.0))
                .andExpect(jsonPath("$.frequencies[14].number").value(15))
                .andExpect(jsonPath("$.frequencies[15].number").value(16))
                .andExpect(jsonPath("$.frequencies[15].occurrences").value(0))
                .andExpect(jsonPath("$.frequencies[15].percentage").value(0.0));
    }

    @Test
    void recentWindowUsesHighestContestNumbersAndComparesWithFullHistory() throws Exception {
        Long recentContestId = jdbcTemplate.queryForObject(
                "INSERT INTO lotofacil_contest (contest_number, draw_date) VALUES (?, ?) RETURNING id",
                Long.class, 90002, LocalDate.of(2026, 10, 29));
        jdbcTemplate.batchUpdate("INSERT INTO lotofacil_drawn_number (contest_id, drawn_number) VALUES (?, ?)",
                IntStream.rangeClosed(11, 25).mapToObj(number -> new Object[]{recentContestId, number}).toList());

        var analysis = recentFrequencyService.calculate(1);

        assertThat(analysis.totalContests()).isEqualTo(2);
        assertThat(analysis.analyzedRecentContests()).isEqualTo(1);
        assertThat(analysis.frequencies()).hasSize(25);
        assertThat(analysis.frequencies().getFirst().number()).isEqualTo(11);
        var numberOne = analysis.frequencies().stream().filter(frequency -> frequency.number() == 1)
                .findFirst().orElseThrow();
        assertThat(numberOne.recentOccurrences()).isZero();
        assertThat(numberOne.historicalOccurrences()).isEqualTo(1);
        assertThat(numberOne.recentPercentage()).hasToString("0.0000");
        assertThat(numberOne.historicalPercentage()).hasToString("50.0000");
        assertThat(numberOne.differencePercentagePoints()).hasToString("-50.0000");
        assertThat(analysis.frequencies().stream()
                .mapToLong(br.com.lotolab.application.RecentNumberFrequencyAnalysis.Frequency::recentOccurrences).sum())
                .isEqualTo(15L * analysis.analyzedRecentContests());
        assertThat(analysis.frequencies().stream()
                .mapToLong(br.com.lotolab.application.RecentNumberFrequencyAnalysis.Frequency::historicalOccurrences).sum())
                .isEqualTo(15L * analysis.totalContests());

        mockMvc.perform(get("/api/analises/frequencia-recente").param("concursos", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalContests").value(2))
                .andExpect(jsonPath("$.analyzedRecentContests").value(1))
                .andExpect(jsonPath("$.frequencies.length()").value(25))
                .andExpect(jsonPath("$.frequencies[0].number").value(11))
                .andExpect(jsonPath("$.frequencies[15].number").value(1))
                .andExpect(jsonPath("$.frequencies[15].differencePercentagePoints").value(-50.0));
    }

    @Test
    void recentWindowUsesAllAvailableContestsWhenRequestedCountIsLarger() {
        var analysis = recentFrequencyService.calculate(100);

        assertThat(analysis.totalContests()).isEqualTo(1);
        assertThat(analysis.analyzedRecentContests()).isEqualTo(1);
    }

    @Test
    void recentEndpointRejectsNonPositiveWindow() throws Exception {
        mockMvc.perform(get("/api/analises/frequencia-recente").param("concursos", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void analysisReflectsContestImportedAfterServiceWasCreated(@TempDir Path directory) throws Exception {
        Path csv = directory.resolve("new-contest.csv");
        String numbers = IntStream.rangeClosed(11, 25).mapToObj(Integer::toString)
                .collect(java.util.stream.Collectors.joining(";"));
        Files.writeString(csv, "Concurso;Data;" + IntStream.rangeClosed(1, 15)
                .mapToObj(number -> "bola " + number).collect(java.util.stream.Collectors.joining(";"))
                + System.lineSeparator() + "90002;29/10/2026;" + numbers + System.lineSeparator(),
                StandardCharsets.UTF_8);

        ContestImportReport report = importService.importFile(csv);

        assertThat(report.newContests()).isEqualTo(1);
        assertThat(recentFrequencyService.calculate(1).analyzedRecentContests()).isEqualTo(1);
        mockMvc.perform(get("/api/analises/frequencia-recente").param("concursos", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalContests").value(2))
                .andExpect(jsonPath("$.analyzedRecentContests").value(1))
                .andExpect(jsonPath("$.frequencies[0].number").value(11));
    }

    @Test
    void gameGenerationEndpointReturnsValidUniqueGamesAndCriteria() throws Exception {
        mockMvc.perform(post("/api/jogos/geracao")
                        .contentType("application/json")
                        .content("""
                                {"quantity":5,"evenNumbers":8,"oddNumbers":7,"seed":1234}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.games.length()").value(5))
                .andExpect(jsonPath("$.criteria.quantity").value(5))
                .andExpect(jsonPath("$.criteria.evenNumbers").value(8))
                .andExpect(jsonPath("$.criteria.oddNumbers").value(7))
                .andExpect(jsonPath("$.criteria.seed").value(1234))
                .andExpect(jsonPath("$.probabilityNotice").exists());
    }

    @Test
    void gameGenerationEndpointRejectsQuantityOverMaximumAndInvalidParity() throws Exception {
        mockMvc.perform(post("/api/jogos/geracao")
                        .contentType("application/json")
                        .content("{\"quantity\":101}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/jogos/geracao")
                        .contentType("application/json")
                        .content("{\"quantity\":2,\"evenNumbers\":8}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/jogos/geracao")
                        .contentType("application/json")
                        .content("{\"quantity\":2,\"evenNumbers\":13,\"oddNumbers\":2}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void gameGenerationEndpointIsReproducibleForTheSameSeed() throws Exception {
        String request = "{\"quantity\":10,\"seed\":9912}";
        String first = mockMvc.perform(post("/api/jogos/geracao").contentType("application/json").content(request))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String second = mockMvc.perform(post("/api/jogos/geracao").contentType("application/json").content(request))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(second).isEqualTo(first);
    }

    @Test
    void gameGenerationEndpointRespondsOverHttp() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + serverPort + "/api/jogos/geracao"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"quantity\":2,\"evenNumbers\":8,\"oddNumbers\":7,\"seed\":20261031}"))
                .build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode json = objectMapper.readTree(response.body());
        assertThat(json.path("criteria").path("quantity").asInt()).isEqualTo(2);
        assertThat(json.path("criteria").path("evenNumbers").asInt()).isEqualTo(8);
        assertThat(json.path("criteria").path("oddNumbers").asInt()).isEqualTo(7);
        assertThat(json.path("criteria").path("seed").asLong()).isEqualTo(20261031L);
        assertThat(json.path("games").size()).isEqualTo(2);
    }
}
