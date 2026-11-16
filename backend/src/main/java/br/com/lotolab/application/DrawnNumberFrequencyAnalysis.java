package br.com.lotolab.application;

import java.math.BigDecimal;
import java.util.List;

public record DrawnNumberFrequencyAnalysis(long totalContests, List<Frequency> frequencies) {

    public DrawnNumberFrequencyAnalysis {
        frequencies = List.copyOf(frequencies);
    }

    public record Frequency(int number, long occurrences, BigDecimal percentage) {
    }
}
