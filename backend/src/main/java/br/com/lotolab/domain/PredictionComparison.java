package br.com.lotolab.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Resultado determinístico da comparação entre um jogo previsto e um concurso real. */
public record PredictionComparison(
        List<Integer> predictedNumbers,
        List<Integer> actualNumbers,
        int hits,
        List<Integer> matchedNumbers,
        List<Integer> missedNumbers,
        List<Integer> unexpectedNumbers) {

    public PredictionComparison {
        predictedNumbers = new LotofacilGame(predictedNumbers).numbers();
        actualNumbers = new LotofacilGame(actualNumbers).numbers();
        matchedNumbers = immutableSorted(matchedNumbers);
        missedNumbers = immutableSorted(missedNumbers);
        unexpectedNumbers = immutableSorted(unexpectedNumbers);

        Set<Integer> predicted = new HashSet<>(predictedNumbers);
        Set<Integer> actual = new HashSet<>(actualNumbers);
        Set<Integer> matched = new HashSet<>(matchedNumbers);
        Set<Integer> expectedMatched = new HashSet<>(predicted);
        expectedMatched.retainAll(actual);
        Set<Integer> expectedMissed = new HashSet<>(predicted);
        expectedMissed.removeAll(actual);
        Set<Integer> expectedUnexpected = new HashSet<>(actual);
        expectedUnexpected.removeAll(predicted);

        if (hits != matchedNumbers.size()
                || !matched.equals(expectedMatched)
                || !new HashSet<>(missedNumbers).equals(expectedMissed)
                || !new HashSet<>(unexpectedNumbers).equals(expectedUnexpected)) {
            throw new IllegalArgumentException("Os dados da comparação não correspondem aos jogos informados");
        }
    }

    private static List<Integer> immutableSorted(List<Integer> numbers) {
        if (numbers == null || numbers.stream().anyMatch(number -> number == null || number < 1 || number > 25)) {
            throw new IllegalArgumentException("As dezenas devem estar entre 1 e 25");
        }
        if (new HashSet<>(numbers).size() != numbers.size()) {
            throw new IllegalArgumentException("A lista de dezenas não pode conter repetições");
        }
        return numbers.stream().sorted().toList();
    }
}
