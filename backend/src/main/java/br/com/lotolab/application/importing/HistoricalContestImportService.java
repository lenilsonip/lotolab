package br.com.lotolab.application.importing;

import br.com.lotolab.contests.application.port.in.ImportContestHistoryUseCase;
import br.com.lotolab.contests.application.port.in.PreviewContestImportUseCase;
import br.com.lotolab.contests.application.port.in.PersistContestUseCase;
import br.com.lotolab.contests.application.ContestImportIssue;
import br.com.lotolab.contests.application.ContestImportPreview;
import br.com.lotolab.contests.application.ContestImportReport;
import br.com.lotolab.contests.application.port.out.ContestRepository;
import br.com.lotolab.contests.application.port.out.ContestResearchTrigger;
import br.com.lotolab.domain.Contest;
import br.com.lotolab.domain.ContestConflictException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

public class HistoricalContestImportService implements ImportContestHistoryUseCase, PreviewContestImportUseCase {
    private static final Logger LOGGER = LoggerFactory.getLogger(HistoricalContestImportService.class);

    private final ContestFileReader fileReader;
    private final ContestRepository contestRepository;
    private final PersistContestUseCase persistence;
    private final ContestResearchTrigger researchTrigger;

    public HistoricalContestImportService(ContestFileReader fileReader,
                                          ContestRepository contestRepository,
                                          PersistContestUseCase persistence,
                                          ContestResearchTrigger researchTrigger) {
        this.fileReader = Objects.requireNonNull(fileReader);
        this.contestRepository = Objects.requireNonNull(contestRepository);
        this.persistence = Objects.requireNonNull(persistence);
        this.researchTrigger = Objects.requireNonNull(researchTrigger);
    }

    public ContestImportReport importFile(Path path) throws IOException {
        return importFile(() -> fileReader.read(path), "file");
    }

    public ContestImportReport importFile(byte[] content) throws IOException {
        return importFile(() -> fileReader.read(content), "upload");
    }

    private ContestImportReport importFile(SupplierWithIOException<ParsedContestFile> source,
                                           String sourceType) throws IOException {
        long startedAt = System.nanoTime();
        LOGGER.info("phase=START sourceType={}", sourceType);
        try {
            ContestImportReport report = importParsedFile(source.get());
            LOGGER.info("operation=historical-import linesRead={} inserted={} existing={} rejected={} conflicts={} notProcessed={} durationMs={}",
                    report.linesRead(), report.newContests(), report.alreadyExisting(), report.rejectedLines(),
                    report.conflictLines(), report.notProcessedLines(), (System.nanoTime() - startedAt) / 1_000_000L);
            return report;
        } catch (IOException | RuntimeException exception) {
            LOGGER.error("operation=historical-import sourceType={} cause={} durationMs={}",
                    sourceType, exception.getClass().getSimpleName(),
                    (System.nanoTime() - startedAt) / 1_000_000L);
            throw exception;
        }
    }

    @FunctionalInterface
    private interface SupplierWithIOException<T> {
        T get() throws IOException;
    }

