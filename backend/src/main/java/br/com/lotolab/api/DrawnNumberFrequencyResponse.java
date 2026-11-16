package br.com.lotolab.api;

import br.com.lotolab.application.DrawnNumberFrequencyAnalysis;

import java.math.BigDecimal;
import java.util.List;

public record DrawnNumberFrequencyResponse(long totalContests, List<Frequency> frequencies) {

    public DrawnNumberFrequencyResponse {
        frequencies = List.copyOf(frequencies);
    }

    static DrawnNumberFrequencyResponse from(DrawnNumberFrequencyAnalysis analysis) {
        List<Frequency> frequencies = analysis.frequencies().stream()
                .map(frequency -> new Frequency(frequency.number(), frequency.occurrences(), frequency.percentage()))
                .toList();
        return new DrawnNumberFrequencyResponse(analysis.totalContests(), frequencies);
    }

    public record Frequency(int number, long occurrences, BigDecimal percentage) {
    }
}
