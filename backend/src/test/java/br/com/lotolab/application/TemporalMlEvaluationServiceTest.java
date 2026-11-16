package br.com.lotolab.application;

import br.com.lotolab.domain.Contest;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class TemporalMlEvaluationServiceTest {
    @Test
    void trainsOnlyOnEarlierContestsAndComparesAgainstPairedUniformBaseline() {
        var extractor = new NumberFeatureExtractor();
        var trainer = new LogisticRegressionTrainer();
        var selector = new MlGameSelector(extractor, trainer);
        var codec = new LogisticModelArtifactCodec(new tools.jackson.databind.ObjectMapper());
        var evaluator = new TemporalMlEvaluationService(extractor, trainer, selector, codec);
        List<Contest> history = syntheticHistory(80);

        var slices = evaluator.evaluate(history, 111L, 30, 0.05, 0.1, 5000);

        assertThat(slices).extracting(slice -> slice.split()).containsExactly("VALIDATION", "HOLDOUT");
        assertThat(slices).allSatisfy(slice -> {
            assertThat(slice.trainingLastContest()).isLessThan(slice.targetFirstContest());
            assertThat(slice.sampleCount()).isEqualTo(slice.targetLastContest() - slice.targetFirstContest() + 1);
            assertThat(slice.modelHitDistribution()).hasSize(16);
            assertThat(slice.uniformHitDistribution()).hasSize(16);
            assertThat(slice.modelHitDistribution().stream().mapToInt(Integer::intValue).sum()).isEqualTo(slice.sampleCount());
            assertThat(slice.uniformHitDistribution().stream().mapToInt(Integer::intValue).sum()).isEqualTo(slice.sampleCount());
            assertThat(slice.confidence95Lower()).isLessThanOrEqualTo(slice.meanPairedDifference());
            assertThat(slice.confidence95Upper()).isGreaterThanOrEqualTo(slice.meanPairedDifference());
        });
        assertThat(slices.get(0).targetLastContest()).isLessThan(slices.get(1).targetFirstContest());
        assertThat(slices.get(1).trainingLastContest()).isLessThan(slices.get(1).targetFirstContest());
    }

    private static List<Contest> syntheticHistory(int count) {
        List<Contest> contests = new ArrayList<>();
        for (int contest = 1; contest <= count; contest++) {
            List<Integer> pool = new ArrayList<>();
            for (int number = 1; number <= 25; number++) pool.add(number);
            Collections.shuffle(pool, new Random(70_000L + contest));
            contests.add(new Contest(contest, LocalDate.of(2020, 1, 1).plusDays(contest), pool.subList(0, 15)));
        }
        return contests;
    }
}
