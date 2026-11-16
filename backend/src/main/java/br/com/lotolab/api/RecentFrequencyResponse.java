package br.com.lotolab.api;

import br.com.lotolab.application.RecentNumberFrequencyAnalysis;

import java.math.BigDecimal;
import java.util.List;

public record RecentFrequencyResponse(long totalContests, long analyzedRecentContests,
                                      List<Frequency> frequencies) {

    public RecentFrequencyResponse {
        frequencies = List.copyOf(frequencies);
    }

    static RecentFrequencyResponse from(RecentNumberFrequencyAnalysis analysis) {
        List<Frequency> frequencies = analysis.frequencies().stream()
                .map(frequency -> new Frequency(frequency.number(), frequency.recentOccurrences(),
                        frequency.recentPercentage(), frequency.historicalOccurrences(),
                        frequency.historicalPercentage(), frequency.differencePercentagePoints()))
                .toList();
        return new RecentFrequencyResponse(analysis.totalContests(), analysis.analyzedRecentContests(), frequencies);
    }

    public record Frequency(int number, long recentOccurrences, BigDecimal recentPercentage,
                            long historicalOccurrences, BigDecimal historicalPercentage,
                            BigDecimal differencePercentagePoints) {
    }
}
