package br.com.lotolab.domain;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public record Contest(int number, LocalDate drawDate, List<Integer> drawnNumbers) {

    public static final int DRAWN_NUMBER_COUNT = 15;
    public static final int MIN_DRAWN_NUMBER = 1;
    public static final int MAX_DRAWN_NUMBER = 25;

    public Contest {
        if (number <= 0) {
            throw new IllegalArgumentException("Contest number must be positive");
        }
        Objects.requireNonNull(drawDate, "Draw date is required");
        Objects.requireNonNull(drawnNumbers, "Drawn numbers are required");

        if (drawnNumbers.size() != DRAWN_NUMBER_COUNT) {
            throw new IllegalArgumentException("A Lotofacil contest must contain exactly 15 drawn numbers");
        }
        if (drawnNumbers.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Drawn numbers cannot contain null values");
        }
        if (drawnNumbers.stream().anyMatch(drawnNumber ->
                drawnNumber < MIN_DRAWN_NUMBER || drawnNumber > MAX_DRAWN_NUMBER)) {
            throw new IllegalArgumentException("Drawn numbers must be between 1 and 25");
        }
        if (new HashSet<>(drawnNumbers).size() != DRAWN_NUMBER_COUNT) {
            throw new IllegalArgumentException("Drawn numbers must be unique");
        }

        drawnNumbers = drawnNumbers.stream().sorted().toList();
    }
}
