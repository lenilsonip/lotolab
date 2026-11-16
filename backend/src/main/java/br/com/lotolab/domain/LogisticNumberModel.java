package br.com.lotolab.domain;

import java.util.List;

public record LogisticNumberModel(int number, double intercept, List<Double> coefficients) {
    public LogisticNumberModel {
        if (number < 1 || number > 25 || !Double.isFinite(intercept) || coefficients == null
                || coefficients.size() != 5 || coefficients.stream().anyMatch(value -> value == null || !Double.isFinite(value))) {
            throw new IllegalArgumentException("Parâmetros logísticos inválidos");
        }
        coefficients = List.copyOf(coefficients);
    }
}
