package br.com.lotolab.application;

import br.com.lotolab.contests.application.ContestNotFoundException;
import br.com.lotolab.contests.application.ContestPage;
import br.com.lotolab.contests.application.ContestImportReport;
import br.com.lotolab.contests.application.ContestResultPersistedEvent;
import br.com.lotolab.contests.application.port.in.ContestRegistrationResult;
import br.com.lotolab.contests.application.port.in.PersistContestUseCase;
import br.com.lotolab.contests.application.port.in.RegisterContestCommand;
import br.com.lotolab.contests.application.port.out.ContestRepository;
import br.com.lotolab.contests.application.port.out.ContestResearchTrigger;
import br.com.lotolab.contests.application.port.out.ContestResultEventPublisher;
import br.com.lotolab.contests.application.port.out.ContestUnitOfWork;
import br.com.lotolab.contests.application.port.out.PendingPredictionEvaluator;
import br.com.lotolab.contests.application.service.GetContestHandler;
import br.com.lotolab.contests.application.service.GetLatestContestHandler;
import br.com.lotolab.contests.application.service.ListContestsHandler;
import br.com.lotolab.contests.application.service.PersistContestService;
import br.com.lotolab.contests.application.service.RegisterContestHandler;
import br.com.lotolab.application.importing.HistoricalContestImportService;
import br.com.lotolab.application.importing.ParsedContestFile;
import br.com.lotolab.application.importing.ParsedContestRow;
import br.com.lotolab.domain.Contest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContestUseCasesTest {
    @Mock private ContestRepository repository;
    @Mock private ContestUnitOfWork unitOfWork;
    @Mock private PendingPredictionEvaluator evaluator;
    @Mock private ContestResultEventPublisher events;
    @Mock private ContestResearchTrigger research;
    @Mock private PersistContestUseCase persistence;

    @Test
    void persistenceRunsRepositoryEvaluationAndEventInsideOneUnitOfWork() {
        var service = persistenceService();
        var contest = contest(3802);
        when(unitOfWork.execute(any())).thenAnswer(invocation ->
                ((Supplier<Boolean>) invocation.getArgument(0)).get());
        when(repository.saveIfAbsent(contest)).thenReturn(true);

        assertThat(service.persist(contest)).isTrue();

        InOrder order = inOrder(unitOfWork, repository, evaluator, events);
        order.verify(unitOfWork).execute(any());
        order.verify(repository).saveIfAbsent(contest);
        order.verify(evaluator).evaluateForContest(3802);
        order.verify(events).publish(new ContestResultPersistedEvent(3802));
    }

    @Test
    void identicalContestDoesNotEvaluateOrPublishEvent() {
        var service = persistenceService();
        var contest = contest(3802);
        when(unitOfWork.execute(any())).thenAnswer(invocation ->
                ((Supplier<Boolean>) invocation.getArgument(0)).get());
        when(repository.saveIfAbsent(contest)).thenReturn(false);

        assertThat(service.persist(contest)).isFalse();
        verify(evaluator, never()).evaluateForContest(anyInt());
        verify(events, never()).publish(any());
    }

    @Test
    void retriesResearchAfterFailedPostCommitAttemptWhenRegistrationIsIdempotent() {
        var handler = new RegisterContestHandler(persistence, research);
        var command = new RegisterContestCommand(3802, LocalDate.of(2026, 10, 11), numbers());
        when(persistence.persist(any(Contest.class))).thenReturn(true, false);
        when(research.trigger("CONTEST_REGISTERED")).thenReturn(false, true);

        ContestRegistrationResult first = handler.register(command);
        ContestRegistrationResult retry = handler.register(command);

        assertThat(first.created()).isTrue();
        assertThat(first.researchTriggered()).isFalse();
        assertThat(retry.created()).isFalse();
        assertThat(retry.researchTriggered()).isTrue();
        verify(research, org.mockito.Mockito.times(2)).trigger("CONTEST_REGISTERED");
        verify(persistence, org.mockito.Mockito.times(2)).persist(any(Contest.class));
    }

    @Test
    void queryHandlersKeepNotFoundAndPaginationRulesLocalToTheirUseCases() {
        var contest = contest(3802);
        when(repository.findByNumber(3802)).thenReturn(Optional.of(contest));
        when(repository.findLatest()).thenReturn(Optional.of(contest));
        when(repository.count()).thenReturn(1L);
        when(repository.findPage(0, 20)).thenReturn(List.of(contest));

        assertThat(new GetContestHandler(repository).get(3802)).isEqualTo(contest);
        assertThat(new GetLatestContestHandler(repository).get()).isEqualTo(contest);
        ContestPage page = new ListContestsHandler(repository).list(0, 20);
        assertThat(page.contests()).containsExactly(contest);
        assertThat(page.totalPages()).isEqualTo(1);

        when(repository.findByNumber(1)).thenReturn(Optional.empty());
        when(repository.findLatest()).thenReturn(Optional.empty());
        assertThatThrownBy(() -> new GetContestHandler(repository).get(1))
                .isInstanceOf(ContestNotFoundException.class);
        assertThatThrownBy(() -> new GetLatestContestHandler(repository).get())
                .isInstanceOf(ContestNotFoundException.class);
        assertThatThrownBy(() -> new ListContestsHandler(repository).list(0, 101))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void historicalImportDoesNotTriggerResearchWhenPersistenceAbortsAfterEarlierInsertions() throws Exception {
        var first = contest(41001);
        var second = contest(41002);
        var reader = org.mockito.Mockito.mock(br.com.lotolab.application.importing.ContestFileReader.class);
        var service = new HistoricalContestImportService(reader, repository, persistence, research);
        when(reader.read(any(Path.class))).thenReturn(new ParsedContestFile(2, true,
                List.of(new ParsedContestRow(2, first), new ParsedContestRow(3, second)), List.of(), List.of()));
        when(persistence.persist(first)).thenReturn(true);
        when(persistence.persist(second)).thenThrow(new IllegalStateException("synthetic persistence failure"));

        ContestImportReport report = service.importFile(Path.of("synthetic.csv"));

        assertThat(report.persistenceAborted()).isTrue();
        assertThat(report.newContests()).isEqualTo(1);
        assertThat(report.notProcessedLines()).isZero();
        verify(research, never()).trigger(any());
    }

    @Test
    void historicalImportTriggersResearchOnceAfterCompletedBatchWithNewContest() throws Exception {
        var contest = contest(41003);
        var reader = org.mockito.Mockito.mock(br.com.lotolab.application.importing.ContestFileReader.class);
        var service = new HistoricalContestImportService(reader, repository, persistence, research);
        when(reader.read(any(Path.class))).thenReturn(new ParsedContestFile(1, true,
                List.of(new ParsedContestRow(2, contest)), List.of(), List.of()));
        when(persistence.persist(contest)).thenReturn(true);
        when(research.trigger("IMPORT")).thenReturn(true);

        ContestImportReport report = service.importFile(Path.of("synthetic.csv"));

        assertThat(report.persistenceAborted()).isFalse();
        assertThat(report.newContests()).isEqualTo(1);
        verify(research).trigger("IMPORT");
    }

    private PersistContestService persistenceService() {
        return new PersistContestService(repository, evaluator, events, unitOfWork);
    }

    private static Contest contest(int number) {
        return new Contest(number, LocalDate.of(2026, 10, 11), numbers());
    }

    private static List<Integer> numbers() { return IntStream.rangeClosed(1, 15).boxed().toList(); }
}
