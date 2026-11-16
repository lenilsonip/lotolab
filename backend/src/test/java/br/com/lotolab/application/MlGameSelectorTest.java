package br.com.lotolab.application;

import br.com.lotolab.domain.LogisticModelArtifact;
import br.com.lotolab.domain.LogisticNumberModel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MlGameSelectorTest {
    @Test
    void selectsAReproducibleValidGameWithRequestedParityAndRespectsCutoff() {
        var models = java.util.stream.IntStream.rangeClosed(1, 25)
                .mapToObj(number -> new LogisticNumberModel(number, 0.01 * number, List.of(0.0, 0.0, 0.0, 0.0, 0.0))).toList();
        var artifact = new LogisticModelArtifact(1, NumberFeatureExtractor.SCHEMA_VERSION, 40, 1, 39,
                39, 975, 42, 100, 0.08, 0.1, "a".repeat(64), models);
        var selector = new MlGameSelector(new NumberFeatureExtractor(), new LogisticRegressionTrainer());

        var first = selector.select(artifact, List.of(), 40, 8, 7, 77);
        var repeated = selector.select(artifact, List.of(), 40, 8, 7, 77);
        assertThat(first.game().numbers()).hasSize(15).containsExactlyElementsOf(repeated.game().numbers());
        assertThat(first.game().numbers().stream().filter(n -> n % 2 == 0)).hasSize(8);
        assertThat(first.game().numbers().stream().filter(n -> n % 2 != 0)).hasSize(7);
        assertThat(first.scores()).hasSize(25);
        assertThatThrownBy(() -> selector.select(artifact, List.of(), 39, 8, 7, 77))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
