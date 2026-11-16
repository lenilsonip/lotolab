package br.com.lotolab.api;

import br.com.lotolab.application.SavePredictionAttemptService;
import br.com.lotolab.domain.PredictionAttempt;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PredictionAttemptController.class)
@Import(ApiExceptionHandler.class)
class PredictionAttemptControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private SavePredictionAttemptService service;

    @Test
    void savesValidatedPredictionAndReturnsCreatedResponse() throws Exception {
        List<Integer> numbers = IntStream.rangeClosed(1, 15).boxed().toList();
        when(service.save(3803, numbers)).thenReturn(
                new PredictionAttempt(7L, 3803, LocalDateTime.of(2026, 10, 31, 12, 0), numbers));

        mockMvc.perform(post("/api/previsoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetContestNumber":3803,"predictedNumbers":[1,2,3,4,5,6,7,8,9,10,11,12,13,14,15]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.targetContestNumber").value(3803))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.predictedNumbers.length()").value(15));
    }

    @Test
    void rejectsRequestWithInvalidGameOrMissingTargetContest() throws Exception {
        mockMvc.perform(post("/api/previsoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetContestNumber\":3803,\"predictedNumbers\":[1,2,3]}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/previsoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetContestNumber\":null,\"predictedNumbers\":[1,2,3,4,5,6,7,8,9,10,11,12,13,14,15]}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
