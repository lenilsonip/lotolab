package br.com.lotolab.api;

import br.com.lotolab.contests.adapter.in.web.ContestController;
import br.com.lotolab.contests.application.ContestNotFoundException;
import br.com.lotolab.contests.application.ContestPage;
import br.com.lotolab.contests.application.port.in.ContestRegistrationResult;
import br.com.lotolab.contests.application.port.in.GetContestUseCase;
import br.com.lotolab.contests.application.port.in.GetLatestContestUseCase;
import br.com.lotolab.contests.application.port.in.ListContestsUseCase;
import br.com.lotolab.contests.application.port.in.RegisterContestCommand;
import br.com.lotolab.contests.application.port.in.RegisterContestUseCase;
import br.com.lotolab.domain.Contest;
import br.com.lotolab.domain.ContestConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Pins the existing public HTTP contract before controller/package refactoring. */
@WebMvcTest(ContestController.class)
@Import(ApiExceptionHandler.class)
class ContestApiContractTest {
    @Autowired MockMvc mockMvc;

    @MockitoBean RegisterContestUseCase registerContestService;
    @MockitoBean GetContestUseCase getContestService;
    @MockitoBean ListContestsUseCase listContestsService;
    @MockitoBean GetLatestContestUseCase getLatestContestService;

    @Test
    void createsContestAndPreservesResponseShape() throws Exception {
        when(registerContestService.register(any(RegisterContestCommand.class)))
                .thenReturn(new ContestRegistrationResult(contest(), true, false));

        mockMvc.perform(post("/api/concursos").contentType("application/json").content(requestBody()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/concursos/64001"))
                .andExpect(jsonPath("$.number").value(64001))
                .andExpect(jsonPath("$.drawDate").value("2026-11-12"))
                .andExpect(jsonPath("$.drawnNumbers[0]").value(1))
                .andExpect(jsonPath("$.drawnNumbers.length()").value(15));
    }

    @Test
    void returnsExistingContractStatusesForIdempotentInvalidMissingAndConflictingData() throws Exception {
        when(registerContestService.register(any(RegisterContestCommand.class)))
                .thenReturn(new ContestRegistrationResult(contest(), false, false));
        mockMvc.perform(post("/api/concursos").contentType("application/json").content(requestBody()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/concursos").contentType("application/json")
                        .content("{\"number\":64001,\"drawDate\":\"2026-11-12\",\"drawnNumbers\":[1,2]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        when(registerContestService.register(any(RegisterContestCommand.class)))
                .thenThrow(new ContestConflictException(64001));
        mockMvc.perform(post("/api/concursos").contentType("application/json").content(requestBody()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONTEST_CONFLICT"));

        when(getContestService.get(64001)).thenThrow(new ContestNotFoundException(64001));
        mockMvc.perform(get("/api/concursos/64001"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CONTEST_NOT_FOUND"));
    }

    @Test
    void preservesLatestAndPageJsonContracts() throws Exception {
        Contest contest = contest();
        when(getLatestContestService.get()).thenReturn(contest);
        when(listContestsService.list(0, 20)).thenReturn(new ContestPage(List.of(contest), 0, 20, 1, 1));

        mockMvc.perform(get("/api/concursos/ultimo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value(64001));
        mockMvc.perform(get("/api/concursos").param("page", "0").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].number").value(64001))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    private static String requestBody() {
        return "{\"number\":64001,\"drawDate\":\"2026-11-12\",\"drawnNumbers\":[15,14,13,12,11,10,9,8,7,6,5,4,3,2,1]}";
    }

    private static Contest contest() {
        return new Contest(64001, LocalDate.of(2026, 11, 12),
                java.util.stream.IntStream.rangeClosed(1, 15).boxed().toList());
    }
}
