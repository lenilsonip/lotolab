package br.com.lotolab.infrastructure.gemini;

import br.com.lotolab.application.AiIntegrationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.web.client.RestClient;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GeminiIntegrationServiceTest {
    private static final String API_URL = "https://generativelanguage.googleapis.test/v1beta/models/gemini-test:generateContent";
    private static final String FALLBACK_URL = "https://generativelanguage.googleapis.test/v1beta/models/gemini-fallback:generateContent";

    @Test
    void sendsGenerateContentRequestAndReturnsFirstCandidateText() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GeminiIntegrationService service = new GeminiIntegrationService(builder.build(), API_URL, FALLBACK_URL, "test-key");
        server.expect(requestTo(API_URL))
                .andExpect(method(POST))
                .andExpect(header("x-goog-api-key", "test-key"))
                .andExpect(jsonPath("$.contents[0].parts[0].text").value("Prompt de teste"))
                .andRespond(withSuccess("""
                        {"candidates":[{"content":{"parts":[{"text":"Resposta Gemini simulada."}],"role":"model"}}]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(service.generateFeedback("Prompt de teste")).isEqualTo("Resposta Gemini simulada.");
        server.verify();
    }

    @Test
    void convertsProviderHttpErrorsToAiIntegrationException() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GeminiIntegrationService service = new GeminiIntegrationService(builder.build(), API_URL, FALLBACK_URL, "test-key");
        server.expect(requestTo(API_URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> service.generateFeedback("Prompt de teste"))
                .isInstanceOf(AiIntegrationException.class)
                .hasMessageContaining("temporariamente indisponível");
        server.verify();
    }

    @Test
    void rejectsResponseWithoutCandidateText() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GeminiIntegrationService service = new GeminiIntegrationService(builder.build(), API_URL, FALLBACK_URL, "test-key");
        server.expect(requestTo(API_URL)).andRespond(withSuccess("{\"candidates\":[]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> service.generateFeedback("Prompt de teste"))
                .isInstanceOf(AiIntegrationException.class)
                .hasMessageContaining("não continha uma análise");
        server.verify();
    }

    @Test
    void fallsBackAfterPrimaryModelIsTemporarilyUnavailable() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GeminiIntegrationService service = new GeminiIntegrationService(builder.build(), API_URL, FALLBACK_URL, "test-key");
        server.expect(requestTo(API_URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo(FALLBACK_URL)).andRespond(withSuccess("""
                {"candidates":[{"content":{"parts":[{"text":"Resposta após retry."}]}}]}
                """, MediaType.APPLICATION_JSON));

        assertThat(service.generateFeedback("Prompt de teste")).isEqualTo("Resposta após retry.");
        server.verify();
    }

    @Test
    void fallsBackToAlternateModelAfterPrimaryReturns503() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GeminiIntegrationService service = new GeminiIntegrationService(builder.build(), API_URL, FALLBACK_URL, "test-key");
        server.expect(requestTo(API_URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo(FALLBACK_URL)).andRespond(withSuccess("""
                {"candidates":[{"content":{"parts":[{"text":"Resposta do modelo alternativo."}]}}]}
                """, MediaType.APPLICATION_JSON));

        assertThat(service.generateFeedback("Prompt de teste")).isEqualTo("Resposta do modelo alternativo.");
        server.verify();
    }

    @Test
    void fallsBackToAlternateModelAfterTransportTimeout() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GeminiIntegrationService service = new GeminiIntegrationService(builder.build(), API_URL, FALLBACK_URL, "test-key");
        server.expect(requestTo(API_URL)).andRespond(withException(new IOException("simulated timeout")));
        server.expect(requestTo(FALLBACK_URL)).andRespond(withSuccess("""
                {"candidates":[{"content":{"parts":[{"text":"Resposta após timeout."}]}}]}
                """, MediaType.APPLICATION_JSON));

        assertThat(service.generateFeedback("Prompt de teste")).isEqualTo("Resposta após timeout.");
        server.verify();
    }
    @Test
    void marksUnavailableWhenBothModelsReturn503() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GeminiIntegrationService service = new GeminiIntegrationService(builder.build(), API_URL, FALLBACK_URL, "test-key");
        server.expect(requestTo(API_URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo(FALLBACK_URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> service.generateFeedback("Prompt de teste"))
                .isInstanceOf(AiIntegrationException.class)
                .extracting(exception -> ((AiIntegrationException) exception).temporarilyUnavailable())
                .isEqualTo(true);
        server.verify();
    }
    @Test
    void convertsQuotaResponseToRateLimitedException() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GeminiIntegrationService service = new GeminiIntegrationService(builder.build(), API_URL, FALLBACK_URL, "test-key");
        server.expect(requestTo(API_URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> service.generateFeedback("Prompt de teste"))
                .isInstanceOf(AiIntegrationException.class)
                .extracting(exception -> ((AiIntegrationException) exception).rateLimited()).isEqualTo(true);
        server.verify();
    }

    @Test
    void failsClearlyWhenApiKeyIsNotConfiguredWithoutMakingARequest() {
        GeminiIntegrationService service = new GeminiIntegrationService(RestClient.builder().build(), API_URL, FALLBACK_URL, " ");

        assertThat(service.isAvailable()).isFalse();
        assertThatThrownBy(() -> service.generateFeedback("Prompt de teste"))
                .isInstanceOf(AiIntegrationException.class)
                .hasMessageContaining("não está configurada");
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void logsProviderStatusWithoutLoggingApiKeyOrPrompt(CapturedOutput output) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GeminiIntegrationService service = new GeminiIntegrationService(builder.build(), API_URL, FALLBACK_URL,
                "SECRET_TEST_API_KEY");
        server.expect(requestTo(API_URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> service.generateFeedback("PRIVATE_TEST_PROMPT_CONTENT"))
                .isInstanceOf(AiIntegrationException.class);
        server.verify();

        assertThat(output.getOut()).contains("providerStatus=400").doesNotContain("SECRET_TEST_API_KEY")
                .doesNotContain("PRIVATE_TEST_PROMPT_CONTENT");
    }
}
