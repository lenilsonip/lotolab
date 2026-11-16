package br.com.lotolab.contests.adapter.in.web;

import br.com.lotolab.api.ApiError;
import br.com.lotolab.contests.application.port.in.ImportContestHistoryUseCase;
import br.com.lotolab.contests.application.port.in.PreviewContestImportUseCase;
import br.com.lotolab.contests.application.ContestImportPreview;
import br.com.lotolab.contests.application.ContestImportReport;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/importacao-concursos")
public class ContestImportController {
    public static final String TOKEN_HEADER = "X-LotoLab-Import-Token";
    private static final long MAX_FILE_SIZE = 1024L * 1024L;

    private final ImportContestHistoryUseCase importContests;
    private final PreviewContestImportUseCase previewImport;
    private final String configuredToken;

    public ContestImportController(ImportContestHistoryUseCase importContests,
                                   PreviewContestImportUseCase previewImport,
                                   @Value("${lotolab.import.api-token:}") String configuredToken) {
        this.importContests = importContests;
        this.previewImport = previewImport;
        this.configuredToken = configuredToken;
    }

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> preview(@RequestHeader(value = TOKEN_HEADER, required = false) String token,
                                     @RequestParam("file") MultipartFile file,
                                     HttpServletRequest request) throws IOException {
        ResponseEntity<ApiError> access = authorize(token, request);
        if (access != null) return access;
        ResponseEntity<ApiError> fileError = validateFile(file);
        if (fileError != null) return fileError;
        ContestImportPreview preview = previewImport.previewFile(file.getBytes());
        return ResponseEntity.ok(preview);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> importContests(@RequestHeader(value = TOKEN_HEADER, required = false) String token,
                                            @RequestParam("file") MultipartFile file,
                                            @RequestParam(defaultValue = "false") boolean confirm,
                                            HttpServletRequest request) throws IOException {
        ResponseEntity<ApiError> access = authorize(token, request);
        if (access != null) return access;
        ResponseEntity<ApiError> fileError = validateFile(file);
        if (fileError != null) return fileError;
        if (!confirm) {
            return ResponseEntity.badRequest().body(new ApiError("IMPORT_CONFIRMATION_REQUIRED",
                    "Confirme explicitamente a importação após revisar a prévia", List.of()));
        }
        ContestImportReport report = importContests.importFile(file.getBytes());
        return ResponseEntity.ok(report);
    }

    private ResponseEntity<ApiError> authorize(String suppliedToken, HttpServletRequest request) {
        if (configuredToken == null || configuredToken.isBlank() || configuredToken.length() < 32) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ApiError(
                    "IMPORT_DISABLED", "Configure LOTOLAB_IMPORT_TOKEN para habilitar a importação administrativa",
                    List.of()));
        }
        if (!isLoopback(request.getRemoteAddr()) || suppliedToken == null
                || !MessageDigest.isEqual(configuredToken.getBytes(StandardCharsets.UTF_8),
                suppliedToken.getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiError(
                    "IMPORT_FORBIDDEN", "Acesso administrativo não autorizado", List.of()));
        }
        return null;
    }

    private ResponseEntity<ApiError> validateFile(MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (file.isEmpty() || filename == null || !filename.toLowerCase(java.util.Locale.ROOT).endsWith(".csv")) {
            return ResponseEntity.badRequest().body(new ApiError(
                    "INVALID_CSV", "Envie um arquivo CSV válido", List.of()));
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(new ApiError(
                    "CSV_TOO_LARGE", "O arquivo CSV deve ter no máximo 1 MB", List.of()));
        }
        return null;
    }

    private static boolean isLoopback(String remoteAddress) {
        try {
            return InetAddress.getByName(remoteAddress).isLoopbackAddress();
        } catch (Exception exception) {
            return false;
        }
    }
}
