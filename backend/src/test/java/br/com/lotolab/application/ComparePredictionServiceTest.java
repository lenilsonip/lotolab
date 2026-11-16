package br.com.lotolab.application;

import br.com.lotolab.application.port.ContestRepository;
import br.com.lotolab.domain.Contest;
import br.com.lotolab.domain.PredictionComparison;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComparePredictionServiceTest {

    @Mock
    private ContestRepository contestRepository;

    @InjectMocks
    private ComparePredictionService service;

    @Test
    void throwsContestNotFoundWhenContestDoesNotExist() {
        when(contestRepository.findByNumber(9999)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.compare(numbers(1, 15), 9999))
                .isInstanceOf(ContestNotFoundException.class)
                .hasMessageContaining("9999");

        verify(contestRepository).findByNumber(9999);
    }

    @Test
    void calculatesPartialHitsAndSortedDifferences() {
        when(contestRepository.findByNumber(3802)).thenReturn(Optional.of(contest(3802, 5, 19)));

        PredictionComparison comparison = service.compare(numbers(1, 15), 3802);

        assertThat(comparison.hits()).isEqualTo(11);
        assertThat(comparison.matchedNumbers()).containsExactlyElementsOf(numbers(5, 15));
        assertThat(comparison.missedNumbers()).containsExactlyElementsOf(numbers(1, 4));
        assertThat(comparison.unexpectedNumbers()).containsExactlyElementsOf(numbers(16, 19));
        assertThat(comparison.predictedNumbers()).containsExactlyElementsOf(numbers(1, 15));
        assertThat(comparison.actualNumbers()).containsExactlyElementsOf(numbers(5, 19));
    }

    @Test
    void returnsEmptyDifferencesWhenAllFifteenNumbersMatch() {
        when(contestRepository.findByNumber(3802)).thenReturn(Optional.of(contest(3802, 1, 15)));

        PredictionComparison comparison = service.compare(numbers(1, 15).reversed(), 3802);

        assertThat(comparison.hits()).isEqualTo(15);
        assertThat(comparison.matchedNumbers()).containsExactlyElementsOf(numbers(1, 15));
        assertThat(comparison.missedNumbers()).isEmpty();
        assertThat(comparison.unexpectedNumbers()).isEmpty();
        assertThat(comparison.predictedNumbers()).containsExactlyElementsOf(numbers(1, 15));
    }

    private static Contest contest(int number, int firstDrawnNumber, int lastDrawnNumber) {
        return new Contest(number, LocalDate.of(2026, 10, 11), numbers(firstDrawnNumber, lastDrawnNumber));
    }

    private static List<Integer> numbers(int first, int last) {
        return IntStream.rangeClosed(first, last).boxed().toList();
    }
}
