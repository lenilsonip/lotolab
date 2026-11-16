package br.com.lotolab.domain;

import java.util.List;
import java.util.Objects;

public record FrequencySnapshot(long totalContests, List<DrawnNumberFrequency> frequencies) {

    public FrequencySnapshot {
        if (totalContests < 0) {
            throw new IllegalArgumentException("Total de concursos não pode ser negativo");
        }
        frequencies = List.copyOf(Objects.requireNonNull(frequencies));
    }
}
