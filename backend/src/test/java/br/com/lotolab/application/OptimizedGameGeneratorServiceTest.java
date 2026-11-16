package br.com.lotolab.application;

import br.com.lotolab.application.port.ContestRepository;
import br.com.lotolab.application.port.PatternInsightRepository;
import br.com.lotolab.domain.Contest;
import br.com.lotolab.domain.GameGenerationCriteria;
import br.com.lotolab.domain.LotofacilGame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.LocalDate;
import java.util.List;
import java.math.BigDecimal;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OptimizedGameGeneratorServiceTest {
    @Test
    void newFrequencyAndCoOccurrenceEvidenceChangeCandidateFitnessPenalty() {
        var service = new OptimizedGameGeneratorService(mock(GameBacktestingService.class), 1, null, null);
        var evidence = List.of(
                new br.com.lotolab.domain.PatternInsight(1L, 1L, "PAIR:01-02", "walk-forward-2",
                        "CO_OCCURRENCE_PAIR", "pair", BigDecimal.ZERO, 1, 10, 11, 12, 2,
                        BigDecimal.ZERO, BigDecimal.ONE, "EXPERIMENTAL", "{\"members\":[1,2],\"predictedValue\":0.9}"),
                new br.com.lotolab.domain.PatternInsight(2L, 1L, "NUMBER_FREQUENCY:n=25", "walk-forward-2",
                        "NUMBER_FREQUENCY", "frequency", BigDecimal.ZERO, 1, 10, 11, 12, 2,
                        BigDecimal.ZERO, BigDecimal.ONE, "EXPERIMENTAL", "{\"number\":25,\"windowSize\":50,\"predictedValue\":0.8}"));
        var containingNumbers = new java.util.ArrayList<>(IntStream.rangeClosed(1, 14).boxed().toList());
        containingNumbers.add(25);
        var containingEvidence = new LotofacilGame(containingNumbers);
        var excludingEvidence = new LotofacilGame(IntStream.rangeClosed(3, 17).boxed().toList());

        assertThat(service.evidencePenalty(containingEvidence, evidence))
                .isLessThan(service.evidencePenalty(excludingEvidence, evidence));
    }

    @Test
    void mutatesToUniqueCandidatesPreservesParityAndIsReproducibleFromSeed() {
        ContestRepository repository = mock(ContestRepository.class);
        when(repository.findAll()).thenReturn(List.of(contest(1, 15), contest(2, 13), contest(3, 12),
                contest(4, 11), contest(5, 10)));
        GameBacktestingService backtesting = spy(new GameBacktestingService(repository));
        OptimizedGameGeneratorService service = new OptimizedGameGeneratorService(backtesting);
        GameGenerationCriteria criteria = new GameGenerationCriteria(1, 8, 7, 1234L);

        var first = service.generate(criteria);
        var second = service.generate(criteria);

        assertThat(first).isEqualTo(second);
        assertThat(first.game().numbers()).hasSize(15).doesNotHaveDuplicates()
                .allMatch(number -> number >= 1 && number <= 25);
        assertThat(first.game().numbers().stream().filter(number -> number % 2 == 0)).hasSize(8);
        assertThat(first.game().numbers().stream().filter(number -> number % 2 != 0)).hasSize(7);
        assertThat(first.iterations()).isEqualTo(OptimizedGameGeneratorService.DEFAULT_ITERATIONS);
        assertThat(first.trainingContestsEvaluated()).isEqualTo(4);
        assertThat(first.contestsEvaluated()).isEqualTo(1);
        assertThat(first.fitnessScore()).isEqualTo(backtesting.evaluateValidation(first.game(), backtesting.loadHistory()).fitnessScore());
        verify(repository, org.mockito.Mockito.times(5)).findAll();
    }

    @Test
    void doesNotEvaluateDuplicateCombinationsAndEveryCandidateChangesTheCurrentChampion() {
        ContestRepository repository = mock(ContestRepository.class);
        when(repository.findAll()).thenReturn(List.of());
        GameBacktestingService backtesting = spy(new GameBacktestingService(repository));
        var result = new OptimizedGameGeneratorService(backtesting)
                .generate(new GameGenerationCriteria(1, null, null, 991L));

        ArgumentCaptor<LotofacilGame> games = ArgumentCaptor.forClass(LotofacilGame.class);
        verify(backtesting, org.mockito.Mockito.atLeast(result.iterations() + 1
                        + OptimizedGameGeneratorService.DEFAULT_ITERATIONS + 1))
                .evaluateTraining(games.capture(), org.mockito.ArgumentMatchers.any());
        assertThat(games.getAllValues().subList(0, result.iterations() + 1)).doesNotHaveDuplicates();
        assertThat(result.duplicateMutationsSkipped()).isGreaterThanOrEqualTo(0);
        assertThat(result.searchExhausted()).isFalse();
    }

    @Test
    void handlesEmptyHistoryAndSaturatedParityWithoutInvalidMutations() {
        ContestRepository repository = mock(ContestRepository.class);
        when(repository.findAll()).thenReturn(List.of());
        var result = new OptimizedGameGeneratorService(new GameBacktestingService(repository))
                .generate(new GameGenerationCriteria(1, 12, 3, 23L));

        assertThat(result.game().numbers()).hasSize(15).doesNotHaveDuplicates();
        assertThat(result.fitnessScore()).isZero();
        assertThat(result.contestsEvaluated()).isZero();
        assertThat(result.game().numbers().stream().filter(number -> number % 2 == 0)).hasSize(12);
        assertThat(result.game().numbers().stream().filter(number -> number % 2 != 0)).hasSize(3);
    }

    @Test
    void stopsAfterTheFiniteFilteredCombinationSpaceIsExhausted() {
        ContestRepository repository = mock(ContestRepository.class);
        when(repository.findAll()).thenReturn(List.of());
        var service = new OptimizedGameGeneratorService(new GameBacktestingService(repository), 10_000);

        var result = service.generate(new GameGenerationCriteria(1, 12, 3, 23L));

        assertThat(result.searchExhausted()).isTrue();
        assertThat(result.iterations()).isLessThan(10_000);
        assertThat(result.duplicateMutationsSkipped()).isPositive();
        assertThat(result.iterations()).isLessThanOrEqualTo(285);
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void consumesOnlyPreviouslyValidatedAgentEvidenceForTheTargetAndReturnsItsProvenance(CapturedOutput output) {
        ContestRepository contests = mock(ContestRepository.class);
        PatternInsightRepository insights = mock(PatternInsightRepository.class);
        when(contests.findAll()).thenReturn(List.of(contest(1, 15), contest(2, 13), contest(3, 12),
                contest(4, 11), contest(5, 10)));
        when(contests.findLatest()).thenReturn(java.util.Optional.of(contest(5, 10)));
        when(insights.findEligible(5)).thenReturn(List.of(new br.com.lotolab.domain.PatternInsight(
                77L, 8L, "ODD_COUNT:fold-1", "walk-forward-1", "ODD_COUNT",
                "validado anteriormente", BigDecimal.ZERO, 1, 2, 3, 3, 1,
                BigDecimal.ONE, BigDecimal.TEN, "EXPERIMENTAL", "{\"predictedValue\":7.0}")));
        var service = new OptimizedGameGeneratorService(new GameBacktestingService(contests), 30, insights, contests);

        var recommendation = service.generateForTarget(new GameGenerationCriteria(1, 8, 7, 123L), 100);

        assertThat(recommendation.evidenceIds()).containsExactly(77L);
        assertThat(recommendation.evidenceStatus()).isEqualTo("EVIDENCE_USED");
        assertThat(recommendation.sourceCutoffContest()).isEqualTo(5);
        assertThat(output.getOut()).contains("knownHistoryThroughContest=5", "candidateTrainingLastContest=4",
                "evidenceCutoffExclusive=5");
        assertThat(recommendation.folds()).isNotEmpty();
        assertThat(recommendation.folds()).allSatisfy(fold ->
                assertThat(fold.trainingLastContest()).isLessThan(fold.validationFirstContest()));
        verify(insights, org.mockito.Mockito.times(2)).findEligible(5); verify(insights, org.mockito.Mockito.never()).findEligible(100);
    }

    private static Contest contest(int number, int hits) {
        List<Integer> drawn = IntStream.rangeClosed(1, hits).boxed()
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        for (int value = 16; drawn.size() < 15; value++) drawn.add(value);
        return new Contest(number, LocalDate.of(2024, 1, 1).plusDays(number), drawn);
    }
}
