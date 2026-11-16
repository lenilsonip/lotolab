package br.com.lotolab.domain;

import java.util.List;
import java.util.Objects;

/** Immutable, JSON-safe learned parameters. The cutoff is exclusive. */
public record LogisticModelArtifact(int artifactSchemaVersion, String featureSchemaVersion,
                                    int trainingCutoffExclusive, Integer trainingFirstContest,
                                    Integer trainingLastContest, int trainingContestCount,
                                    int trainingExampleCount, long seed, int epochs,
                                    double learningRate, double l2, String datasetChecksum,
                                    List<LogisticNumberModel> numberModels) {
    public LogisticModelArtifact {
        if (artifactSchemaVersion != 1 || !"lotofacil-number-features-v1".equals(featureSchemaVersion)
                || trainingCutoffExclusive <= 0 || trainingContestCount < 30 || trainingExampleCount < 750
                || epochs < 1 || !Double.isFinite(learningRate) || learningRate <= 0
                || !Double.isFinite(l2) || l2 < 0 || datasetChecksum == null || !datasetChecksum.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Metadados do artefato ML inválidos ou incompatíveis");
        }
        if (trainingFirstContest == null || trainingLastContest == null
                || trainingLastContest >= trainingCutoffExclusive || trainingFirstContest > trainingLastContest) {
            throw new IllegalArgumentException("Janela temporal incompatível com o cutoff");
        }
        Objects.requireNonNull(numberModels, "Modelos por dezena obrigatórios");
        numberModels = numberModels.stream().sorted(java.util.Comparator.comparingInt(LogisticNumberModel::number)).toList();
        if (numberModels.size() != 25) throw new IllegalArgumentException("O artefato deve conter 25 classificadores");
        for (int i = 0; i < 25; i++) if (numberModels.get(i).number() != i + 1) {
            throw new IllegalArgumentException("Classificadores devem cobrir exatamente as dezenas 1 a 25");
        }
    }
}
