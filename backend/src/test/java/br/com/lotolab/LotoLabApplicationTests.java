package br.com.lotolab;

import br.com.lotolab.contests.application.ContestImportReport;
import br.com.lotolab.contests.application.ContestImportPreview;
import br.com.lotolab.application.importing.HistoricalContestImportService;
import br.com.lotolab.application.SavePredictionAttemptService;
import br.com.lotolab.application.port.AgentInterpretationRepository;
import br.com.lotolab.contests.application.ContestResultPersistedEvent;
import br.com.lotolab.contests.application.port.in.PersistContestUseCase;
import br.com.lotolab.contests.application.port.out.ContestUnitOfWork;
import br.com.lotolab.domain.Contest;
import br.com.lotolab.domain.ContestConflictException;
import br.com.lotolab.domain.AgentInterpretation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {"lotolab.agents.gemini.enabled=false", "lotolab.agents.worker.enabled=false",
        "lotolab.ml.worker.enabled=false", "lotolab.pattern-miner.cron=0 0 0 1 1 *"})
@AutoConfigureMockMvc
@Import(LotoLabApplicationTests.EventProbeConfiguration.class)
class LotoLabApplicationTests {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:17-alpine"));

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PersistContestUseCase persistContest;

    @Autowired
    private ContestUnitOfWork contestUnitOfWork;

    @Autowired
    private SavePredictionAttemptService savePredictionAttemptService;

    @Autowired
    private ContestEventProbe contestEventProbe;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private HistoricalContestImportService historicalContestImportService;

