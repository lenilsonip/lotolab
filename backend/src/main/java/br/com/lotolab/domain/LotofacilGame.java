package br.com.lotolab.domain;

import java.util.HashSet;
import java.util.List;

public record LotofacilGame(List<Integer> numbers) {

    public LotofacilGame {
        if (numbers == null || numbers.size() != 15 || numbers.stream().anyMatch(number ->
                number == null || number < 1 || number > 25) || new HashSet<>(numbers).size() != 15) {
            throw new IllegalArgumentException("O jogo deve conter 15 dezenas distintas entre 1 e 25");
        }
        numbers = numbers.stream().sorted().toList();
    }
}
