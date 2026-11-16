package br.com.lotolab.domain;

import java.util.List;
import java.util.Objects;

public record RecentFrequencySnapshot(long totalContests, long recentContests,
                                      List<RecentDrawnNumberFrequency> frequencies) {

    public RecentFrequencySnapshot {
        if (totalContests < 0 || recentContests < 0 || recentContests > totalContests) {
            throw new IllegalArgumentException("Totais de concursos recentes inválidos");
        }
        frequencies = List.copyOf(Objects.requireNonNull(frequencies));
    }
}
