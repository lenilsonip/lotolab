package br.com.lotolab.application;

import br.com.lotolab.domain.LogisticNumberModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Small deterministic batch-gradient implementation; no third-party ML runtime is required. */
@Component
public final class LogisticRegressionTrainer {
    public static final int DEFAULT_EPOCHS = 200;
    public static final double DEFAULT_LEARNING_RATE = 0.08;
    public static final double DEFAULT_L2 = 0.10;

    public List<LogisticNumberModel> fit(List<NumberFeatureExtractor.TrainingExample> examples,
                                         int epochs, double learningRate, double l2, long maxMillis) {
        Objects.requireNonNull(examples, "Exemplos obrigatórios");
        if (epochs < 1 || epochs > 500 || !Double.isFinite(learningRate) || learningRate <= 0
                || !Double.isFinite(l2) || l2 < 0 || maxMillis < 1) {
            throw new IllegalArgumentException("Hiperparâmetros fora dos limites permitidos");
        }
        long deadline = System.nanoTime() + maxMillis * 1_000_000L;
        List<LogisticNumberModel> models = new ArrayList<>(25);
        for (int number = 1; number <= 25; number++) {
            int currentNumber = number;
            List<NumberFeatureExtractor.TrainingExample> rows = examples.stream()
                    .filter(example -> example.number() == currentNumber).toList();
            if (rows.size() < 30) throw new IllegalArgumentException("São necessários ao menos 30 exemplos por dezena");
            double[] coefficients = new double[NumberFeatureExtractor.FEATURE_COUNT];
            double positives = rows.stream().filter(NumberFeatureExtractor.TrainingExample::occurred).count();
            double prior = Math.min(0.99, Math.max(0.01, positives / rows.size()));
            double intercept = Math.log(prior / (1 - prior));
            for (int epoch = 0; epoch < epochs; epoch++) {
                if ((epoch & 7) == 0 && System.nanoTime() > deadline) {
                    throw new TrainingTimeLimitException();
                }
                double interceptGradient = 0;
                double[] gradient = new double[coefficients.length];
                for (var row : rows) {
                    double linear = intercept;
                    for (int j = 0; j < coefficients.length; j++) linear += coefficients[j] * row.feature(j);
                    double error = sigmoid(linear) - (row.occurred() ? 1.0 : 0.0);
                    interceptGradient += error;
                    for (int j = 0; j < coefficients.length; j++) gradient[j] += error * row.feature(j);
                }
                double scale = 1.0 / rows.size();
                intercept -= learningRate * interceptGradient * scale;
                for (int j = 0; j < coefficients.length; j++) {
                    coefficients[j] -= learningRate * (gradient[j] * scale + l2 * coefficients[j]);
                }
            }
            models.add(new LogisticNumberModel(number, intercept,
                    java.util.Arrays.stream(coefficients).boxed().toList()));
        }
        return List.copyOf(models);
    }

    public static double score(LogisticNumberModel model, double[] features) {
        if (features == null || features.length != NumberFeatureExtractor.FEATURE_COUNT) {
            throw new IllegalArgumentException("Schema de features incompatível");
        }
        for (double value : features) if (!Double.isFinite(value)) throw new IllegalArgumentException("Feature não finita");
        return sigmoid(model.intercept() + dot(model.coefficients().stream().mapToDouble(Double::doubleValue).toArray(), features));
    }

    private static double dot(double[] weights, double[] features) {
        double result = 0;
        for (int i = 0; i < weights.length; i++) result += weights[i] * features[i];
        return result;
    }

    private static double sigmoid(double value) {
        if (value >= 0) return 1.0 / (1.0 + Math.exp(-Math.min(value, 40)));
        double exp = Math.exp(Math.max(value, -40));
        return exp / (1.0 + exp);
    }

    public static final class TrainingTimeLimitException extends RuntimeException {
        public TrainingTimeLimitException() { super("Limite de tempo do treinamento excedido"); }
    }
}
