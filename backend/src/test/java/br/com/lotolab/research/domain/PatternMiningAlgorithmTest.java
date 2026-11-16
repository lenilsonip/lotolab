package br.com.lotolab.research.domain;

import br.com.lotolab.domain.Contest;
import br.com.lotolab.domain.PatternInsight;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class PatternMiningAlgorithmTest {
    private final PatternMiningAlgorithm algorithm = new PatternMiningAlgorithm();

    @Test
    void computesKnownWalkForwardFrequencyAndErrorValuesDeterministically() {
        List<Contest> contests = List.of(contest(1, 9), contest(2, 7), contest(3, 5),
                contest(4, 8), contest(5, 6), contest(6, 7));
        List<PatternInsight> first = algorithm.evaluateFold(42, 1, contests.subList(0, 3),
                contests.subList(3, 4), 1234L);
        List<PatternInsight> repeated = algorithm.evaluateFold(42, 1, contests.subList(0, 3),
                contests.subList(3, 4), 1234L);

        PatternInsight oddMean = first.stream().filter(value -> value.hypothesisKey().equals("ODD_COUNT_MEAN:fold-1"))
                .findFirst().orElseThrow();
        assertThat(oddMean.observedMetric()).hasToString("1.000000");
        assertThat(oddMean.trainingLastContest()).isEqualTo(3);
        assertThat(oddMean.validationFirstContest()).isEqualTo(4);

        PatternInsight numberOne = first.stream()
                .filter(value -> value.hypothesisKey().equals("NUMBER_FREQUENCY:n=1:w=3:fold=1"))
                .findFirst().orElseThrow();
        assertThat(numberOne.observedMetric()).hasToString("0.000000");
        assertThat(numberOne.baselineMetric()).hasToString("0.400000");
        assertThat(numberOne.evidenceJson()).contains("\"predictedValue\":1.000000", "\"validationRate\":1.000000");
        assertThat(numberOne.evidenceStatus()).isEqualTo("INSUFFICIENT_DATA");
        assertThat(first).hasSize(72).isEqualTo(repeated);
        assertThat(first).allSatisfy(value -> assertThat(value.algorithmVersion()).isEqualTo("walk-forward-2"));
    }

    @Test
    void preservesExactWalkForwardOutputAfterCoOccurrenceOptimization() throws Exception {
        List<Contest> contests = IntStream.rangeClosed(1, 120)
                .mapToObj(number -> new Contest(number, LocalDate.of(2020, 1, 1).plusDays(number),
                        IntStream.range(0, 15).map(offset -> ((number * 3 + offset * 7) % 25) + 1)
                                .boxed().toList()))
                .toList();
        List<PatternInsight> insights = algorithm.evaluateFold(77, 3, contests.subList(0, 80),
                contests.subList(80, 100), 918273L);
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(insights.toString().getBytes(StandardCharsets.UTF_8));
        assertThat(java.util.HexFormat.of().formatHex(digest))
                .isEqualTo("b71d4af2b3d3b65e5e1586f358235a879bf686437a5d6530e98ba28ba3c4c05d");
    }

    private static Contest contest(int number, int oddCount) {
        var values = new java.util.ArrayList<Integer>();
        for (int n = 1; values.size() < oddCount; n += 2) values.add(n);
        for (int n = 2; values.size() < 15; n += 2) values.add(n);
        return new Contest(number, LocalDate.of(2024, 1, number), values);
    }
}
