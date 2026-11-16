package br.com.lotolab.research.application;

import br.com.lotolab.domain.Contest;
import br.com.lotolab.domain.PatternInsight;
import br.com.lotolab.research.application.port.in.MinePatternsCommand;
import br.com.lotolab.research.application.port.in.PatternMiningResult;
import br.com.lotolab.research.application.port.out.PatternMiningCompletionPublisher;
import br.com.lotolab.research.application.port.out.PatternResearchRepository;
import br.com.lotolab.research.application.port.out.PatternResearchUnitOfWork;
import br.com.lotolab.research.application.port.out.ResearchContestHistory;
import br.com.lotolab.research.application.service.RunPatternMiningService;
import br.com.lotolab.research.domain.PatternMiningAlgorithm;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.LocalDate;
import java.util.List;
import java.util.OptionalLong;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RunPatternMiningServiceTest {
    private final ResearchContestHistory history = mock(ResearchContestHistory.class);
    private final PatternResearchRepository repository = mock(PatternResearchRepository.class);
    private final PatternResearchUnitOfWork unitOfWork = mock(PatternResearchUnitOfWork.class);
    private final PatternMiningCompletionPublisher events = mock(PatternMiningCompletionPublisher.class);

    @Test
    void persistsAllDeterministicFoldsAndPublishesOnlyAfterRunCompletion() {
        when(history.loadAll()).thenReturn(List.of(contest(6, 7), contest(2, 7), contest(4, 8),
                contest(1, 9), contest(5, 6), contest(3, 5)));
        when(repository.startRun("PATTERN_RESEARCHER", "walk-forward-2", "pattern-miner:walk-forward-2:1:6", 1, 6))
                .thenReturn(OptionalLong.of(42));
        runInUnitOfWork();

        PatternMiningResult result = service().execute(new MinePatternsCommand("MANUAL"));

        assertThat(result.outcome()).isEqualTo(PatternMiningResult.Outcome.COMPLETED);
        assertThat(result.runId()).isEqualTo(42L);
        assertThat(result.insightsWritten()).isEqualTo(216);
        assertThat(result.foldCount()).isEqualTo(3);
        InOrder order = inOrder(repository, events);
        order.verify(repository).completeRun(42);
        order.verify(events).publish(42, "MANUAL");
        verify(repository, times(216)).save(org.mockito.ArgumentMatchers.eq(42L), any(PatternInsight.class));
    }

    @Test
    void skipsAnAlreadyCompletedRangeWithoutPublishingASecondEvent() {
        when(history.loadAll()).thenReturn(IntStream.rangeClosed(1, 3).mapToObj(number -> contest(number, 7)).toList());
        when(repository.startRun(any(), any(), any(), any(), any())).thenReturn(OptionalLong.empty());
        runInUnitOfWork();

        PatternMiningResult result = service().execute(new MinePatternsCommand("SCHEDULED"));

        assertThat(result.outcome()).isEqualTo(PatternMiningResult.Outcome.ALREADY_COMPLETED);
        assertThat(result.insightsWritten()).isZero();
        verify(repository, never()).save(anyLong(), any(PatternInsight.class));
        verify(repository, never()).completeRun(anyLong());
        verify(events, never()).publish(anyLong(), any());
    }

    @Test
    void historyShorterThanThreeDoesNotStartARun() {
        when(history.loadAll()).thenReturn(List.of(contest(1, 7), contest(2, 7)));
        runInUnitOfWork();

        PatternMiningResult result = service().execute(new MinePatternsCommand("MANUAL"));

        assertThat(result.outcome()).isEqualTo(PatternMiningResult.Outcome.INSUFFICIENT_HISTORY);
        verify(repository, never()).startRun(any(), any(), any(), any(), any());
        verify(events, never()).publish(anyLong(), any());
    }

    @Test
    void calculationFailurePropagatesBeforeCompletionEvent() {
        when(history.loadAll()).thenReturn(IntStream.rangeClosed(1, 6).mapToObj(number -> contest(number, 7)).toList());
        when(repository.startRun(any(), any(), any(), any(), any())).thenReturn(OptionalLong.of(7));
        when(unitOfWork.execute(any())).thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(0)).get());
        org.mockito.Mockito.doThrow(new IllegalStateException("synthetic write failure"))
                .when(repository).save(org.mockito.ArgumentMatchers.eq(7L), any(PatternInsight.class));

        assertThatThrownBy(() -> service().execute(new MinePatternsCommand("MANUAL")))
                .isInstanceOf(IllegalStateException.class);
        verify(repository, never()).completeRun(anyLong());
        verify(events, never()).publish(anyLong(), any());
    }

    private void runInUnitOfWork() {
        when(unitOfWork.execute(any())).thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(0)).get());
    }

    private RunPatternMiningService service() {
        return new RunPatternMiningService(history, repository, unitOfWork, events, new PatternMiningAlgorithm());
    }

    private static Contest contest(int number, int oddCount) {
        var values = new java.util.ArrayList<Integer>();
        for (int n = 1; values.size() < oddCount; n += 2) values.add(n);
        for (int n = 2; values.size() < 15; n += 2) values.add(n);
        return new Contest(number, LocalDate.of(2024, 1, number), values);
    }
}
