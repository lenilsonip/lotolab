package br.com.lotolab.api;

import br.com.lotolab.contests.adapter.in.web.ContestController;
import br.com.lotolab.contests.application.ContestNotFoundException;
import br.com.lotolab.contests.application.port.in.GetContestUseCase;
import br.com.lotolab.contests.application.port.in.GetLatestContestUseCase;
import br.com.lotolab.contests.application.port.in.ListContestsUseCase;
import br.com.lotolab.contests.application.port.in.RegisterContestUseCase;
import br.com.lotolab.domain.Contest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ContestController.class)
@Import(ApiExceptionHandler.class)
class ContestLatestControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RegisterContestUseCase registerContestService;
    @MockitoBean
    private GetContestUseCase getContestService;
    @MockitoBean
    private ListContestsUseCase listContestsService;
    @MockitoBean
    private GetLatestContestUseCase getLatestContestService;

    @Test
    void returnsLatestContest() throws Exception {
        when(getLatestContestService.get()).thenReturn(
                new Contest(3802, LocalDate.of(2026, 10, 11), numbers(1, 15)));

        mockMvc.perform(get("/api/concursos/ultimo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value(3802))
                .andExpect(jsonPath("$.drawDate").value("2026-10-11"))
                .andExpect(jsonPath("$.drawnNumbers.length()").value(15));
    }

    @Test
    void returnsNotFoundWhenDatabaseIsEmpty() throws Exception {
        when(getLatestContestService.get()).thenThrow(new ContestNotFoundException());

        mockMvc.perform(get("/api/concursos/ultimo"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CONTEST_NOT_FOUND"));
    }

    private static List<Integer> numbers(int first, int last) {
        return IntStream.rangeClosed(first, last).boxed().toList();
    }
}
