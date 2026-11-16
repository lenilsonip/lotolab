package br.com.lotolab.application;

import br.com.lotolab.domain.Contest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Deterministic, per-number features. Every target is excluded from its own inputs. */
@Component
public final class NumberFeatureExtractor {
    public static final String SCHEMA_VERSION = "lotofacil-number-features-v1";
    public static final int FEATURE_COUNT = 5;
    private static final int[] WINDOWS = {10, 30, 100};

    public List<TrainingExample> examplesBefore(List<Contest> history, int exclusiveCutoff) {
        Objects.requireNonNull(history, "Histórico obrigatório");
        if (exclusiveCutoff <= 0) throw new IllegalArgumentException("Cutoff deve ser positivo");
        List<Contest> ordered = history.stream().filter(c -> c.number() < exclusiveCutoff)
                .sorted(Comparator.comparingInt(Contest::number)).toList();
        List<TrainingExample> examples = new ArrayList<>(ordered.size() * 25);
        for (int i = 0; i < ordered.size(); i++) {
            Contest target = ordered.get(i);
            List<Contest> prior = ordered.subList(0, i);
            for (int number = 1; number <= 25; number++) {
                examples.add(new TrainingExample(target.number(), number, features(prior, number),
                        target.drawnNumbers().contains(number)));
            }
        }
        return List.copyOf(examples);
    }

    public double[] featuresBefore(List<Contest> history, int number, int targetContest) {
        if (number < 1 || number > 25) throw new IllegalArgumentException("Dezena deve estar entre 1 e 25");
        if (targetContest <= 0) throw new IllegalArgumentException("Concurso-alvo deve ser positivo");
        List<Contest> prior = Objects.requireNonNull(history, "Histórico obrigatório").stream()
                .filter(c -> c.number() < targetContest)
                .sorted(Comparator.comparingInt(Contest::number)).toList();
        return features(prior, number);
    }

    private static double[] features(List<Contest> prior, int number) {
        double[] result = new double[FEATURE_COUNT];
        for (int feature = 0; feature < WINDOWS.length; feature++) {
            int from = Math.max(0, prior.size() - WINDOWS[feature]);
            int available = prior.size() - from;
            long count = prior.subList(from, prior.size()).stream()
                    .filter(contest -> contest.drawnNumbers().contains(number)).count();
            result[feature] = available == 0 ? 0.0 : ((double) count / available) - 0.6;
        }
        int gap = 100;
        for (int i = prior.size() - 1; i >= 0; i--) {
            if (prior.get(i).drawnNumbers().contains(number)) {
                gap = Math.min(100, prior.size() - i - 1);
                break;
            }
        }
        result[3] = gap / 100.0;
        result[4] = result[0] - result[1];
        return result;
    }

    public record TrainingExample(int targetContestNumber, int number, double[] features, boolean occurred) {
        public TrainingExample {
            if (targetContestNumber <= 0 || number < 1 || number > 25) throw new IllegalArgumentException("Exemplo inválido");
            if (features == null || features.length != FEATURE_COUNT) throw new IllegalArgumentException("Schema de features incompatível");
            features = features.clone();
            for (double value : features) if (!Double.isFinite(value)) throw new IllegalArgumentException("Feature não finita");
        }
        @Override public double[] features() { return features.clone(); }
        public double feature(int index) { return features[index]; }
    }
}
