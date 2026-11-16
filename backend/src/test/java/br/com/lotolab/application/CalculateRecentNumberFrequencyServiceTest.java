package br.com.lotolab.application;

import br.com.lotolab.application.port.HistoricalFrequencyRepository;
import br.com.lotolab.domain.FrequencySnapshot;
import br.com.lotolab.domain.RecentDrawnNumberFrequency;
import br.com.lotolab.domain.RecentFrequencySnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CalculateRecentNumberFrequencyServiceTest {

    @Test
    void calculatesRecentAndHistoricalRatesAndOrdersTiesByNumber() {
        List<RecentDrawnNumberFrequency> counts = IntStream.rangeClosed(1, 25)
                .mapToObj(number -> new RecentDrawnNumberFrequency(
                        number,
                        number <= 10 ? 2 : number <= 20 ? 1 : 0,
                        number <= 10 ? 3 : number <= 15 ? 1 : number <= 20 ? 2 : 0))
                .toList();
        CalculateRecentNumberFrequencyService service = new CalculateRecentNumberFrequencyService(
                repositoryWith(new RecentFrequencySnapshot(3, 2, counts)));

        RecentNumberFrequencyAnalysis result = service.calculate(100);

        assertThat(result.totalContests()).isEqualTo(3);
        assertThat(result.analyzedRecentContests()).isEqualTo(2);
        assertThat(result.frequencies()).hasSize(25);
        assertThat(result.frequencies().subList(0, 10))
                .extracting(RecentNumberFrequencyAnalysis.Frequency::number)
                .containsExactlyElementsOf(IntStream.rangeClosed(1, 10).boxed().toList());
        assertThat(result.frequencies().get(10).number()).isEqualTo(11);
        assertThat(result.frequencies().get(10).recentPercentage()).hasToString("50.0000");
        assertThat(result.frequencies().get(10).historicalPercentage()).hasToString("33.3333");
        assertThat(result.frequencies().get(10).differencePercentagePoints()).hasToString("16.6667");
        assertThat(result.frequencies().get(20).number()).isEqualTo(21);
        assertThat(result.frequencies().get(20).recentPercentage()).hasToString("0.0000");
        assertThat(result.frequencies().stream().mapToLong(RecentNumberFrequencyAnalysis.Frequency::recentOccurrences).sum())
                .isEqualTo(15L * result.analyzedRecentContests());
        assertThat(result.frequencies().stream().mapToLong(RecentNumberFrequencyAnalysis.Frequency::historicalOccurrences).sum())
                .isEqualTo(15L * result.totalContests());
    }

    @Test
    void rejectsAZeroOrNegativeWindow() {
        CalculateRecentNumberFrequencyService service = new CalculateRecentNumberFrequencyService(repositoryWith(
                new RecentFrequencySnapshot(0, 0, IntStream.rangeClosed(1, 25)
                        .mapToObj(number -> new RecentDrawnNumberFrequency(number, 0, 0)).toList())));

        assertThatThrownBy(() -> service.calculate(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.calculate(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    private static HistoricalFrequencyRepository repositoryWith(RecentFrequencySnapshot recentSnapshot) {
        return new HistoricalFrequencyRepository() {
            @Override
            public FrequencySnapshot calculateDrawnNumberFrequencies() {
                throw new UnsupportedOperationException("This unit test only exercises recent history");
            }

            @Override
            public RecentFrequencySnapshot calculateRecentDrawnNumberFrequencies(int limit) {
                return recentSnapshot;
            }
        };
    }
}
