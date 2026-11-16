package br.com.lotolab.application;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LogisticRegressionTrainerTest {
    @Test
    void fitsNonTrivialCoefficientsDeterministicallyAndChangesWhenLabelsChange() {
        var trainer = new LogisticRegressionTrainer();
        var positive = examples(false);
        var negative = examples(true);
        var first = trainer.fit(positive, 120, 0.08, 0.1, 10_000);
        var repeated = trainer.fit(positive, 120, 0.08, 0.1, 10_000);
        var changed = trainer.fit(negative, 120, 0.08, 0.1, 10_000);

        assertThat(first).isEqualTo(repeated);
        assertThat(first.getFirst().coefficients().getFirst()).isNotZero();
        assertThat(first.getFirst().coefficients()).isNotEqualTo(changed.getFirst().coefficients());
        assertThat(LogisticRegressionTrainer.score(first.getFirst(), new double[]{0.4, 0.2, 0.1, 0.2, 0.2}))
                .isGreaterThan(LogisticRegressionTrainer.score(first.getFirst(), new double[]{-0.4, -0.2, -0.1, 0.8, -0.2}));
    }

    private static List<NumberFeatureExtractor.TrainingExample> examples(boolean invert) {
        List<NumberFeatureExtractor.TrainingExample> rows = new ArrayList<>();
        for (int draw = 0; draw < 80; draw++) {
            double trend = (draw - 39.5) / 40.0;
            for (int number = 1; number <= 25; number++) {
                boolean outcome = trend > 0;
                rows.add(new NumberFeatureExtractor.TrainingExample(draw + 1, number,
                        new double[]{trend, trend * 0.5, trend * 0.25, draw / 100.0, trend * 0.5}, invert != outcome));
            }
        }
        return rows;
    }
}
