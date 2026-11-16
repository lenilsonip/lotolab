package br.com.lotolab.application;

import br.com.lotolab.domain.DrawnNumberFrequency;
import br.com.lotolab.domain.FrequencySnapshot;
import br.com.lotolab.application.port.HistoricalFrequencyRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CalculateDrawnNumberFrequencyServiceTest {

    @Test
    void calculatesPercentagesSortsTiesAndRetainsZeroFrequencies() {
        List<DrawnNumberFrequency> counts = new ArrayList<>();
        IntStream.rangeClosed(1, 25).forEach(number -> counts.add(new DrawnNumberFrequency(
                number, number <= 10 ? 2 : number <= 20 ? 1 : 0)));
        CalculateDrawnNumberFrequencyService service = new CalculateDrawnNumberFrequencyService(
                repositoryWith(new FrequencySnapshot(2, counts)));

        DrawnNumberFrequencyAnalysis result = service.calculate();

        assertThat(result.totalContests()).isEqualTo(2);
        assertThat(result.frequencies()).hasSize(25);
        assertThat(result.frequencies().get(0).number()).isEqualTo(1);
        assertThat(result.frequencies().get(0).percentage()).hasToString("100.0000");
        assertThat(result.frequencies().subList(0, 10))
                .extracting(DrawnNumberFrequencyAnalysis.Frequency::number)
                .containsExactlyElementsOf(IntStream.rangeClosed(1, 10).boxed().toList());
        assertThat(result.frequencies().get(10).number()).isEqualTo(11);
        assertThat(result.frequencies().get(10).percentage()).hasToString("50.0000");
        assertThat(result.frequencies().get(20).number()).isEqualTo(21);
        assertThat(result.frequencies().get(20).occurrences()).isZero();
        assertThat(result.frequencies().get(20).percentage()).hasToString("0.0000");
        assertThat(result.frequencies().stream().mapToLong(DrawnNumberFrequencyAnalysis.Frequency::occurrences).sum())
                .isEqualTo(15L * result.totalContests());
    }

    @Test
    void rejectsFrequencySumThatDoesNotMatchFifteenDrawsPerContest() {
        List<DrawnNumberFrequency> counts = IntStream.rangeClosed(1, 25)
                .mapToObj(number -> new DrawnNumberFrequency(number, number <= 14 ? 1 : 0))
                .toList();
        CalculateDrawnNumberFrequencyService service = new CalculateDrawnNumberFrequencyService(
                repositoryWith(new FrequencySnapshot(1, counts)));

        assertThatThrownBy(service::calculate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("15 vezes");
    }

    private static HistoricalFrequencyRepository repositoryWith(FrequencySnapshot snapshot) {
        return new HistoricalFrequencyRepository() {
            @Override
            public FrequencySnapshot calculateDrawnNumberFrequencies() {
                return snapshot;
            }

            @Override
            public br.com.lotolab.domain.RecentFrequencySnapshot calculateRecentDrawnNumberFrequencies(int limit) {
                throw new UnsupportedOperationException("This unit test only exercises full history");
            }
        };
    }
}
