package br.com.lotolab.api;

import br.com.lotolab.contests.application.ContestImportPreview;
import br.com.lotolab.contests.application.ContestImportReport;
import br.com.lotolab.contests.adapter.in.web.ContestImportController;
import br.com.lotolab.contests.application.port.in.ImportContestHistoryUseCase;
import br.com.lotolab.contests.application.port.in.PreviewContestImportUseCase;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ContestImportController.class)
@Import(ApiExceptionHandler.class)
@TestPropertySource(properties = "lotolab.import.api-token=0123456789abcdef0123456789abcdef")
class ContestImportApiContractTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean ImportContestHistoryUseCase importer;
    @MockitoBean PreviewContestImportUseCase previewer;

    @Test
    void previewRemainsReadOnlyAndReturnsExistingPreviewContract() throws Exception {
        when(previewer.previewFile(any(byte[].class))).thenReturn(new ContestImportPreview(
                1, 1, 1, 0, 0, 0, false, List.of(), List.of(), List.of()));
        mockMvc.perform(multipart("/api/admin/importacao-concursos/preview")
                        .file(file()).header("X-LotoLab-Import-Token", token()).with(ContestImportApiContractTest::loopback))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linesRead").value(1))
                .andExpect(jsonPath("$.wouldInsert").value(1));
    }

    @Test
    void requiresExplicitConfirmationBeforeImporting() throws Exception {
        mockMvc.perform(multipart("/api/admin/importacao-concursos")
                        .file(file()).header("X-LotoLab-Import-Token", token()).with(ContestImportApiContractTest::loopback))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IMPORT_CONFIRMATION_REQUIRED"));
    }

    @Test
    void confirmedImportReturnsReportContract() throws Exception {
        when(importer.importFile(any(byte[].class))).thenReturn(new ContestImportReport(
                1, 1, 0, 0, 0, 0, false, false, List.of(), List.of(), List.of()));
        mockMvc.perform(multipart("/api/admin/importacao-concursos")
                        .file(file()).param("confirm", "true").header("X-LotoLab-Import-Token", token())
                        .with(ContestImportApiContractTest::loopback))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newContests").value(1));
    }

    private static org.springframework.mock.web.MockHttpServletRequest loopback(
            org.springframework.mock.web.MockHttpServletRequest request) {
        request.setRemoteAddr("127.0.0.1");
        return request;
    }

    private static MockMultipartFile file() {
        return new MockMultipartFile("file", "history.csv", "text/csv", "header\n".getBytes());
    }

    private static String token() { return "0123456789abcdef0123456789abcdef"; }
}
