package br.com.lotolab.application;

import br.com.lotolab.application.port.LearningPolicyRepository;
import br.com.lotolab.domain.LearningModel;
import br.com.lotolab.domain.PredictionOutcome;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LearningPolicyServiceTest {
    private final LearningPolicyRepository repository = mock(LearningPolicyRepository.class);

    @Test
    void keepsUniformChampionEvenWhenSingleArmConfidenceBoundLooksHigh() {
        var champion = new LearningModel(1, 1, "CHAMPION", "UNIFORM", 0, 9, 1.5, null, null);
        var candidate = new AtomicReference<LearningModel>();
        when(repository.champion()).thenReturn(champion);
        when(repository.candidate()).thenAnswer(call -> candidate.get());
        when(repository.findUnlearnedOutcomes(5000)).thenReturn(java.util.stream.IntStream.range(0, 100)
                .mapToObj(i -> new PredictionOutcome(i + 1, 5000, 15, 1)).toList());
        when(repository.createVersion(anyString(), anyString(), anyInt(), anyDouble(), anyDouble(),
                any(), any(), anyString())).thenAnswer(call -> {
            int version = call.getArgument(2);
            LearningModel next = new LearningModel(version + 10L, version, call.getArgument(0), call.getArgument(1),
                    call.getArgument(2), call.getArgument(3), call.getArgument(4), call.getArgument(5), call.getArgument(6));
            candidate.set("CANDIDATE".equals(next.status()) ? next : null);
            return next;
        });

        int updated = new LearningPolicyService(repository).observeContest(5000);

        assertThat(updated).isEqualTo(100);
        verify(repository, never()).changeStatus(1, "REJECTED");
        verify(repository).createVersion(eq("CANDIDATE"), eq("MUTATION_OPTIMIZER"), eq(100),
                doubleThat(mean -> mean > 14.9), doubleThat(variance -> variance >= 0), any(), eq(5000),
                eq("AUTOMATIC_PROMOTION_DISABLED_REQUIRES_PAIRED_WALK_FORWARD_REVIEW"));
        verify(repository, times(100)).record(anyLong(), eq(5000), eq(1L), any(), anyInt(), anyInt(),
                anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyString(), anyString());
    }

    @Test
    void keepsUniformChampionWhenObservedResultsMatchNullMean() {
        var champion = new LearningModel(1, 1, "CHAMPION", "UNIFORM", 0, 9, 1.5, null, null);
        var candidate = new AtomicReference<LearningModel>();
        when(repository.champion()).thenReturn(champion);
        when(repository.candidate()).thenAnswer(call -> candidate.get());
        when(repository.findUnlearnedOutcomes(5001)).thenReturn(java.util.stream.IntStream.range(0, 100)
                .mapToObj(i -> new PredictionOutcome(i + 1000, 5001, 9, 1)).toList());
        when(repository.createVersion(anyString(), anyString(), anyInt(), anyDouble(), anyDouble(),
                any(), any(), anyString())).thenAnswer(call -> {
            int version = call.getArgument(2);
            LearningModel next = new LearningModel(version + 10L, version, call.getArgument(0), call.getArgument(1),
                    call.getArgument(2), call.getArgument(3), call.getArgument(4), call.getArgument(5), call.getArgument(6));
            candidate.set(next);
            return next;
        });

        new LearningPolicyService(repository).observeContest(5001);

        verify(repository, never()).changeStatus(1, "REJECTED");
        verify(repository).createVersion(eq("CANDIDATE"), eq("MUTATION_OPTIMIZER"), eq(100),
                eq(9.0), anyDouble(), any(), eq(5001), eq("AUTOMATIC_PROMOTION_DISABLED_REQUIRES_PAIRED_WALK_FORWARD_REVIEW"));
    }
}
