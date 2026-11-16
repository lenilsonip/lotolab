package br.com.lotolab.application;

import br.com.lotolab.domain.Contest;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NumberFeatureExtractorTest {
    private final NumberFeatureExtractor extractor = new NumberFeatureExtractor();

    @Test
    void featuresAndLabelsNeverReadAtOrAfterTheTarget() {
        List<Contest> prefix = List.of(contest(1, 1), contest(2, 2), contest(3, 3));
        List<Contest> changedFuture = new ArrayList<>(prefix);
        changedFuture.set(2, contest(3, 25));

        assertThat(extractor.featuresBefore(prefix, 25, 3))
                .containsExactly(extractor.featuresBefore(changedFuture, 25, 3));
        var examples = extractor.examplesBefore(prefix, 3);
        assertThat(examples).allMatch(example -> example.targetContestNumber() < 3);
        assertThat(examples).hasSize(50);
        assertThat(extractor.featuresBefore(List.of(), 1, 10)).containsExactly(0, 0, 0, 1, 0);
    }

    private static Contest contest(int number, int first) {
        List<Integer> numbers = new ArrayList<>();
        for (int i = 0; i < 15; i++) numbers.add((first + i - 1) % 25 + 1);
        return new Contest(number, LocalDate.of(2020, 1, 1).plusDays(number), numbers);
    }
}
