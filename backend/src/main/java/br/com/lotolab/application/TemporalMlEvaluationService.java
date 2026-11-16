package br.com.lotolab.application;

import br.com.lotolab.domain.Contest;
import br.com.lotolab.domain.LogisticModelArtifact;
import br.com.lotolab.domain.LogisticNumberModel;
import br.com.lotolab.domain.LotofacilGame;
import br.com.lotolab.domain.MlEvaluationSlice;
import br.com.lotolab.domain.UniformLotofacilGameGenerator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

/** Fixed-parameter, expanding-window evaluation; validation is kept before the final holdout. */
@Service
public final class TemporalMlEvaluationService {
    private static final int MIN_TRAINING_CONTESTS = 30;
    private final NumberFeatureExtractor extractor;
    private final LogisticRegressionTrainer trainer;
    private final MlGameSelector selector;
    private final LogisticModelArtifactCodec codec;

    public TemporalMlEvaluationService(NumberFeatureExtractor extractor, LogisticRegressionTrainer trainer,
                                       MlGameSelector selector, LogisticModelArtifactCodec codec) {
        this.extractor = extractor; this.trainer = trainer; this.selector = selector; this.codec = codec;
    }

    public List<MlEvaluationSlice> evaluate(List<Contest> source, long seed, int epochs,
                                            double learningRate, double l2, long maxMillis) {
        List<Contest> history = source.stream().sorted(Comparator.comparingInt(Contest::number)).toList();
        if (history.size() < MIN_TRAINING_CONTESTS + 4) throw new IllegalArgumentException("Histórico insuficiente para validação e holdout temporal");
        int trainEnd = Math.max(MIN_TRAINING_CONTESTS, (int) (history.size() * 0.60));
        int validationEnd = Math.max(trainEnd + 1, (int) (history.size() * 0.80));
        validationEnd = Math.min(validationEnd, history.size() - 1);
        int validationFirst = history.get(trainEnd).number();
        int holdoutFirst = history.get(validationEnd).number();
        List<MlEvaluationSlice> result = new ArrayList<>(2);
        LogisticModelArtifact validationModel = fit(history, validationFirst, seed ^ 0x4D4C56414CL,
                epochs, learningRate, l2, maxMillis);
        result.add(scoreSplit("VALIDATION", history, trainEnd, validationEnd, validationModel, seed ^ 0x56414C4944L));
        LogisticModelArtifact holdoutModel = fit(history, holdoutFirst, seed ^ 0x4D4C484F4CL,
                epochs, learningRate, l2, maxMillis);
        result.add(scoreSplit("HOLDOUT", history, validationEnd, history.size(), holdoutModel, seed ^ 0x484F4C444FL));
        return List.copyOf(result);
    }

    private LogisticModelArtifact fit(List<Contest> history, int cutoff, long seed, int epochs,
                                     double learningRate, double l2, long maxMillis) {
        List<Contest> train = history.stream().filter(c -> c.number() < cutoff).toList();
        List<NumberFeatureExtractor.TrainingExample> examples = extractor.examplesBefore(train, cutoff);
        List<LogisticNumberModel> models = trainer.fit(examples, epochs, learningRate, l2, maxMillis);
        int first = train.getFirst().number(), last = train.getLast().number();
        String hash = datasetChecksum(train, cutoff);
        return new LogisticModelArtifact(1, NumberFeatureExtractor.SCHEMA_VERSION, cutoff, first, last,
                train.size(), examples.size(), seed, epochs, learningRate, l2, hash, models);
    }

    private MlEvaluationSlice scoreSplit(String name, List<Contest> history, int from, int to,
                                         LogisticModelArtifact model, long seed) {
        List<Contest> targets = history.subList(from, to);
        List<Integer> modelHits = new ArrayList<>(targets.size());
        List<Integer> randomHits = new ArrayList<>(targets.size());
        UniformLotofacilGameGenerator uniform = new UniformLotofacilGameGenerator();
        for (Contest target : targets) {
            long drawSeed = seed ^ (target.number() * 0x9E3779B97F4A7C15L);
            LotofacilGame predicted = selector.select(model, history, target.number(), 8, 7, drawSeed).game();
            LotofacilGame baseline = uniform.generate(new br.com.lotolab.domain.GameGenerationCriteria(1, 8, 7, drawSeed)).getFirst();
            modelHits.add(hits(predicted, target));
            randomHits.add(hits(baseline, target));
        }
        double meanModel = mean(modelHits), meanRandom = mean(randomHits);
        List<Double> paired = new ArrayList<>(modelHits.size());
        for (int i = 0; i < modelHits.size(); i++) paired.add((double) modelHits.get(i) - randomHits.get(i));
        double difference = mean(paired);
        double standardError = paired.size() < 2 ? 0 : Math.sqrt(variance(paired) / paired.size());
        int[] modelDistribution = distribution(modelHits), baselineDistribution = distribution(randomHits);
        int trainingFirst = history.stream().filter(c -> c.number() < model.trainingCutoffExclusive())
                .mapToInt(Contest::number).min().orElseThrow();
        int trainingLast = model.trainingLastContest();
        return new MlEvaluationSlice(name, trainingFirst, trainingLast, targets.getFirst().number(),
                targets.getLast().number(), targets.size(), meanModel, meanRandom, difference,
                standardError, difference - 1.96 * standardError, difference + 1.96 * standardError,
                Arrays.stream(modelDistribution).boxed().toList(), Arrays.stream(baselineDistribution).boxed().toList(), seed);
    }

    private static int hits(LotofacilGame game, Contest contest) {
        return (int) game.numbers().stream().filter(contest.drawnNumbers()::contains).count();
    }
    private static int[] distribution(List<Integer> values) {
        int[] result = new int[16]; values.forEach(value -> result[value]++); return result;
    }
    private static double mean(List<? extends Number> values) {
        return values.stream().mapToDouble(Number::doubleValue).average().orElse(0);
    }
    private static double variance(List<Double> values) {
        double mean = mean(values); return values.stream().mapToDouble(v -> (v - mean) * (v - mean)).sum() / (values.size() - 1);
    }
    public static String datasetChecksum(List<Contest> contests, int cutoff) {
        String canonical = contests.stream().filter(c -> c.number() < cutoff)
                .sorted(Comparator.comparingInt(Contest::number))
                .map(c -> c.number() + "|" + c.drawDate() + "|" + c.drawnNumbers())
                .collect(java.util.stream.Collectors.joining("\n"));
        return LogisticModelArtifactCodec.sha256("cutoff=" + cutoff + "\n" + canonical);
    }
}
