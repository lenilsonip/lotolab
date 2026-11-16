package br.com.lotolab.domain;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class UniformLotofacilGameGenerator {

    public List<LotofacilGame> generate(GameGenerationCriteria criteria) {
        Random random = criteria.seed() == null ? new Random() : new Random(criteria.seed());
        Set<List<Integer>> unique = new LinkedHashSet<>();
        while (unique.size() < criteria.quantity()) {
            List<Integer> numbers = draw(random, criteria);
            unique.add(numbers);
        }
        return unique.stream().map(LotofacilGame::new).toList();
    }

    private List<Integer> draw(Random random, GameGenerationCriteria criteria) {
        List<Integer> odds = new ArrayList<>();
        List<Integer> evens = new ArrayList<>();
        for (int number = 1; number <= 25; number++) {
            ((number % 2 == 0) ? evens : odds).add(number);
        }
        if (criteria.evenNumbers() == null) {
            List<Integer> allNumbers = new ArrayList<>(25);
            for (int number = 1; number <= 25; number++) allNumbers.add(number);
            shuffleAndTake(allNumbers, 15, random);
            return allNumbers.subList(0, 15).stream().sorted().toList();
        }
        int evenCount = criteria.evenNumbers();
        int oddCount = criteria.oddNumbers();
        shuffleAndTake(evens, evenCount, random);
        shuffleAndTake(odds, oddCount, random);
        List<Integer> numbers = new ArrayList<>(15);
        numbers.addAll(evens.subList(0, evenCount));
        numbers.addAll(odds.subList(0, oddCount));
        numbers.sort(Integer::compareTo);
        return List.copyOf(numbers);
    }

    private void shuffleAndTake(List<Integer> values, int count, Random random) {
        for (int i = 0; i < count; i++) {
            int selected = i + random.nextInt(values.size() - i);
            Integer current = values.get(i);
            values.set(i, values.get(selected));
            values.set(selected, current);
        }
    }
}