    private ContestImportReport importParsedFile(ParsedContestFile parsedFile) {
        List<ContestImportIssue> rejections = new ArrayList<>(parsedFile.rejectedRows());
        List<ContestImportIssue> conflicts = new ArrayList<>();
        if (!parsedFile.structureValid()) {
            return new ContestImportReport(parsedFile.linesRead(), 0, 0, rejections.size(), 0, 0,
                    true, false, rejections, conflicts, parsedFile.warnings());
        }
        int newContests = 0;
        int alreadyExisting = 0;
        int notProcessed = 0;
        boolean persistenceAborted = false;

        for (int index = 0; index < parsedFile.contests().size(); index++) {
            ParsedContestRow row = parsedFile.contests().get(index);
            try {
                boolean inserted = persistence.persist(row.contest());
                if (inserted) {
                    newContests++;
                } else {
                    alreadyExisting++;
                }
            } catch (ContestConflictException exception) {
                conflicts.add(new ContestImportIssue(row.lineNumber(),
                        "Concurso já existe no banco com data ou dezenas diferentes"));
            } catch (RuntimeException exception) {
                rejections.add(new ContestImportIssue(row.lineNumber(),
                        "Falha de persistência; a transação deste concurso foi revertida"));
                persistenceAborted = true;
                notProcessed = parsedFile.contests().size() - index - 1;
                break;
            }
        }

        List<ContestImportIssue> warnings = new ArrayList<>(parsedFile.warnings());
        if (!persistenceAborted && newContests > 0 && !researchTrigger.trigger("IMPORT")) {
            warnings.add(new ContestImportIssue(0,
                    "Os concursos foram persistidos, mas a pesquisa incremental de padrões falhou"));
        }

        return new ContestImportReport(parsedFile.linesRead(), newContests, alreadyExisting,
                rejections.size(), conflicts.size(), notProcessed, false, persistenceAborted,
                rejections, conflicts, warnings);
    }

    /**
     * Parses and compares the file with persisted contests without writing to the database.
     */
    public ContestImportPreview previewFile(Path path) throws IOException {
        return preview(path, "file");
    }

    public ContestImportPreview previewFile(byte[] content) throws IOException {
        return preview(() -> fileReader.read(content), "upload");
    }

    private ContestImportPreview preview(Path path, String sourceType) throws IOException {
        return preview(() -> fileReader.read(path), sourceType);
    }

    private ContestImportPreview preview(SupplierWithIOException<ParsedContestFile> source,
                                         String sourceType) throws IOException {
        long startedAt = System.nanoTime();
            LOGGER.info("phase=START sourceType={}", sourceType);
            try {
                ContestImportPreview preview = previewParsedFile(source.get());
                LOGGER.info("sourceType={} linesRead={} valid={} wouldInsert={} existing={} conflicts={} rejected={} durationMs={}",
                        sourceType, preview.linesRead(), preview.validLines(), preview.wouldInsert(),
                        preview.alreadyExisting(), preview.conflictLines(), preview.rejectedLines(),
                        (System.nanoTime() - startedAt) / 1_000_000L);
                return preview;
            } catch (IOException | RuntimeException exception) {
                LOGGER.error("sourceType={} cause={} durationMs={}", sourceType,
                        exception.getClass().getSimpleName(), (System.nanoTime() - startedAt) / 1_000_000L);
                throw exception;
            }
    }

    private ContestImportPreview previewParsedFile(ParsedContestFile parsedFile) {
        List<ContestImportIssue> conflicts = new ArrayList<>();
        int wouldInsert = 0;
        int alreadyExisting = 0;

        if (!parsedFile.structureValid()) {
            return new ContestImportPreview(parsedFile.linesRead(), 0, 0, 0, 0,
                    parsedFile.rejectedRows().size(), true, parsedFile.rejectedRows(),
                    conflicts, parsedFile.warnings());
        }

        Map<Integer, Contest> existingContests = contestRepository.findAll().stream()
                .collect(Collectors.toMap(Contest::number, Function.identity()));
        for (ParsedContestRow row : parsedFile.contests()) {
            var persisted = existingContests.get(row.contest().number());
            if (persisted == null) {
                wouldInsert++;
            } else if (persisted.equals(row.contest())) {
                alreadyExisting++;
            } else {
                conflicts.add(new ContestImportIssue(row.lineNumber(),
                        "Concurso já existe no banco com data ou dezenas diferentes"));
            }
        }

        int validLines = parsedFile.contests().size();
        return new ContestImportPreview(parsedFile.linesRead(), validLines, wouldInsert,
                alreadyExisting, conflicts.size(), parsedFile.rejectedRows().size(), false,
                parsedFile.rejectedRows(), conflicts, parsedFile.warnings());
    }
}
