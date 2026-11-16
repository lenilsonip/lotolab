package br.com.lotolab.application;

import br.com.lotolab.domain.PredictionComparison;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class FeedbackPromptServiceTest {

    private final FeedbackPromptService service = new FeedbackPromptService();

    @Test
    void formatsComparisonValuesIntoPrompt() {
        PredictionComparison comparison = new PredictionComparison(
                numbers(1, 15), numbers(5, 19), 11,
                numbers(5, 15), numbers(1, 4), numbers(16, 19));

        String prompt = service.createPrompt(comparison);

        assertThat(prompt)
                .contains("O jogo gerado foi [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15]")
                .contains("O resultado real do concurso foi [5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19]")
                .contains("Tivemos 11 acertos")
                .contains("As dezenas que eu escolhi e falharam foram [1, 2, 3, 4]")
                .contains("As dezenas que eu ignorei e acabaram sendo sorteadas foram [16, 17, 18, 19]")
                .contains("sugira quais critérios devo adotar para gerar o próximo jogo.");
    }

    private static List<Integer> numbers(int first, int last) {
        return IntStream.rangeClosed(first, last).boxed().toList();
    }
}
