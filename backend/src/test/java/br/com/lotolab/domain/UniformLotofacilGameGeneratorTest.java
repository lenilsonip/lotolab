package br.com.lotolab.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UniformLotofacilGameGeneratorTest {
    private final UniformLotofacilGameGenerator generator = new UniformLotofacilGameGenerator();

    @Test
    void generatesValidSortedDistinctGamesWithoutDuplicates() {
        var criteria = new GameGenerationCriteria(100, null, null, 42L);

        List<LotofacilGame> games = generator.generate(criteria);

        assertThat(games).hasSize(100).doesNotHaveDuplicates();
        assertThat(games).allSatisfy(game -> {
            assertThat(game.numbers()).hasSize(15).doesNotHaveDuplicates()
                    .containsExactlyElementsOf(game.numbers().stream().sorted().toList())
                    .allMatch(number -> number >= 1 && number <= 25);
        });
    }

    @Test
    void appliesParityAndSeedReproducesSameGames() {
        var criteria = new GameGenerationCriteria(20, 8, 7, 987L);

        List<LotofacilGame> first = generator.generate(criteria);
        List<LotofacilGame> second = generator.generate(criteria);

        assertThat(first).isEqualTo(second);
        assertThat(first).allSatisfy(game -> {
            assertThat(game.numbers().stream().filter(number -> number % 2 == 0)).hasSize(8);
            assertThat(game.numbers().stream().filter(number -> number % 2 == 1)).hasSize(7);
        });
    }

    @Test
    void rejectsInvalidQuantityAndImpossibleParityFilter() {
        assertThatThrownBy(() -> new GameGenerationCriteria(101, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GameGenerationCriteria(1, 13, 2, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GameGenerationCriteria(1, 8, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
