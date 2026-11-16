package br.com.lotolab.application;

import br.com.lotolab.domain.PredictionComparison;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class FeedbackPromptService {

    public String createPrompt(PredictionComparison comparison) {
        Objects.requireNonNull(comparison, "A comparação é obrigatória");
        return "O jogo gerado foi %s. O resultado real do concurso foi %s. Tivemos %d acertos. "
                .formatted(comparison.predictedNumbers(), comparison.actualNumbers(), comparison.hits())
                + "As dezenas que eu escolhi e falharam foram %s. "
                .formatted(comparison.missedNumbers())
                + "As dezenas que eu ignorei e acabaram sendo sorteadas foram %s. "
                .formatted(comparison.unexpectedNumbers())
                + "Com base nesses dados, analise a falha na minha escolha de dezenas e sugira quais critérios "
                + "devo adotar para gerar o próximo jogo.";
    }
}
