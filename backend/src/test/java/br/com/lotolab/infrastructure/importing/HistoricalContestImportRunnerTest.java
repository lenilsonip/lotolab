package br.com.lotolab.infrastructure.importing;

import br.com.lotolab.contests.application.ContestImportPreview;
import br.com.lotolab.contests.application.ContestImportReport;
import br.com.lotolab.application.importing.HistoricalContestImportService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.mock.env.MockEnvironment;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HistoricalContestImportRunnerTest {

    private static final Path CSV = Path.of("data/raw/history.csv");

    @Test
    void usesReadOnlyPreviewByDefault() throws Exception {
        HistoricalContestImportService service = mock(HistoricalContestImportService.class);
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        when(service.previewFile(CSV)).thenReturn(new ContestImportPreview(
                0, 0, 0, 0, 0, 0, false, List.of(), List.of(), List.of()));
        HistoricalContestImportRunner runner = new HistoricalContestImportRunner(service, service,
                new MockEnvironment().withProperty("lotolab.import.file", CSV.toString()), context);

        runner.run(mock(ApplicationArguments.class));

        verify(service).previewFile(CSV);
        verify(service, never()).importFile(CSV);
        verify(context).close();
    }

    @Test
    void refusesEffectiveImportWithoutExplicitConfirmation() throws Exception {
        HistoricalContestImportService service = mock(HistoricalContestImportService.class);
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        HistoricalContestImportRunner runner = new HistoricalContestImportRunner(service, service,
                new MockEnvironment().withProperty("lotolab.import.file", CSV.toString())
                        .withProperty("lotolab.import.dry-run", "false"), context);

        assertThatThrownBy(() -> runner.run(mock(ApplicationArguments.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("nenhum concurso foi gravado");
        verify(service, never()).importFile(CSV);
        verify(context).close();
    }

    @Test
    void importsOnlyWhenBothWriteModeAndExplicitConfirmationAreSet() throws Exception {
        HistoricalContestImportService service = mock(HistoricalContestImportService.class);
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        when(service.importFile(CSV)).thenReturn(new ContestImportReport(
                0, 0, 0, 0, 0, 0, false, false, List.of(), List.of(), List.of()));
        HistoricalContestImportRunner runner = new HistoricalContestImportRunner(service, service,
                new MockEnvironment().withProperty("lotolab.import.file", CSV.toString())
                        .withProperty("lotolab.import.dry-run", "false")
                        .withProperty("lotolab.import.confirm", "true"), context);

        runner.run(mock(ApplicationArguments.class));

        verify(service).importFile(CSV);
        verify(service, never()).previewFile(CSV);
        verify(context).close();
    }
}
