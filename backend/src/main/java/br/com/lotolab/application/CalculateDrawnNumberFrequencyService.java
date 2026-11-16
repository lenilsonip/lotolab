package br.com.lotolab.application;

import br.com.lotolab.application.port.HistoricalFrequencyRepository;
import br.com.lotolab.domain.DrawnNumberFrequency;
import br.com.lotolab.domain.FrequencySnapshot;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

@Service
public class CalculateDrawnNumberFrequencyService {

    private static final int NUMBER_COUNT = 25;
    private static final int DRAWN_NUMBERS_PER_CONTEST = 15;
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private final HistoricalFrequencyRepository repository;

    public CalculateDrawnNumberFrequencyService(HistoricalFrequencyRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    public DrawnNumberFrequencyAnalysis calculate() {
        FrequencySnapshot snapshot = repository.calculateDrawnNumberFrequencies();
        validateSnapshot(snapshot);

        List<DrawnNumberFrequencyAnalysis.Frequency> frequencies = snapshot.frequencies().stream()
                .sorted(Comparator.comparingLong(DrawnNumberFrequency::occurrences).reversed()
                        .thenComparingInt(DrawnNumberFrequency::number))
                .map(frequency -> new DrawnNumberFrequencyAnalysis.Frequency(
                        frequency.number(), frequency.occurrences(), percentage(frequency.occurrences(), snapshot.totalContests())))
                .toList();

        return new DrawnNumberFrequencyAnalysis(snapshot.totalContests(), frequencies);
    }

    private void validateSnapshot(FrequencySnapshot snapshot) {
        Objects.requireNonNull(snapshot, "Frequency snapshot is required");
        List<DrawnNumberFrequency> frequencies = snapshot.frequencies();
        boolean hasAllNumbersExactlyOnce = frequencies.size() == NUMBER_COUNT
                && IntStream.rangeClosed(1, NUMBER_COUNT).allMatch(number ->
                frequencies.stream().filter(frequency -> frequency.number() == number).count() == 1);
        if (!hasAllNumbersExactlyOnce || frequencies.stream().anyMatch(frequency ->
                frequency.occurrences() < 0 || frequency.occurrences() > snapshot.totalContests())) {
            throw new IllegalStateException("Consulta de frequência retornou dezenas ausentes ou inválidas");
        }

        long frequencySum = frequencies.stream().mapToLong(DrawnNumberFrequency::occurrences).sum();
        if (frequencySum != (long) DRAWN_NUMBERS_PER_CONTEST * snapshot.totalContests()) {
            throw new IllegalStateException("Soma das frequências diverge de 15 vezes o total de concursos");
        }
    }

    private BigDecimal percentage(long occurrences, long totalContests) {
        if (totalContests == 0) {
            return BigDecimal.ZERO.setScale(4);
        }
        return BigDecimal.valueOf(occurrences).multiply(ONE_HUNDRED)
                .divide(BigDecimal.valueOf(totalContests), 4, RoundingMode.HALF_UP);
    }
}
