package br.com.lotolab.application;

import java.math.BigDecimal;
import java.util.List;

public record RecentNumberFrequencyAnalysis(long totalContests, long analyzedRecentContests,
                                            List<Frequency> frequencies) {

    public RecentNumberFrequencyAnalysis {
        frequencies = List.copyOf(frequencies);
    }

    public record Frequency(int number, long recentOccurrences, BigDecimal recentPercentage,
                            long historicalOccurrences, BigDecimal historicalPercentage,
                            BigDecimal differencePercentagePoints) {
    }
}
