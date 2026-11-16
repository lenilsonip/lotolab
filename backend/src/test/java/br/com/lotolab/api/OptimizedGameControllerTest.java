package br.com.lotolab.api;

import br.com.lotolab.application.OptimizedGameGeneratorService;
import br.com.lotolab.application.SavePredictionAttemptService;
import br.com.lotolab.application.AgentInterpretationService;
import br.com.lotolab.domain.AgentGameRecommendation;
import br.com.lotolab.domain.AgentInterpretation;
import br.com.lotolab.domain.LotofacilGame;
import br.com.lotolab.domain.OptimizedGameResult;
import br.com.lotolab.domain.PredictionAttempt;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.stream.IntStream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OptimizedGameController.class)
@Import(ApiExceptionHandler.class)
class OptimizedGameControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OptimizedGameGeneratorService service;

    @MockitoBean
    private SavePredictionAttemptService saveAttemptService;

    @MockitoBean
    private AgentInterpretationService interpretationService;

    @Test
    void returnsOptimizedGameAndRetrospectiveMetrics() throws Exception {
        var result = new OptimizedGameResult(new LotofacilGame(IntStream.rangeClosed(1, 15).boxed().toList()),
                1234, 760, 500, 4321, 3042, 3, false);
        when(service.generateForTarget(any(), org.mockito.ArgumentMatchers.isNull())).thenReturn(
                new AgentGameRecommendation(1, result, 0, "NO_ELIGIBLE_EVIDENCE", java.util.List.of(),
                        "mutation-1", 1200, 760, 44, 501, "NO_DEMONSTRATED_ADVANTAGE", 1000, 1200,
                        java.util.List.of()));
        when(saveAttemptService.saveGeneratedAndQueue(any(), org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(
                new SavePredictionAttemptService.GeneratedPrediction(
                        new PredictionAttempt(33L, 1, java.time.LocalDateTime.parse("2026-11-01T12:00:00"),
                                IntStream.rangeClosed(1, 15).boxed().toList()), 44L));
        when(interpretationService.predictionInterpretation(33L)).thenReturn(java.util.Optional.of(new AgentInterpretation(
                1, null, 33L, "GAME_EVALUATOR", "GOOGLE_GEMINI", "gemini-test", "v1",
                "PENDING", null, java.util.List.of(),
                null, "{}", null, java.time.Instant.now())));

        mockMvc.perform(post("/api/experimentos/gerar-otimizado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"evenNumbers\":8,\"oddNumbers\":7,\"seed\":44}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.game.length()").value(15))
                .andExpect(jsonPath("$.fitnessScore").value(1234))
                .andExpect(jsonPath("$.contestsEvaluated").value(760))
                .andExpect(jsonPath("$.trainingContestsEvaluated").value(3042))
                .andExpect(jsonPath("$.trainingFitnessScore").value(4321))
                .andExpect(jsonPath("$.duplicateMutationsSkipped").value(3))
                .andExpect(jsonPath("$.iterations").value(500))
                .andExpect(jsonPath("$.predictionId").value(33))
                .andExpect(jsonPath("$.targetContestNumber").value(1))
                .andExpect(jsonPath("$.randomBaselineFitnessScore").value(1200))
                .andExpect(jsonPath("$.aiInterpretationStatus").value("PENDING"))
                .andExpect(jsonPath("$.interpretationJobId").value(44))
                .andExpect(jsonPath("$.statisticalNotice").exists());
    }

    @Test
    void rejectsIncompleteParityCriteria() throws Exception {
        mockMvc.perform(post("/api/experimentos/gerar-otimizado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"evenNumbers\":8}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verifyNoInteractions(service);
    }
}
