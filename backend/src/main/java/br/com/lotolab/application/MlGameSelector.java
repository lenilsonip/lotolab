package br.com.lotolab.application;

import br.com.lotolab.domain.LogisticModelArtifact;
import br.com.lotolab.domain.LotofacilGame;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/** Selects by model ranking score, without replacement and with reproducible tie breaking. */
@Component
public final class MlGameSelector {
    private final NumberFeatureExtractor features;
    private final LogisticRegressionTrainer trainer;

    public MlGameSelector(NumberFeatureExtractor features, LogisticRegressionTrainer trainer) {
        this.features = features; this.trainer = trainer;
    }

    public Selection select(LogisticModelArtifact artifact, List<br.com.lotolab.domain.Contest> history,
                            int targetContest, int evenNumbers, int oddNumbers, long seed) {
        if (targetContest < artifact.trainingCutoffExclusive()) throw new IllegalArgumentException("Modelo treinado com informação posterior ao alvo");
        if (evenNumbers < 0 || oddNumbers < 0 || evenNumbers + oddNumbers != 15) throw new IllegalArgumentException("Filtro de paridade deve somar 15");
        List<ScoredNumber> scored = new ArrayList<>(25);
        for (var model : artifact.numberModels()) {
            double score = LogisticRegressionTrainer.score(model,
                    features.featuresBefore(history, model.number(), targetContest));
            long tie = mix64(seed ^ (0x9E3779B97F4A7C15L * model.number()));
            scored.add(new ScoredNumber(model.number(), score, tie));
        }
        Comparator<ScoredNumber> order = Comparator.comparingDouble(ScoredNumber::score).reversed()
                .thenComparingLong(ScoredNumber::tieBreaker).thenComparingInt(ScoredNumber::number);
        List<Integer> chosen = new ArrayList<>(15);
        scored.stream().filter(n -> n.number() % 2 == 0).sorted(order).limit(evenNumbers).forEach(n -> chosen.add(n.number()));
        scored.stream().filter(n -> n.number() % 2 != 0).sorted(order).limit(oddNumbers).forEach(n -> chosen.add(n.number()));
        LotofacilGame game = new LotofacilGame(chosen);
        return new Selection(game, scored.stream().sorted(Comparator.comparingInt(ScoredNumber::number)).toList());
    }

    private static long mix64(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    public record ScoredNumber(int number, double score, long tieBreaker) { }
    public record Selection(LotofacilGame game, List<ScoredNumber> scores) {
        public Selection { scores = List.copyOf(scores); }
    }
}
