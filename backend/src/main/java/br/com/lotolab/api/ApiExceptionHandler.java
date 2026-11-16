package br.com.lotolab.api;

import br.com.lotolab.application.ContestNotFoundException;
import br.com.lotolab.application.AiIntegrationException;
import br.com.lotolab.application.PersistedDataInconsistencyException;
import br.com.lotolab.application.MachineLearningService;
import br.com.lotolab.application.MlTrainingJobNotFoundException;
import br.com.lotolab.domain.ContestConflictException;
import br.com.lotolab.infrastructure.logging.LogContext;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ContestConflictException.class)
    ResponseEntity<ApiError> conflict(ContestConflictException exception) {
        return error(HttpStatus.CONFLICT, "CONTEST_CONFLICT", exception.getMessage(), List.of());
    }

    @ExceptionHandler({ContestNotFoundException.class,
            br.com.lotolab.contests.application.ContestNotFoundException.class})
    ResponseEntity<ApiError> notFound(RuntimeException exception) {
        return error(HttpStatus.NOT_FOUND, "CONTEST_NOT_FOUND", exception.getMessage(), List.of());
    }

    @ExceptionHandler(MachineLearningService.MlModelNotFoundException.class)
    ResponseEntity<ApiError> mlModelNotFound(MachineLearningService.MlModelNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, "ML_MODEL_NOT_FOUND", "Modelo experimental não encontrado", List.of());
    }

    @ExceptionHandler(MlTrainingJobNotFoundException.class)
    ResponseEntity<ApiError> mlTrainingJobNotFound(MlTrainingJobNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, "ML_TRAINING_NOT_FOUND", "Treinamento não encontrado", List.of());
    }

    @ExceptionHandler(PersistedDataInconsistencyException.class)
    ResponseEntity<ApiError> persistedDataInconsistency(PersistedDataInconsistencyException exception) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "PERSISTED_DATA_INCONSISTENCY",
                "Os dados persistidos do concurso são inconsistentes", List.of());
    }

    @ExceptionHandler(AiIntegrationException.class)
    ResponseEntity<ApiError> aiIntegration(AiIntegrationException exception) {
        boolean unavailable = exception.rateLimited() || exception.temporarilyUnavailable();
        HttpStatus status = unavailable ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.BAD_GATEWAY;
        LogContext.phase("ERROR");
        LOGGER.warn("Gemini integration failed; rateLimited={}, temporarilyUnavailable={}",
                exception.rateLimited(), exception.temporarilyUnavailable());
        String message = exception.rateLimited()
                ? "Limite temporário do serviço Gemini atingido. Tente novamente mais tarde."
                : exception.temporarilyUnavailable()
                ? "O Gemini está temporariamente indisponível. Tente novamente em instantes."
                : "Serviço de IA indisponível. Verifique os logs da aplicação para o código HTTP do provedor.";
        return error(status, "AI_SERVICE_UNAVAILABLE", message, List.of());
    }
    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class,
            HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            IllegalArgumentException.class})
    ResponseEntity<ApiError> badRequest(Exception exception) {
        List<String> details = exception instanceof MethodArgumentNotValidException validation
                ? validation.getBindingResult().getFieldErrors().stream()
                .map(field -> field.getField() + ": " + field.getDefaultMessage()).toList()
                : List.of();
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "A requisição é inválida", details);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiError> uploadTooLarge(MaxUploadSizeExceededException exception) {
        return error(HttpStatus.PAYLOAD_TOO_LARGE, "CSV_TOO_LARGE", "O arquivo CSV deve ter no máximo 1 MB", List.of());
    }

    private ResponseEntity<ApiError> error(HttpStatus status, String code, String message, List<String> details) {
        LogContext.phase("ERROR");
        return ResponseEntity.status(status).body(new ApiError(code, message, details));
    }
}
