package br.com.lotolab.application;

import br.com.lotolab.application.port.ContestRepository;
import br.com.lotolab.domain.Contest;
import br.com.lotolab.domain.LotofacilGame;
import br.com.lotolab.domain.GameGenerationCriteria;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GameBacktestingServiceTest {
    @Test
    void sumsTheConfiguredWeightsForHistoricalHitCounts() {
        ContestRepository repository = mock(ContestRepository.class);
        List<Contest> contests = IntStream.rangeClosed(10, 15)
                .mapToObj(hits -> contest(hits, hits))
                .toList();
        when(repository.findAll()).thenReturn(contests);
        GameBacktestingService service = new GameBacktestingService(repository);

        var result = service.evaluate(game());

        assertThat(result.game()).isEqualTo(game());
        assertThat(result.fitnessScore()).isEqualTo(10_531);
        assertThat(result.contestsEvaluated()).isEqualTo(6);
        verify(repository).findAll();
    }

    @Test
    void givesZeroPointsBelowElevenHits() {
        ContestRepository repository = mock(ContestRepository.class);
        when(repository.findAll()).thenReturn(List.of(contest(1, 10)));

        var result = new GameBacktestingService(repository).evaluate(game());

        assertThat(result.fitnessScore()).isZero();
        assertThat(result.contestsEvaluated()).isEqualTo(1);
    }

    @Test
    void separatesTrainingFromLaterContestsInNumberOrder() {
        ContestRepository repository = mock(ContestRepository.class);
        when(repository.findAll()).thenReturn(List.of(contest(5, 15), contest(1, 15), contest(4, 15),
                contest(2, 15), contest(3, 15)));
        GameBacktestingService service = new GameBacktestingService(repository);
        var history = service.loadHistory();

        assertThat(history.trainingContestCount()).isEqualTo(4);
        assertThat(history.validationContestCount()).isEqualTo(1);
        assertThat(service.evaluateTraining(game(), history).contestsEvaluated()).isEqualTo(4);
        assertThat(service.evaluateValidation(game(), history).contestsEvaluated()).isEqualTo(1);
    }

    @Test
    void emptyHistoryHasNoTrainingOrValidationData() {
        ContestRepository repository = mock(ContestRepository.class);
        when(repository.findAll()).thenReturn(List.of());
        var history = new GameBacktestingService(repository).loadHistory();
        assertThat(history.contestCount()).isZero();
        assertThat(history.trainingContestCount()).isZero();
        assertThat(history.validationContestCount()).isZero();
    }

    @Test
    void randomBaselineIsReproducibleAndUsesTheSameParityFiltersAndHoldout() {
        ContestRepository repository = mock(ContestRepository.class);
        when(repository.findAll()).thenReturn(IntStream.rangeClosed(1, 20)
                .mapToObj(number -> contest(number, 11 + number % 5)).toList());
        GameBacktestingService service = new GameBacktestingService(repository);
        var history = service.loadHistory();
        var criteria = new GameGenerationCriteria(1, 8, 7, 123L);

        var first = service.evaluateRandomBaseline(criteria, history, 25, 9382L);
        var second = service.evaluateRandomBaseline(criteria, history, 25, 9382L);

        assertThat(first).isEqualTo(second);
        assertThat(first.game().numbers()).hasSize(15).doesNotHaveDuplicates();
        assertThat(first.game().numbers().stream().filter(number -> number % 2 == 0)).hasSize(8);
        assertThat(first.contestsEvaluated()).isEqualTo(history.validationContestCount());
    }



    private static LotofacilGame game() {
        return new LotofacilGame(IntStream.rangeClosed(1, 15).boxed().toList());
    }

    private static Contest contest(int number, int hits) {
        List<Integer> drawn = IntStream.rangeClosed(1, hits).boxed().collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        for (int value = 16; drawn.size() < 15; value++) drawn.add(value);
        return new Contest(number, LocalDate.of(2024, 1, 1).plusDays(number), drawn);
    }
}
