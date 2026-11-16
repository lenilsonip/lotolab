package br.com.lotolab.infrastructure.importing;

import br.com.lotolab.contests.application.ContestImportIssue;
import br.com.lotolab.contests.application.ContestImportPreview;
import br.com.lotolab.contests.application.ContestImportReport;
import br.com.lotolab.contests.application.port.in.ImportContestHistoryUseCase;
import br.com.lotolab.contests.application.port.in.PreviewContestImportUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.regex.Pattern;

@Component
@ConditionalOnProperty(name = "lotolab.import.file")
public class HistoricalContestImportRunner implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(HistoricalContestImportRunner.class);
    private final ImportContestHistoryUseCase importService;
    private final PreviewContestImportUseCase previewService;
    private final String file;
    private final String jdbcTarget;
    private final boolean dryRun;
    private final boolean confirmed;
    private final ConfigurableApplicationContext applicationContext;

    private static final Pattern JDBC_CREDENTIALS = Pattern.compile("(jdbc:postgresql://)[^/@]+@");

    public HistoricalContestImportRunner(ImportContestHistoryUseCase importService,
                                         PreviewContestImportUseCase previewService,
                                         org.springframework.core.env.Environment environment,
                                         ConfigurableApplicationContext applicationContext) {
        this.importService = importService;
        this.previewService = previewService;
        this.applicationContext = applicationContext;
        this.file = environment.getRequiredProperty("lotolab.import.file");
        this.jdbcTarget = safeJdbcTarget(environment.getProperty("spring.datasource.url", ""));
        this.dryRun = environment.getProperty("lotolab.import.dry-run", Boolean.class, true);
        this.confirmed = environment.getProperty("lotolab.import.confirm", Boolean.class, false);
    }

    @Override
    public void run(ApplicationArguments arguments) throws Exception {
        try {
            if (!dryRun && !confirmed) {
                throw new IllegalStateException("Importação efetiva exige --lotolab.import.confirm=true; "
                        + "nenhum concurso foi gravado");
            }
            if (dryRun) {
                Instant started = Instant.now();
                ContestImportPreview preview = previewService.previewFile(Path.of(file));
                LOGGER.info("Arquivo: {}; banco alvo: {}; {}; duração: {} ms", file, jdbcTarget,
                        preview.summary(), Duration.between(started, Instant.now()).toMillis());
                logIssues(preview.rejections(), preview.conflicts(), preview.warnings());
                return;
            }

            Instant started = Instant.now();
            ContestImportReport report = importService.importFile(Path.of(file));
            LOGGER.info("Arquivo: {}; banco alvo: {}; {}; duração: {} ms", file, jdbcTarget,
                    report.summary(), Duration.between(started, Instant.now()).toMillis());
            logIssues(report.rejections(), report.conflicts(), report.warnings());
            if (report.fileStructureRejected() || report.persistenceAborted()) {
                throw new IllegalStateException("Importação não concluída: " + report.summary());
            }
        } finally {
            applicationContext.close();
        }
    }

    private void logIssues(java.util.List<ContestImportIssue> rejections,
                           java.util.List<ContestImportIssue> conflicts,
                           java.util.List<ContestImportIssue> warnings) {
        rejections.forEach(issue -> logIssue("Rejeição", issue));
        conflicts.forEach(issue -> logIssue("Conflito", issue));
        warnings.forEach(issue -> logIssue("Inconsistência", issue));
    }

    private static String safeJdbcTarget(String url) {
        String withoutCredentials = JDBC_CREDENTIALS.matcher(url).replaceFirst("$1");
        int queryIndex = withoutCredentials.indexOf('?');
        return queryIndex < 0 ? withoutCredentials : withoutCredentials.substring(0, queryIndex);
    }

    private void logIssue(String category, ContestImportIssue issue) {
        LOGGER.warn("{} na linha {}: {}", category, issue.lineNumber(), issue.message());
    }
}
