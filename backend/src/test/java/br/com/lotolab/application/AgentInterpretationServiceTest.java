package br.com.lotolab.application;

import br.com.lotolab.application.port.AiFeedbackGenerator;
import br.com.lotolab.application.port.AgentInterpretationRepository;
import br.com.lotolab.application.port.AgentJob;
import br.com.lotolab.application.port.PatternInsightRepository;
import br.com.lotolab.domain.AgentInterpretation;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

class AgentInterpretationServiceTest {
    private final AiFeedbackGenerator ai = mock(AiFeedbackGenerator.class);
    private final PatternInsightRepository insights = mock(PatternInsightRepository.class);
    private final AgentInterpretationRepository interpretations = mock(AgentInterpretationRepository.class);
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void recordsDisabledWithoutCallingGemini() {
        when(interpretations.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var service = new AgentInterpretationService(ai, insights, interpretations, json, false);

        AgentInterpretation result = service.interpretPatternRun(55);

        assertThat(result.status()).isEqualTo("SKIPPED_DISABLED");
        assertThat(result.agentRunId()).isEqualTo(55L);
        verify(ai, never()).generateFeedback(any());
    }

    @Test
    void acceptsOnlyBoundedStructuredNarrativeAndPersistsHypotheses() {
        when(ai.isAvailable()).thenReturn(true);
        when(ai.modelName()).thenReturn("gemini-test");
        when(ai.generateFeedback(any())).thenReturn("""
                {"summary":"As métricas mostram variação descritiva entre as janelas observadas.",
                 "hypotheses":["Testar se a estabilidade se mantém em amostras posteriores."],
                 "explanation":"O histórico não estabelece vantagem em sorteios futuros."}
                """);
        when(interpretations.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var service = new AgentInterpretationService(ai, insights, interpretations, json, true);

        AgentInterpretation result = service.interpretPatternRun(56);

        assertThat(result.status()).isEqualTo("SUCCEEDED");
        assertThat(result.hypotheses()).containsExactly("Testar se a estabilidade se mantém em amostras posteriores.");
        assertThat(result.statisticalInputJson()).contains("PATTERN_RESEARCHER");
        verify(ai).generateFeedback(org.mockito.ArgumentMatchers.contains("Do not calculate"));
    }

    @Test
    void rejectsModelResponseThatIntroducesNumbersOrProbabilityClaims() {
        when(ai.isAvailable()).thenReturn(true);
        when(ai.modelName()).thenReturn("gemini-test");
        when(ai.generateFeedback(any())).thenReturn("""
                {"summary":"A dezena 7 aparece muito e tem maior chance.",
                 "hypotheses":[], "explanation":"Não há evidência suficiente para afirmar probabilidade."}
                """);
        when(interpretations.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var service = new AgentInterpretationService(ai, insights, interpretations, json, true);

        AgentInterpretation result = service.interpretPatternRun(57);

        assertThat(result.status()).isEqualTo("INVALID_RESPONSE");
        assertThat(result.summary()).isNull();
        assertThat(result.errorCategory()).isEqualTo("SCHEMA_OR_LENGTH");
    }

    @Test
    void workerMarksQueuedJobSkippedWhenGeminiIsDisabledWithoutCallingGateway() {
        var queued = new AgentInterpretation(12, null, 31L, "GAME_EVALUATOR", "GOOGLE_GEMINI",
                "gemini-test", "v1", "PENDING", null, java.util.List.of(), null, "{}", null,
                java.time.Instant.parse("2026-01-01T00:00:00Z"));
        var job = new AgentJob(51, 12, 0, 4, "RUNNING", "TEST", "trace-test", "run-test", queued);
        when(interpretations.claimNext(anyString(), any())).thenReturn(java.util.Optional.of(job));
        when(interpretations.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var service = new AgentInterpretationService(ai, insights, interpretations, json, false);

        service.processNextJob("test-worker");

        org.mockito.ArgumentCaptor<AgentInterpretation> saved = org.mockito.ArgumentCaptor.forClass(AgentInterpretation.class);
        verify(interpretations, times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(AgentInterpretation::status)
                .containsExactly("RUNNING", "SKIPPED_DISABLED");
        verify(interpretations).finishJob(51, "SKIPPED", "GEMINI_DISABLED", null);
        verify(ai, never()).generateFeedback(any());
    }
}
