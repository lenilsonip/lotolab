package br.com.lotolab.infrastructure.gemini;

import br.com.lotolab.application.AiIntegrationException;
import br.com.lotolab.application.port.AiFeedbackGenerator;
import br.com.lotolab.infrastructure.logging.LogContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class GeminiIntegrationService implements AiFeedbackGenerator {
    private static final Logger LOGGER = LoggerFactory.getLogger(GeminiIntegrationService.class);
    private final RestClient restClient;
    private final String apiUrl;
    private final String fallbackApiUrl;
    private final String apiKey;

    public GeminiIntegrationService(RestClient geminiRestClient,
                                    @Value("${gemini.api.url}") String apiUrl,
                                    @Value("${gemini.api.fallback-url:}") String fallbackApiUrl,
                                    @Value("${gemini.api.key:}") String apiKey) {
        this.restClient = Objects.requireNonNull(geminiRestClient);
        this.apiUrl = Objects.requireNonNull(apiUrl);
        this.fallbackApiUrl = fallbackApiUrl == null ? "" : fallbackApiUrl.trim();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    @Override
    public boolean isAvailable() { return !apiKey.isBlank(); }

    @Override
    public String modelName() {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("/models/([^:/?]+)").matcher(apiUrl);
        return matcher.find() ? matcher.group(1) : "configured-by-url";
    }

    @Override
    public String generateFeedback(String promptText) {
        if (promptText == null || promptText.isBlank()) {
            throw new IllegalArgumentException("O prompt de análise é obrigatório");
        }
        if (apiKey.isBlank()) {
            throw new AiIntegrationException("A integração Gemini não está configurada");
        }

        List<String> endpoints = new ArrayList<>(List.of(apiUrl));
        if (!fallbackApiUrl.isBlank() && !fallbackApiUrl.equals(apiUrl)) endpoints.add(fallbackApiUrl);
        long startedAt = System.nanoTime();
        try (LogContext ignored = LogContext.component("Gemini", "generate-content")) {
            LOGGER.info("phase=START model={} promptLength={} fallbackConfigured={}",
                    modelName(), promptText.length(), endpoints.size() > 2);
            for (int attempt = 0; attempt < endpoints.size(); attempt++) {
                if (attempt > 0) pauseBeforeRetry(attempt - 1);
                String endpointRole = attempt == 0 ? "primary" : "fallback";
                long attemptStartedAt = System.nanoTime();
                LogContext.phase("START");
                try {
                    Map<?, ?> response = restClient.post()
                            .uri(endpoints.get(attempt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("x-goog-api-key", apiKey)
                            .body(Map.of("contents", List.of(Map.of(
                                    "parts", List.of(Map.of("text", promptText))))))
                            .retrieve()
                            .body(Map.class);
                    String feedback = responseText(response);
                    if (feedback == null || feedback.isBlank()) {
                        LOGGER.warn("phase=ERROR endpointRole={} failure=invalid-response durationMs={}", endpointRole,
                                elapsedMs(attemptStartedAt));
                        throw new AiIntegrationException("A resposta do Gemini não continha uma análise");
                    }
                    LogContext.phase("SUCCESS");
                    LOGGER.info("endpointRole={} model={} outputLength={} durationMs={} totalDurationMs={}",
                            endpointRole, modelName(), feedback.length(), elapsedMs(attemptStartedAt), elapsedMs(startedAt));
                    return feedback;
                } catch (RestClientResponseException exception) {
                    int status = exception.getStatusCode().value();
                    LogContext.phase("ERROR");
                    LOGGER.warn("endpointRole={} attempt={} providerStatus={} durationMs={}", endpointRole,
                            attempt + 1, status, elapsedMs(attemptStartedAt));
                    if (status == 429) throw AiIntegrationException.rateLimited(exception);
                    if (status == 503 && attempt < endpoints.size() - 1) continue;
                    if (status == 503) throw AiIntegrationException.temporarilyUnavailable(exception);
                    throw new AiIntegrationException("O serviço Gemini está temporariamente indisponível", exception);
                } catch (RestClientException exception) {
                    LogContext.phase("ERROR");
                    LOGGER.warn("endpointRole={} attempt={} failure={} durationMs={}", endpointRole, attempt + 1,
                            exception.getClass().getSimpleName(), elapsedMs(attemptStartedAt));
                    if (attempt == 0 && endpoints.size() > 2) attempt = 1;
                    if (attempt < endpoints.size() - 1) continue;
                    throw AiIntegrationException.temporarilyUnavailable(exception);
                }
            }
            LogContext.phase("ERROR");
            LOGGER.error("failure=attempts-exhausted totalDurationMs={}", elapsedMs(startedAt));
            throw AiIntegrationException.temporarilyUnavailable(null);
        }
    }

    private static long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }

    private void pauseBeforeRetry(int previousAttempt) {
        try {
            Thread.sleep(previousAttempt == 0 ? 1_000 : 2_000);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw AiIntegrationException.temporarilyUnavailable(exception);
        }
    }

    private String responseText(Map<?, ?> response) {
        if (response == null || !(response.get("candidates") instanceof List<?> candidates)
                || candidates.isEmpty() || !(candidates.getFirst() instanceof Map<?, ?> candidate)
                || !(candidate.get("content") instanceof Map<?, ?> content)
                || !(content.get("parts") instanceof List<?> parts) || parts.isEmpty()
                || !(parts.getFirst() instanceof Map<?, ?> part)
                || !(part.get("text") instanceof String text)) {
            return null;
        }
        return text;
    }
}
