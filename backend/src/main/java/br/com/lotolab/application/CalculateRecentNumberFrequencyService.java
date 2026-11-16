package br.com.lotolab.application;

import br.com.lotolab.application.port.HistoricalFrequencyRepository;
import br.com.lotolab.domain.RecentDrawnNumberFrequency;
import br.com.lotolab.domain.RecentFrequencySnapshot;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

@Service
public class CalculateRecentNumberFrequencyService {

    private static final int NUMBER_COUNT = 25;
    private static final int DRAWN_NUMBERS_PER_CONTEST = 15;
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private final HistoricalFrequencyRepository repository;

    public CalculateRecentNumberFrequencyService(HistoricalFrequencyRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    public RecentNumberFrequencyAnalysis calculate(int requestedRecentContests) {
        if (requestedRecentContests < 1) {
            throw new IllegalArgumentException("O parâmetro concursos deve ser maior que zero");
        }

        RecentFrequencySnapshot snapshot = repository.calculateRecentDrawnNumberFrequencies(requestedRecentContests);
        validateSnapshot(snapshot);

        List<RecentNumberFrequencyAnalysis.Frequency> frequencies = snapshot.frequencies().stream()
                .sorted(Comparator.comparingLong(RecentDrawnNumberFrequency::recentOccurrences).reversed()
                        .thenComparingInt(RecentDrawnNumberFrequency::number))
                .map(frequency -> {
                    BigDecimal recentPercentage = percentage(frequency.recentOccurrences(), snapshot.recentContests());
                    BigDecimal historicalPercentage = percentage(frequency.historicalOccurrences(), snapshot.totalContests());
                    return new RecentNumberFrequencyAnalysis.Frequency(
                            frequency.number(), frequency.recentOccurrences(), recentPercentage,
                            frequency.historicalOccurrences(), historicalPercentage,
                            recentPercentage.subtract(historicalPercentage));
                })
                .toList();

        return new RecentNumberFrequencyAnalysis(snapshot.totalContests(), snapshot.recentContests(), frequencies);
    }

    private void validateSnapshot(RecentFrequencySnapshot snapshot) {
        Objects.requireNonNull(snapshot, "Recent frequency snapshot is required");
        List<RecentDrawnNumberFrequency> frequencies = snapshot.frequencies();
        boolean hasAllNumbersExactlyOnce = frequencies.size() == NUMBER_COUNT
                && IntStream.rangeClosed(1, NUMBER_COUNT).allMatch(number ->
                frequencies.stream().filter(frequency -> frequency.number() == number).count() == 1);
        if (!hasAllNumbersExactlyOnce || frequencies.stream().anyMatch(frequency ->
                frequency.recentOccurrences() < 0 || frequency.recentOccurrences() > snapshot.recentContests()
                        || frequency.historicalOccurrences() < 0
                        || frequency.historicalOccurrences() > snapshot.totalContests())) {
            throw new IllegalStateException("Consulta de frequência recente retornou dezenas ou totais inválidos");
        }

        long recentSum = frequencies.stream().mapToLong(RecentDrawnNumberFrequency::recentOccurrences).sum();
        long historicalSum = frequencies.stream().mapToLong(RecentDrawnNumberFrequency::historicalOccurrences).sum();
        if (recentSum != (long) DRAWN_NUMBERS_PER_CONTEST * snapshot.recentContests()
                || historicalSum != (long) DRAWN_NUMBERS_PER_CONTEST * snapshot.totalContests()) {
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
