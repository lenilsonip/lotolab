package br.com.lotolab.api;

import br.com.lotolab.application.ComparePredictionService;
import br.com.lotolab.application.AiIntegrationException;
import br.com.lotolab.application.FeedbackPromptService;
import br.com.lotolab.application.port.AiFeedbackGenerator;
import br.com.lotolab.domain.PredictionComparison;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.stream.IntStream;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ExperimentController.class)
@Import(ApiExceptionHandler.class)
class ExperimentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ComparePredictionService comparePredictionService;

    @MockitoBean
    private FeedbackPromptService feedbackPromptService;

    @MockitoBean
    private AiFeedbackGenerator aiFeedbackGenerator;

    @Test
    void returnsPromptForValidAnalysisRequest() throws Exception {
        PredictionComparison comparison = new PredictionComparison(
                numbers(1, 15), numbers(5, 19), 11,
                numbers(5, 15), numbers(1, 4), numbers(16, 19));
        when(comparePredictionService.compare(anyList(), eq(3802))).thenReturn(comparison);
        when(feedbackPromptService.createPrompt(comparison)).thenReturn("Prompt de análise pronto.");
        when(aiFeedbackGenerator.isAvailable()).thenReturn(true);
        when(aiFeedbackGenerator.generateFeedback("Prompt de análise pronto."))
                .thenReturn("Análise estatística gerada pela IA.");

        mockMvc.perform(post("/api/experimentos/analise")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"predictedNumbers":[1,2,3,4,5,6,7,8,9,10,11,12,13,14,15],"actualContestNumber":3802}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.promptText").value("Prompt de análise pronto."))
                .andExpect(jsonPath("$.aiFeedback").value("Análise estatística gerada pela IA."));

        verify(comparePredictionService).compare(numbers(1, 15), 3802);
        verify(feedbackPromptService).createPrompt(comparison);
        verify(aiFeedbackGenerator).isAvailable();
        verify(aiFeedbackGenerator).generateFeedback("Prompt de análise pronto.");
    }

    @Test
    void rejectsRequestWithFewerThanFifteenNumbers() throws Exception {
        mockMvc.perform(post("/api/experimentos/analise")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"predictedNumbers":[1,2,3],"actualContestNumber":3802}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verifyNoInteractions(comparePredictionService, feedbackPromptService, aiFeedbackGenerator);
    }

    @Test
    void rejectsRequestWithNullContestNumber() throws Exception {
        mockMvc.perform(post("/api/experimentos/analise")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"predictedNumbers":[1,2,3,4,5,6,7,8,9,10,11,12,13,14,15],"actualContestNumber":null}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verifyNoInteractions(comparePredictionService, feedbackPromptService, aiFeedbackGenerator);
    }

    @Test
    void mapsAiProviderFailureToBadGateway() throws Exception {
        PredictionComparison comparison = new PredictionComparison(
                numbers(1, 15), numbers(5, 19), 11,
                numbers(5, 15), numbers(1, 4), numbers(16, 19));
        when(comparePredictionService.compare(anyList(), eq(3802))).thenReturn(comparison);
        when(feedbackPromptService.createPrompt(comparison)).thenReturn("Prompt de análise pronto.");
        when(aiFeedbackGenerator.isAvailable()).thenReturn(true);
        when(aiFeedbackGenerator.generateFeedback("Prompt de análise pronto."))
                .thenThrow(new AiIntegrationException("provider failure"));

        mockMvc.perform(post("/api/experimentos/analise")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"predictedNumbers":[1,2,3,4,5,6,7,8,9,10,11,12,13,14,15],"actualContestNumber":3802}
                                """))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("AI_SERVICE_UNAVAILABLE"));
    }

    @Test
    void mapsGeminiTemporaryUnavailabilityToServiceUnavailable() throws Exception {
        PredictionComparison comparison = new PredictionComparison(
                numbers(1, 15), numbers(5, 19), 11, numbers(5, 15), numbers(1, 4), numbers(16, 19));
        when(comparePredictionService.compare(anyList(), eq(3802))).thenReturn(comparison);
        when(feedbackPromptService.createPrompt(comparison)).thenReturn("Prompt de análise pronto.");
        when(aiFeedbackGenerator.isAvailable()).thenReturn(true);
        when(aiFeedbackGenerator.generateFeedback("Prompt de análise pronto."))
                .thenThrow(AiIntegrationException.temporarilyUnavailable(new RuntimeException("provider 503")));

        mockMvc.perform(post("/api/experimentos/analise").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"predictedNumbers\":[1,2,3,4,5,6,7,8,9,10,11,12,13,14,15],\"actualContestNumber\":3802}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AI_SERVICE_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("O Gemini está temporariamente indisponível. Tente novamente em instantes."));
    }
    @Test
    void returnsPromptWithoutAiWhenProviderIsNotConfigured() throws Exception {
        PredictionComparison comparison = new PredictionComparison(
                numbers(1, 15), numbers(5, 19), 11, numbers(5, 15), numbers(1, 4), numbers(16, 19));
        when(comparePredictionService.compare(anyList(), eq(3802))).thenReturn(comparison);
        when(feedbackPromptService.createPrompt(comparison)).thenReturn("Prompt disponível para uso manual.");
        when(aiFeedbackGenerator.isAvailable()).thenReturn(false);

        mockMvc.perform(post("/api/experimentos/analise").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"predictedNumbers\":[1,2,3,4,5,6,7,8,9,10,11,12,13,14,15],\"actualContestNumber\":3802}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.promptText").value("Prompt disponível para uso manual."))
                .andExpect(jsonPath("$.aiFeedback").value(""));

        org.mockito.Mockito.verify(aiFeedbackGenerator, org.mockito.Mockito.never()).generateFeedback(org.mockito.ArgumentMatchers.anyString());
    }


    @Test
    void mapsGeminiQuotaToServiceUnavailable() throws Exception {
        PredictionComparison comparison = new PredictionComparison(
                numbers(1, 15), numbers(5, 19), 11, numbers(5, 15), numbers(1, 4), numbers(16, 19));
        when(comparePredictionService.compare(anyList(), eq(3802))).thenReturn(comparison);
        when(feedbackPromptService.createPrompt(comparison)).thenReturn("Prompt de análise pronto.");
        when(aiFeedbackGenerator.isAvailable()).thenReturn(true);
        when(aiFeedbackGenerator.generateFeedback("Prompt de análise pronto."))
                .thenThrow(AiIntegrationException.rateLimited(new RuntimeException("provider quota")));

        mockMvc.perform(post("/api/experimentos/analise").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"predictedNumbers\":[1,2,3,4,5,6,7,8,9,10,11,12,13,14,15],\"actualContestNumber\":3802}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AI_SERVICE_UNAVAILABLE"));
    }

    private static List<Integer> numbers(int first, int last) {
        return IntStream.rangeClosed(first, last).boxed().toList();
    }
}