    @Autowired
    private AgentInterpretationRepository agentInterpretationRepository;

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("DB_URL", POSTGRES::getJdbcUrl);
        registry.add("DB_USER", POSTGRES::getUsername);
        registry.add("DB_PASSWORD", POSTGRES::getPassword);
        registry.add("LOTOLAB_IMPORT_TOKEN", () -> "test-only-import-token-0123456789abcdef");
    }

    @Test
    void contextLoadsAndFlywayMigrationAllowsDatabaseWrites() {
        Long insertedId = jdbcTemplate.queryForObject(
                "INSERT INTO migration_probe (value) VALUES (?) RETURNING id",
                Long.class,
                "test-value");

        String persistedValue = jdbcTemplate.queryForObject(
                "SELECT value FROM migration_probe WHERE id = ?",
                String.class,
                insertedId);

        assertThat(persistedValue).isEqualTo("test-value");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '1'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void v6PersistsVersionedAgentInterpretationsIdempotently() {
        Long runId = jdbcTemplate.queryForObject("""
                INSERT INTO agent_run (agent_key, algorithm_version, idempotency_key, status, completed_at)
                VALUES ('PATTERN_RESEARCHER', 'test-v1', 'interpretation-test-run', 'COMPLETED', CURRENT_TIMESTAMP)
                RETURNING id
                """, Long.class);
        AgentInterpretation first = new AgentInterpretation(0, runId, null, "PATTERN_RESEARCHER",
                "GOOGLE_GEMINI", "gemini-test", "prompt-v1", "SUCCEEDED", "Resumo experimental.",
                List.of("Testar estabilidade em outra janela."), "Sem alegação preditiva.",
                "{\"sample\":3}", null, java.time.Instant.now());

        AgentInterpretation saved = agentInterpretationRepository.save(first);
        AgentInterpretation repeated = agentInterpretationRepository.save(new AgentInterpretation(0, runId, null,
                "PATTERN_RESEARCHER", "GOOGLE_GEMINI", "gemini-test", "prompt-v1", "SUCCEEDED",
                "Resumo atualizado experimental.", List.of("Hipótese revisada."), "Sem alegação preditiva.",
                "{\"sample\":4}", null, java.time.Instant.now()));

        assertThat(saved.id()).isEqualTo(repeated.id());
        assertThat(agentInterpretationRepository.findLatestByAgent("PATTERN_RESEARCHER"))
                .get().extracting(AgentInterpretation::hypotheses).isEqualTo(List.of("Hipótese revisada."));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM agent_interpretation WHERE agent_run_id = ?",
                Integer.class, runId)).isEqualTo(1);
    }

    @Test
    void v8OutboxIsIdempotentClaimableAndQueryable() throws Exception {
        jdbcTemplate.update("UPDATE agent_job SET status='CANCELLED', lease_owner=NULL, lease_until=NULL WHERE status IN ('PENDING','RUNNING')");
        Long runId = jdbcTemplate.queryForObject("""
                INSERT INTO agent_run(agent_key,algorithm_version,idempotency_key,status,completed_at)
                VALUES('PATTERN_RESEARCHER','outbox-test-v1','outbox-test-run','COMPLETED',CURRENT_TIMESTAMP) RETURNING id
                """, Long.class);
        var interpretation = new AgentInterpretation(0, runId, null, "PATTERN_RESEARCHER", "GOOGLE_GEMINI",
                "gemini-test", "prompt-v1", "PENDING", null, List.of(), null, "{}", null, java.time.Instant.now());

        var first = agentInterpretationRepository.enqueue(interpretation, "RESEARCH_INTERPRETATION", "TEST", "trace-test");
        var repeated = agentInterpretationRepository.enqueue(interpretation, "RESEARCH_INTERPRETATION", "TEST", "trace-test");
        assertThat(repeated.id()).isEqualTo(first.id());
        assertThat(agentInterpretationRepository.claimNext("test-worker", java.time.Instant.now().plusSeconds(60)))
                .get().satisfies(claimed -> {
                    assertThat(claimed.id()).isEqualTo(first.id());
                    assertThat(claimed.status()).isEqualTo("RUNNING");
                    assertThat(claimed.attempts()).isEqualTo(1);
                });
        agentInterpretationRepository.finishJob(first.id(), "SUCCEEDED", null, null);
        assertThat(agentInterpretationRepository.findJob(first.id())).get()
                .extracting(br.com.lotolab.application.port.AgentJob::status).isEqualTo("SUCCEEDED");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM agent_job WHERE interpretation_id=?",
                Integer.class, first.interpretationId())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT version FROM flyway_schema_history WHERE version='8'",
                String.class)).isEqualTo("8");
    }

    @Test
    void persistsContestAndTreatsIdenticalImportAsNoOp() {
        Contest contest = contest(10001, LocalDate.of(2026, 10, 24), numbersFrom(1, 15));

        assertThat(persistContest.persist(contest)).isTrue();
        assertThat(persistContest.persist(contest)).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM lotofacil_contest WHERE contest_number = ?",
                Integer.class,
                contest.number())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM lotofacil_drawn_number n JOIN lotofacil_contest c " +
                        "ON c.id = n.contest_id WHERE c.contest_number = ?",
                Integer.class,
                contest.number())).isEqualTo(15);
    }

    @Test
    void rejectsReimportWithDifferentContestData() {
        LocalDate drawDate = LocalDate.of(2026, 10, 25);
        Contest original = contest(10002, drawDate, numbersFrom(1, 15));
        persistContest.persist(original);

        assertThatThrownBy(() -> persistContest.persist(
                contest(10002, drawDate, numbersFrom(2, 16))))
                .isInstanceOf(ContestConflictException.class);
        assertThat(jdbcTemplate.queryForObject("SELECT draw_date FROM lotofacil_contest WHERE contest_number = ?",
                LocalDate.class, original.number())).isEqualTo(original.drawDate());
        java.util.List<Integer> persistedNumbers = jdbcTemplate.queryForObject("""
                SELECT array_agg(n.drawn_number ORDER BY n.drawn_number)
                FROM lotofacil_drawn_number n JOIN lotofacil_contest c ON c.id = n.contest_id
                WHERE c.contest_number = ?
                """, (rs, row) -> {
            java.sql.Array sqlArray = rs.getArray(1);
            return java.util.Arrays.stream((Short[]) sqlArray.getArray()).map(Short::intValue).toList();
        }, original.number());
        assertThat(persistedNumbers).isEqualTo(original.drawnNumbers());
    }

    @Test
    void databaseConstraintsRejectDuplicateContestAndInvalidOrRepeatedNumbers() {
        Long contestId = jdbcTemplate.queryForObject(
                "INSERT INTO lotofacil_contest (contest_number, draw_date) VALUES (?, ?) RETURNING id",
                Long.class,
                10003,
                LocalDate.of(2026, 10, 26));
        jdbcTemplate.batchUpdate(
                "INSERT INTO lotofacil_drawn_number (contest_id, drawn_number) VALUES (?, ?)",
                numbersFrom(1, 15).stream()
                        .map(number -> new Object[]{contestId, number})
                        .toList());

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO lotofacil_contest (contest_number, draw_date) VALUES (?, ?)",
                10003,
                LocalDate.of(2026, 10, 26)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO lotofacil_drawn_number (contest_id, drawn_number) VALUES (?, ?)",
                contestId,
                26))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO lotofacil_drawn_number (contest_id, drawn_number) VALUES (?, ?)",
                contestId,
                7))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void restApiRegistersContestIdempotentlyAndReturnsItByNumber() throws Exception {
        String body = """
                {"number":20001,"drawDate":"2026-10-25","drawnNumbers":[15,14,13,12,11,10,9,8,7,6,5,4,3,2,1]}
                """;

        mockMvc.perform(post("/api/concursos").contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.number").value(20001))
                .andExpect(jsonPath("$.drawnNumbers[0]").value(1));
        mockMvc.perform(post("/api/concursos").contentType("application/json").content(body))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/concursos/20001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.drawDate").value("2026-10-25"))
                .andExpect(jsonPath("$.drawnNumbers.length()").value(15));
    }

    @Test
    void restApiMapsInvalidInputMissingContestAndContentConflict() throws Exception {
        String invalidBody = """
                {"number":21001,"drawDate":"2026-10-25","drawnNumbers":[1,2,3]}
                """;
        mockMvc.perform(post("/api/concursos").contentType("application/json").content(invalidBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mockMvc.perform(get("/api/concursos/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CONTEST_NOT_FOUND"));

        String first = """
                {"number":21002,"drawDate":"2026-10-25","drawnNumbers":[1,2,3,4,5,6,7,8,9,10,11,12,13,14,15]}
                """;
        String conflict = """
                {"number":21002,"drawDate":"2026-10-26","drawnNumbers":[1,2,3,4,5,6,7,8,9,10,11,12,13,14,15]}
                """;
        mockMvc.perform(post("/api/concursos").contentType("application/json").content(first))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/concursos").contentType("application/json").content(conflict))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONTEST_CONFLICT"));
    }

    @Test
    void restApiListsContestsInStableNumberOrderWithPagination() throws Exception {
        int totalBefore = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM lotofacil_contest", Integer.class);
        persistContest.persist(contest(1, LocalDate.of(2026, 1, 1), numbersFrom(1, 15)));
        persistContest.persist(contest(2, LocalDate.of(2026, 1, 2), numbersFrom(1, 15)));
        persistContest.persist(contest(3, LocalDate.of(2026, 1, 3), numbersFrom(1, 15)));

        mockMvc.perform(get("/api/concursos").param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].number").value(1))
                .andExpect(jsonPath("$.content[1].number").value(2))
                .andExpect(jsonPath("$.totalElements").value(totalBefore + 3))
                .andExpect(jsonPath("$.totalPages").value((totalBefore + 4) / 2));
        mockMvc.perform(get("/api/concursos").param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].number").value(3));
        mockMvc.perform(get("/api/concursos").param("page", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void restApiRejectsIncompletePersistedContestsAsServerErrors() throws Exception {
        Long contestId = jdbcTemplate.queryForObject(
                "INSERT INTO lotofacil_contest (contest_number, draw_date) VALUES (?, ?) RETURNING id",
                Long.class,
                30001,
                LocalDate.of(2026, 10, 26));

        try {
            mockMvc.perform(get("/api/concursos/30001"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value("PERSISTED_DATA_INCONSISTENCY"));

            jdbcTemplate.update("INSERT INTO lotofacil_drawn_number (contest_id, drawn_number) VALUES (?, ?)",
                    contestId, 7);
            mockMvc.perform(get("/api/concursos/30001"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value("PERSISTED_DATA_INCONSISTENCY"));
            mockMvc.perform(get("/api/concursos").param("page", "0").param("size", "100"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value("PERSISTED_DATA_INCONSISTENCY"));
        } finally {
            jdbcTemplate.update("DELETE FROM lotofacil_drawn_number WHERE contest_id = ?", contestId);
            jdbcTemplate.update("DELETE FROM lotofacil_contest WHERE id = ?", contestId);
        }
    }

    @Test
    void restApiTreatsIncompleteExistingContestAsPersistedDataInconsistency() throws Exception {
        int contestNumber = 30002;
        LocalDate drawDate = LocalDate.of(2026, 10, 26);
        jdbcTemplate.update("INSERT INTO lotofacil_contest (contest_number, draw_date) VALUES (?, ?)",
                contestNumber, drawDate);
        String body = """
                {"number":30002,"drawDate":"2026-10-26","drawnNumbers":[1,2,3,4,5,6,7,8,9,10,11,12,13,14,15]}
                """;

        try {
            mockMvc.perform(post("/api/concursos").contentType("application/json").content(body))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value("PERSISTED_DATA_INCONSISTENCY"));
        } finally {
            jdbcTemplate.update("DELETE FROM lotofacil_contest WHERE contest_number = ?", contestNumber);
        }
    }

    @Test
    void importsValidCsvRowsWithExplicitColumnMapping(@TempDir Path directory) throws IOException {
        Path csv = writeCsv(directory, row(40001, "01/01/2025", 1), row(40002, "02/01/2025", 11));

        ContestImportReport report = historicalContestImportService.importFile(csv);

        assertThat(report.linesRead()).isEqualTo(2);
        assertThat(report.newContests()).isEqualTo(2);
        assertThat(report.alreadyExisting()).isZero();
        assertThat(report.rejectedLines()).isZero();
        assertThat(report.warnings()).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT draw_date FROM lotofacil_contest WHERE contest_number = ?", LocalDate.class, 40001))
                .isEqualTo(LocalDate.of(2025, 1, 1));
        assertThat(jdbcTemplate.queryForList(
                "SELECT drawn_number FROM lotofacil_drawn_number " +
                        "WHERE contest_id = (SELECT id FROM lotofacil_contest WHERE contest_number = ?) " +
                        "ORDER BY drawn_number",
                Integer.class, 40001)).containsExactlyElementsOf(numbersFrom(1, 15));
    }

    @Test
    void importPreviewEndpointRequiresLoopbackTokenAndDoesNotPersist(@TempDir Path directory) throws Exception {
        Path csv = writeCsv(directory, row(40101, "01/01/2025", 1));
        byte[] content = Files.readAllBytes(csv);
        MockMultipartFile file = new MockMultipartFile("file", "history.csv", "text/csv", content);

        mockMvc.perform(multipart("/api/admin/importacao-concursos/preview").file(file)
                        .header("X-LotoLab-Import-Token", "test-only-import-token-0123456789abcdef")
                        .with(request -> { request.setRemoteAddr("127.0.0.1"); return request; }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linesRead").value(1))
                .andExpect(jsonPath("$.wouldInsert").value(1))
                .andExpect(jsonPath("$.alreadyExisting").value(0));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM lotofacil_contest WHERE contest_number = 40101", Integer.class)).isZero();
    }

    @Test
    void importEndpointRequiresConfirmationAndPersistsOnlyAfterConfirmedRequest(@TempDir Path directory)
            throws Exception {
        Path csv = writeCsv(directory, row(40102, "02/01/2025", 1));
        byte[] content = Files.readAllBytes(csv);

        mockMvc.perform(multipart("/api/admin/importacao-concursos")
                        .file(new MockMultipartFile("file", "history.csv", "text/csv", content))
                        .header("X-LotoLab-Import-Token", "test-only-import-token-0123456789abcdef")
                        .with(request -> { request.setRemoteAddr("127.0.0.1"); return request; }))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IMPORT_CONFIRMATION_REQUIRED"));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM lotofacil_contest WHERE contest_number = 40102", Integer.class)).isZero();

        mockMvc.perform(multipart("/api/admin/importacao-concursos")
                        .file(new MockMultipartFile("file", "history.csv", "text/csv", content))
                        .param("confirm", "true")
                        .header("X-LotoLab-Import-Token", "test-only-import-token-0123456789abcdef")
                        .with(request -> { request.setRemoteAddr("127.0.0.1"); return request; }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newContests").value(1))
                .andExpect(jsonPath("$.alreadyExisting").value(0))
                .andExpect(jsonPath("$.persistenceAborted").value(false));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM lotofacil_contest WHERE contest_number = 40102", Integer.class)).isEqualTo(1);
    }

    @Test
    void importEndpointRejectsMissingOrInvalidToken(@TempDir Path directory) throws Exception {
        Path csv = writeCsv(directory, row(40103, "03/01/2025", 1));
        MockMultipartFile file = new MockMultipartFile("file", "history.csv", "text/csv", Files.readAllBytes(csv));
        mockMvc.perform(multipart("/api/admin/importacao-concursos/preview").file(file)
                        .header("X-LotoLab-Import-Token", "wrong")
                        .with(request -> { request.setRemoteAddr("127.0.0.1"); return request; }))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("IMPORT_FORBIDDEN"));

        mockMvc.perform(multipart("/api/admin/importacao-concursos/preview")
                        .file(new MockMultipartFile("file", "history.csv", "text/csv", Files.readAllBytes(csv)))
                        .header("X-LotoLab-Import-Token", "test-only-import-token-0123456789abcdef")
                        .with(request -> { request.setRemoteAddr("192.0.2.20"); return request; }))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("IMPORT_FORBIDDEN"));
    }

    @Test
    void previewsImportWithoutWritingAndClassifiesExistingNewAndConflictingRows(@TempDir Path directory)
            throws IOException {
        int existingNumber = 40005;
        int conflictingNumber = 40006;
        int newNumber = 40007;
        LocalDate existingDate = LocalDate.of(2025, 1, 5);
        LocalDate conflictingDate = LocalDate.of(2025, 1, 6);
        persistContest.persist(contest(existingNumber, existingDate, numbersFrom(1, 15)));
        persistContest.persist(contest(conflictingNumber, conflictingDate, numbersFrom(2, 16)));
        int countBeforePreview = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM lotofacil_contest", Integer.class);
        Path csv = writeCsv(directory,
                row(existingNumber, "05/01/2025", 1),
                row(newNumber, "07/01/2025", 3),
                row(conflictingNumber, "06/01/2025", 1),
                rowWithDraws(40008, "08/01/2025", numbersWithDuplicate()));

        try {
            ContestImportPreview preview = historicalContestImportService.previewFile(csv);

            assertThat(preview.linesRead()).isEqualTo(4);
            assertThat(preview.validLines()).isEqualTo(3);
            assertThat(preview.wouldInsert()).isEqualTo(1);
            assertThat(preview.alreadyExisting()).isEqualTo(1);
            assertThat(preview.conflictLines()).isEqualTo(1);
            assertThat(preview.rejectedLines()).isEqualTo(1);
            assertThat(preview.summary()).contains("Prévia somente leitura");
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM lotofacil_contest", Integer.class)).isEqualTo(countBeforePreview);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM lotofacil_contest WHERE contest_number = ?", Integer.class, newNumber))
                    .isZero();
        } finally {
            jdbcTemplate.update("DELETE FROM lotofacil_drawn_number WHERE contest_id IN " +
                            "(SELECT id FROM lotofacil_contest WHERE contest_number IN (?, ?))",
                    existingNumber, conflictingNumber);
            jdbcTemplate.update("DELETE FROM lotofacil_contest WHERE contest_number IN (?, ?)",
                    existingNumber, conflictingNumber);
        }
    }

    @Test
    void previewReportsMissingFileWithoutWriting(@TempDir Path directory) {
        Path missingCsv = directory.resolve("missing.csv");
        int countBefore = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM lotofacil_contest", Integer.class);

        assertThatThrownBy(() -> historicalContestImportService.previewFile(missingCsv))
                .isInstanceOf(IOException.class);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM lotofacil_contest", Integer.class))
                .isEqualTo(countBefore);
    }

    @Test
    void reportsExactAndConflictingDuplicatesWithinFile(@TempDir Path directory) throws IOException {
        String exact = row(40011, "01/01/2025", 1);
        Path csv = writeCsv(directory, exact, exact,
                row(40012, "02/01/2025", 1), row(40012, "03/01/2025", 2));

        ContestImportReport report = historicalContestImportService.importFile(csv);

        assertThat(report.newContests()).isEqualTo(1);
        assertThat(report.rejectedLines()).isEqualTo(3);
        assertThat(report.conflictLines()).isZero();
        assertThat(report.rejections()).hasSize(3);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM lotofacil_contest WHERE contest_number = ?", Integer.class, 40011))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM lotofacil_contest WHERE contest_number = ?", Integer.class, 40012))
                .isZero();
    }

    @Test
    void rejectsInvalidRowsAndRejectsWrongHeaderBeforeAnyWrite(@TempDir Path directory) throws IOException {
        Path csv = writeCsv(directory,
                row(40021, "01/01/2025", 1),
                row(40022, "31/02/2025", 1),
                rowWithDraws(40023, "03/01/2025", numbersWithDuplicate()),
                "40024;04/01/2025;1;2");

        ContestImportReport report = historicalContestImportService.importFile(csv);

        assertThat(report.linesRead()).isEqualTo(4);
        assertThat(report.newContests()).isEqualTo(1);
        assertThat(report.rejectedLines()).isEqualTo(3);
        assertThat(report.rejections()).hasSize(3);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM lotofacil_contest WHERE contest_number IN (40021, 40022, 40023, 40024)",
                Integer.class)).isEqualTo(1);

        Path invalidHeader = directory.resolve("invalid-header.csv");
        Files.writeString(invalidHeader, "contest;date;numbers\n" + row(40025, "05/01/2025", 1),
                StandardCharsets.UTF_8);
        ContestImportReport rejectedFile = historicalContestImportService.importFile(invalidHeader);
        assertThat(rejectedFile.fileStructureRejected()).isTrue();
        assertThat(rejectedFile.rejectedLines()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM lotofacil_contest WHERE contest_number = ?", Integer.class, 40025))
                .isZero();
    }

    @Test
    void reprocessingSameFileCountsExistingContestsWithoutDuplicates(@TempDir Path directory) throws IOException {
        Path csv = writeCsv(directory, row(40031, "01/01/2025", 1));

        ContestImportReport firstRun = historicalContestImportService.importFile(csv);
        ContestImportReport secondRun = historicalContestImportService.importFile(csv);

        assertThat(firstRun.newContests()).isEqualTo(1);
        assertThat(secondRun.newContests()).isZero();
        assertThat(secondRun.alreadyExisting()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM lotofacil_contest WHERE contest_number = ?", Integer.class, 40031))
                .isEqualTo(1);
    }

    @Test
    void importsOnlyNewContestsFromIncrementalFile(@TempDir Path directory) throws IOException {
        Path csv = writeCsv(directory, row(40032, "02/01/2025", 1));

        ContestImportReport report = historicalContestImportService.importFile(csv);

        assertThat(report.linesRead()).isEqualTo(1);
        assertThat(report.newContests()).isEqualTo(1);
        assertThat(report.alreadyExisting()).isZero();
        assertThat(report.rejectedLines()).isZero();
        assertThat(report.conflictLines()).isZero();
    }

    @Test
    void importsExistingAndNewContestsAndReportsPersistedContentConflictSeparately(@TempDir Path directory)
            throws IOException {
        LocalDate existingDate = LocalDate.of(2025, 1, 3);
        Contest existing = contest(40033, existingDate, numbersFrom(1, 15));
        persistContest.persist(existing);
        LocalDate conflictDate = LocalDate.of(2025, 1, 5);
        persistContest.persist(contest(40035, conflictDate, numbersFrom(2, 16)));
        Path csv = writeCsv(directory,
                row(40033, "03/01/2025", 1),
                row(40034, "04/01/2025", 1),
                row(40035, "06/01/2025", 1));

        ContestImportReport report = historicalContestImportService.importFile(csv);

        assertThat(report.linesRead()).isEqualTo(3);
        assertThat(report.newContests()).isEqualTo(1);
        assertThat(report.alreadyExisting()).isEqualTo(1);
        assertThat(report.rejectedLines()).isZero();
        assertThat(report.conflictLines()).isEqualTo(1);
        assertThat(report.conflicts()).singleElement()
                .satisfies(conflict -> assertThat(conflict.lineNumber()).isEqualTo(4));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT draw_date FROM lotofacil_contest WHERE contest_number = ?", LocalDate.class, 40035))
                .isEqualTo(conflictDate);
        java.util.List<Integer> retainedDraw = jdbcTemplate.query("""
                SELECT n.drawn_number FROM lotofacil_drawn_number n
                JOIN lotofacil_contest c ON c.id = n.contest_id
                WHERE c.contest_number = ? ORDER BY n.drawn_number
                """, (rs, row) -> rs.getInt(1), 40035);
        assertThat(retainedDraw).isEqualTo(numbersFrom(2, 16));
    }

    @Test
    void preservesSuspiciousDatesAndReportsThemWithoutCorrection(@TempDir Path directory) throws IOException {
        Path csv = writeCsv(directory,
                row(40041, "03/01/2025", 1),
                row(40042, "02/01/2025", 2),
                row(40043, "02/01/2025", 3));

        ContestImportReport report = historicalContestImportService.importFile(csv);

        assertThat(report.newContests()).isEqualTo(3);
        assertThat(report.warnings()).isNotEmpty();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT draw_date FROM lotofacil_contest WHERE contest_number = ?", LocalDate.class, 40042))
                .isEqualTo(LocalDate.of(2025, 1, 2));
    }

    @Test
    void reportsRepeatedDrawnResultsWithoutChangingThem(@TempDir Path directory) throws IOException {
        String sameResult = String.join(";", numbersFrom(1, 15).stream().map(Object::toString).toList());
        Path csv = writeCsv(directory,
                "40044;01/01/2025;" + sameResult,
                "40045;02/01/2025;" + sameResult);

        ContestImportReport report = historicalContestImportService.importFile(csv);

        assertThat(report.newContests()).isEqualTo(2);
        assertThat(report.warnings()).anyMatch(warning -> warning.message().contains("Resultado repetido"));
        assertThat(jdbcTemplate.queryForList(
                "SELECT drawn_number FROM lotofacil_drawn_number " +
                        "WHERE contest_id = (SELECT id FROM lotofacil_contest WHERE contest_number = ?) " +
                        "ORDER BY drawn_number",
                Integer.class, 40045)).containsExactlyElementsOf(numbersFrom(1, 15));
    }

    @Test
    void rollsBackFailedContestAndStopsBeforeProcessingLaterRows(@TempDir Path directory) throws IOException {
        Path csv = writeCsv(directory,
                row(40051, "01/01/2025", 1), row(40052, "02/01/2025", 2));
        jdbcTemplate.execute("ALTER TABLE lotofacil_drawn_number " +
                "ADD CONSTRAINT ck_test_import_persistence_failure CHECK (drawn_number <> 15) NOT VALID");

        try {
            ContestImportReport report = historicalContestImportService.importFile(csv);

            assertThat(report.persistenceAborted()).isTrue();
            assertThat(report.newContests()).isZero();
            assertThat(report.rejectedLines()).isEqualTo(1);
            assertThat(report.notProcessedLines()).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM lotofacil_contest WHERE contest_number IN (40051, 40052)",
                    Integer.class)).isZero();
        } finally {
            jdbcTemplate.execute("ALTER TABLE lotofacil_drawn_number " +
                    "DROP CONSTRAINT ck_test_import_persistence_failure");
        }
    }

    @Test
    void contestPersistenceEvaluationAndLearningEventShareCommitAndDuplicateEmitsNothing() {
        int contestNumber = 40201;
        Contest contest = contest(contestNumber, LocalDate.of(2030, 1, 1), numbersFrom(1, 15));
        savePredictionAttemptService.saveGenerated(contestNumber, numbersFrom(1, 15), contestNumber - 1,
                false, "contest-event-test-v1", 0L, 0L, 17L, 1,
                "NO_ELIGIBLE_EVIDENCE", "NOT_EVALUATED", List.of(), List.of());
        contestEventProbe.reset();

        assertThatThrownBy(() -> contestUnitOfWork.execute(() -> {
            persistContest.persist(contest);
            throw new IllegalStateException("test rollback after event publication");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM lotofacil_contest WHERE contest_number = ?", Integer.class, contestNumber))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM prediction_evaluation WHERE actual_contest_number = ?", Integer.class, contestNumber))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM learning_event WHERE actual_contest_number = ?", Integer.class, contestNumber))
                .isZero();
        assertThat(contestEventProbe.count()).isZero();

        assertThat(persistContest.persist(contest)).isTrue();
        assertThat(contestEventProbe.count()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM prediction_evaluation WHERE actual_contest_number = ?", Integer.class, contestNumber))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM learning_event WHERE actual_contest_number = ?", Integer.class, contestNumber))
                .isEqualTo(1);

        assertThat(persistContest.persist(contest)).isFalse();
        assertThat(contestEventProbe.count()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM prediction_evaluation WHERE actual_contest_number = ?", Integer.class, contestNumber))
                .isEqualTo(1);
    }

    @Test
    void concurrentIdenticalRegistrationsPersistOneContestAndPublishOneEvent() throws Exception {
        int contestNumber = 40202;
        Contest contest = contest(contestNumber, LocalDate.of(2030, 1, 2), numbersFrom(1, 15));
        contestEventProbe.reset();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> concurrentPersist(contest, ready, start));
            var second = executor.submit(() -> concurrentPersist(contest, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM lotofacil_contest WHERE contest_number = ?", Integer.class, contestNumber))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM lotofacil_drawn_number n JOIN lotofacil_contest c ON c.id=n.contest_id
                WHERE c.contest_number = ?
                """, Integer.class, contestNumber)).isEqualTo(15);
        assertThat(contestEventProbe.count()).isEqualTo(1);
    }

    private boolean concurrentPersist(Contest contest, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("concurrent test start timed out");
        return persistContest.persist(contest);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class EventProbeConfiguration {
        @Bean ContestEventProbe contestEventProbe() { return new ContestEventProbe(); }
    }

    static class ContestEventProbe {
        private final AtomicInteger events = new AtomicInteger();

        @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
        void onContestPersisted(ContestResultPersistedEvent ignored) { events.incrementAndGet(); }

        void reset() { events.set(0); }
        int count() { return events.get(); }
    }

    private static Path writeCsv(Path directory, String... rows) throws IOException {
        Path csv = directory.resolve("contests.csv");
        String header = "Concurso;Data;bola 1;bola 2;bola 3;bola 4;bola 5;bola 6;bola 7;" +
                "bola 8;bola 9;bola 10;bola 11;bola 12;bola 13;bola 14;bola 15";
        Files.writeString(csv, header + System.lineSeparator() + String.join(System.lineSeparator(), rows)
                + System.lineSeparator(), StandardCharsets.UTF_8);
        return csv;
    }

    private static String row(int contestNumber, String date, int firstDrawnNumber) {
        return rowWithDraws(contestNumber, date,
                IntStream.rangeClosed(firstDrawnNumber, firstDrawnNumber + 14).boxed().toList());
    }

    private static String rowWithDraws(int contestNumber, String date, List<Integer> drawnNumbers) {
        return contestNumber + ";" + date + ";" + String.join(";", drawnNumbers.stream()
                .map(Object::toString).toList());
    }

    private static List<Integer> numbersWithDuplicate() {
        return List.of(1, 1, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15);
    }

    private static Contest contest(int number, LocalDate drawDate, List<Integer> drawnNumbers) {
        return new Contest(number, drawDate, drawnNumbers);
    }

    private static List<Integer> numbersFrom(int first, int last) {
        return IntStream.rangeClosed(first, last).boxed().toList();
    }
}
