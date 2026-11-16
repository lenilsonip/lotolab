package br.com.lotolab.research.domain;

import br.com.lotolab.domain.Contest;
import br.com.lotolab.domain.PatternInsight;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.function.ToDoubleFunction;
import java.util.stream.IntStream;

/** Pure, deterministic calculations for retrospective pattern hypotheses. */
public final class PatternMiningAlgorithm {
    public static final String ALGORITHM_VERSION = "walk-forward-2";
    private static final BigDecimal UNCALIBRATED_CONFIDENCE = BigDecimal.ZERO.setScale(4);
    private static final int RANDOM_BASELINE_SAMPLES_PER_CONTEST = 64;
    private static final int[] FREQUENCY_WINDOWS = {50, 100, 250, 500};
    private static final int TOP_CO_OCCURRENCES = 16;

    public List<PatternInsight> evaluateFold(long runId, int fold, List<Contest> training,
                                               List<Contest> validation, long seed) {
        List<PatternInsight> result = new ArrayList<>();
        for (Metric metric : Metric.values()) {
            double prediction = training.stream().mapToDouble(metric.value).average().orElse(0);
            double observedError = meanAbsoluteError(validation, metric.value, prediction);
            double randomError = randomBaselineError(metric.value, validation, seed + metric.ordinal());
            result.add(insight(runId, fold, training, validation, metric.type, metric.key + ":fold-" + fold,
                    metric.label, prediction, observedError, randomError,
                    metricEvidence(metric, prediction, fold)));
        }

        addFrequencyEvidence(result, runId, fold, training, validation);
        addCoOccurrenceEvidence(result, runId, fold, training, validation);
        addConsecutiveRepeatEvidence(result, runId, fold, training, validation);
        return result;
    }

    private void addFrequencyEvidence(List<PatternInsight> target, long runId, int fold,
                                      List<Contest> training, List<Contest> validation) {
        int[] windows = IntStream.of(FREQUENCY_WINDOWS).filter(window -> window <= training.size()).toArray();
        if (windows.length == 0) windows = new int[]{training.size()};
        double longTermMean = 15.0 / 25.0;
        for (int window : windows) {
            List<Contest> recent = training.subList(training.size() - window, training.size());
            for (int number = 1; number <= 25; number++) {
                final int selectedNumber = number;
                double predictedRate = rate(recent, draw -> draw.drawnNumbers().contains(selectedNumber));
                double validationRate = rate(validation, draw -> draw.drawnNumbers().contains(selectedNumber));
                double observedError = meanAbsoluteIndicatorError(validation, selectedNumber, predictedRate);
                double randomError = meanAbsoluteIndicatorError(validation, selectedNumber, longTermMean);
                double priorRate = rate(training, draw -> draw.drawnNumbers().contains(selectedNumber));
                double temporalDelta = predictedRate - priorRate;
                String key = "NUMBER_FREQUENCY:n=" + number + ":w=" + window + ":fold=" + fold;
                String json = String.format(Locale.ROOT,
                        "{\"number\":%d,\"windowSize\":%d,\"predictedValue\":%.6f,\"validationRate\":%.6f,\"temporalDelta\":%.6f,\"temporalStability\":%.6f}",
                        number, window, predictedRate, validationRate, temporalDelta,
                        temporalStability(training, number, window));
                target.add(insight(runId, fold, training, validation, "NUMBER_FREQUENCY", key,
                        "Frequência da dezena " + number + " na janela " + window,
                        predictedRate, observedError, randomError, json));
            }
        }
    }

    private void addCoOccurrenceEvidence(List<PatternInsight> target, long runId, int fold,
                                         List<Contest> training, List<Contest> validation) {
        Map<Integer, Long> pairs = new HashMap<>();
        Map<Integer, Long> triples = new HashMap<>();
        for (Contest draw : training) {
            List<Integer> numbers = draw.drawnNumbers();
            for (int first = 0; first < numbers.size(); first++) {
                int firstBit = 1 << (numbers.get(first) - 1);
                for (int second = first + 1; second < numbers.size(); second++) {
                    int secondBit = 1 << (numbers.get(second) - 1);
                    pairs.merge(firstBit | secondBit, 1L, Long::sum);
                    for (int third = second + 1; third < numbers.size(); third++) {
                        triples.merge(firstBit | secondBit | (1 << (numbers.get(third) - 1)), 1L, Long::sum);
                    }
                }
            }
        }
        addTopCombinations(target, runId, fold, pairs, training, validation, 2, "CO_OCCURRENCE_PAIR");
        addTopCombinations(target, runId, fold, triples, training, validation, 3, "CO_OCCURRENCE_TRIPLE");
    }

    private void addTopCombinations(List<PatternInsight> target, long runId, int fold,
                                    Map<Integer, Long> observedCounts, List<Contest> training,
                                    List<Contest> validation, int size, String type) {
        List<CombinationCount> combinations = observedCounts.entrySet().stream()
                .map(entry -> new CombinationCount(entry.getKey(), entry.getValue())).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        combinations.sort(Comparator.comparingLong(CombinationCount::count).reversed()
                .thenComparingInt(CombinationCount::mask));
        double baselineRate = size == 2 ? (15.0 / 25) * (14.0 / 24)
                : (15.0 / 25) * (14.0 / 24) * (13.0 / 23);
        for (CombinationCount candidate : combinations.stream().limit(TOP_CO_OCCURRENCES).toList()) {
            double predictedRate = candidate.count() / (double) training.size();
            double validationRate = rate(validation, draw -> containsMask(draw, candidate.mask()));
            double observedError = meanAbsoluteIndicatorError(validation, candidate.mask(), predictedRate, true);
            double randomError = meanAbsoluteIndicatorError(validation, candidate.mask(), baselineRate, true);
            List<Integer> numbers = numbers(candidate.mask());
            String typeKey = numbers.stream().map(n -> String.format(Locale.ROOT, "%02d", n))
                    .collect(java.util.stream.Collectors.joining("-"));
            String key = type + ":" + typeKey + ":fold=" + fold;
            String json = String.format(Locale.ROOT,
                    "{\"members\":%s,\"predictedValue\":%.6f,\"validationRate\":%.6f,\"trainingCount\":%d}",
                    numbers, predictedRate, validationRate, candidate.count());
            target.add(insight(runId, fold, training, validation, type, key,
                    "Coocorrência observada de " + numbers, predictedRate, observedError, randomError, json));
        }
    }

    private static String metricEvidence(Metric metric, double prediction, int fold) {
        String json = "{\"predictedValue\":" + round(prediction) + ",\"fold\":" + fold;
        if (metric.type.equals("NUMBER_BAND")) {
            String[] bounds = metric.key.substring("BAND_".length()).split("_");
            json += ",\"bandStart\":" + Integer.parseInt(bounds[0])
                    + ",\"bandEnd\":" + Integer.parseInt(bounds[1]);
        } else if (metric.type.equals("CONSECUTIVE_RUN_LENGTH")) {
            String length = metric.key.substring("RUN_LENGTH_".length()).replace("_PLUS", "");
            json += ",\"runLength\":" + Integer.parseInt(length);
        }
        return json + "}";
    }

    private static double temporalStability(List<Contest> training, int number, int window) {
        List<Double> rates = new ArrayList<>();
        for (int end = training.size(); end >= window; end -= window) {
            List<Contest> part = training.subList(end - window, end);
            rates.add(rate(part, draw -> draw.drawnNumbers().contains(number)));
        }
        if (rates.size() < 2) return 1.0;
        double mean = rates.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double variance = rates.stream().mapToDouble(value -> Math.pow(value - mean, 2)).average().orElse(0);
        return 1.0 / (1.0 + Math.sqrt(variance));
    }

    private void addConsecutiveRepeatEvidence(List<PatternInsight> target, long runId, int fold,
                                               List<Contest> training, List<Contest> validation) {
        if (training.size() < 2) return;
        double trainingMean = meanRepeatCount(training);
        double validationError = 0;
        double uniformError = 0;
        double uniformExpected = 9.0;
        for (int i = 0; i < validation.size(); i++) {
            Contest prior = i == 0 ? training.getLast() : validation.get(i - 1);
            if (validation.get(i).number() != prior.number() + 1) continue;
            int repeats = intersectionSize(prior, validation.get(i));
            validationError += Math.abs(repeats - trainingMean);
            uniformError += Math.abs(repeats - uniformExpected);
        }
        long consecutivePairs = 0;
        for (int i = 0; i < validation.size(); i++) {
            Contest prior = i == 0 ? training.getLast() : validation.get(i - 1);
            if (validation.get(i).number() == prior.number() + 1) consecutivePairs++;
        }
        validationError = consecutivePairs == 0 ? 0 : validationError / consecutivePairs;
        uniformError = consecutivePairs == 0 ? 0 : uniformError / consecutivePairs;
        String json = String.format(Locale.ROOT,
                "{\"predictedValue\":%.6f,\"uniformBaseline\":9,\"previousNumbers\":%s}",
                trainingMean, training.getLast().drawnNumbers());
        target.add(insight(runId, fold, training, validation, "CONSECUTIVE_DRAW_REPETITION",
                "CONSECUTIVE_DRAW_REPETITION:fold=" + fold,
                "Quantidade média de dezenas repetidas entre concursos consecutivos",
                trainingMean, validationError, uniformError, json));
    }

    private PatternInsight insight(long runId, int fold, List<Contest> training, List<Contest> validation,
                                   String type, String key, String label, double predicted,
                                   double observedError, double baselineError, String evidence) {
        String status = validation.size() < 3 ? "INSUFFICIENT_DATA"
                : observedError < baselineError ? "EXPERIMENTAL" : "NO_DEMONSTRATED_ADVANTAGE";
        String description = String.format(Locale.ROOT,
                "%s. Erro médio absoluto fora da amostra %.5f; referência uniforme/aleatória %.5f; "
                        + "treino %d–%d, validação %d–%d (%d concursos). Resultado retrospectivo, sem inferência de probabilidade futura.",
                label, observedError, baselineError, training.getFirst().number(), training.getLast().number(),
                validation.getFirst().number(), validation.getLast().number(), validation.size());
        return new PatternInsight(null, runId, key, ALGORITHM_VERSION, type, description,
                UNCALIBRATED_CONFIDENCE, training.getFirst().number(), training.getLast().number(),
                validation.getFirst().number(), validation.getLast().number(), validation.size(),
                decimal(observedError), decimal(baselineError), status, evidence);
    }

    private double randomBaselineError(ToDoubleFunction<Contest> metric, List<Contest> validation, long seed) {
        Random random = new Random(seed);
        double total = 0;
        long count = 0;
        for (Contest actual : validation) {
            for (int i = 0; i < RANDOM_BASELINE_SAMPLES_PER_CONTEST; i++) {
                List<Integer> pick = IntStream.rangeClosed(1, 25).boxed()
                        .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
                java.util.Collections.shuffle(pick, random);
                Contest randomDraw = new Contest(1, actual.drawDate(), pick.subList(0, 15));
                total += Math.abs(metric.applyAsDouble(actual) - metric.applyAsDouble(randomDraw));
                count++;
            }
        }
        return count == 0 ? 0 : total / count;
    }

    private static double meanAbsoluteError(List<Contest> draws, ToDoubleFunction<Contest> metric, double prediction) {
        return draws.stream().mapToDouble(draw -> Math.abs(metric.applyAsDouble(draw) - prediction)).average().orElse(0);
    }

    private static double meanAbsoluteIndicatorError(List<Contest> draws, int number, double prediction) {
        return draws.stream().mapToDouble(draw -> Math.abs((draw.drawnNumbers().contains(number) ? 1 : 0) - prediction))
                .average().orElse(0);
    }

    private static double meanAbsoluteIndicatorError(List<Contest> draws, int mask, double prediction, boolean maskValue) {
        return draws.stream().mapToDouble(draw -> Math.abs((containsMask(draw, mask) ? 1 : 0) - prediction))
                .average().orElse(0);
    }

    private static double rate(List<Contest> draws, java.util.function.Predicate<Contest> condition) {
        return draws.stream().filter(condition).count() / (double) draws.size();
    }

    private static double meanRepeatCount(List<Contest> draws) {
        return IntStream.range(1, draws.size())
                .filter(i -> draws.get(i).number() == draws.get(i - 1).number() + 1)
                .map(i -> intersectionSize(draws.get(i - 1), draws.get(i))).average().orElse(0);
    }

    private static int intersectionSize(Contest a, Contest b) {
        return (int) a.drawnNumbers().stream().filter(b.drawnNumbers()::contains).count();
    }

    private static boolean containsMask(Contest draw, int mask) {
        int drawMask = 0;
        for (int number : draw.drawnNumbers()) drawMask |= 1 << (number - 1);
        return (drawMask & mask) == mask;
    }

    private static List<Integer> numbers(int mask) {
        return IntStream.rangeClosed(1, 25).filter(number -> (mask & (1 << (number - 1))) != 0).boxed().toList();
    }

    private static long deterministicSeed(int first, int last, int fold) {
        return (((long) first << 32) ^ last) + fold * 104729L;
    }

    private static double round(double value) {
        return BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP).doubleValue();
    }

    private static BigDecimal decimal(double value) {
        return BigDecimal.valueOf(round(value)).setScale(6, RoundingMode.HALF_UP);
    }

    private record CombinationCount(int mask, long count) { }

    private enum Metric {
        ODD_COUNT("ODD_COUNT", "ODD_COUNT_MEAN", "Média de ímpares", draw ->
                draw.drawnNumbers().stream().filter(n -> n % 2 != 0).count()),
        DRAW_SUM("DRAW_SUM", "DRAW_SUM_MEAN", "Soma das dezenas", draw ->
                draw.drawnNumbers().stream().mapToInt(Integer::intValue).sum()),
        CONSECUTIVE_PAIRS("CONSECUTIVE_PAIRS", "CONSECUTIVE_PAIR_COUNT", "Pares consecutivos", draw -> {
            List<Integer> numbers = draw.drawnNumbers();
            int pairs = 0;
            for (int i = 1; i < numbers.size(); i++) if (numbers.get(i) == numbers.get(i - 1) + 1) pairs++;
            return pairs;
        }),
        BAND_01_05("NUMBER_BAND", "BAND_01_05", "Quantidade na faixa 1–5", band(1, 5)),
        BAND_06_10("NUMBER_BAND", "BAND_06_10", "Quantidade na faixa 6–10", band(6, 10)),
        BAND_11_15("NUMBER_BAND", "BAND_11_15", "Quantidade na faixa 11–15", band(11, 15)),
        BAND_16_20("NUMBER_BAND", "BAND_16_20", "Quantidade na faixa 16–20", band(16, 20)),
        BAND_21_25("NUMBER_BAND", "BAND_21_25", "Quantidade na faixa 21–25", band(21, 25)),
        RUNS_2("CONSECUTIVE_RUN_LENGTH", "RUN_LENGTH_2", "Sequências com pelo menos 2 dezenas", runLength(2)),
        RUNS_3("CONSECUTIVE_RUN_LENGTH", "RUN_LENGTH_3", "Sequências com pelo menos 3 dezenas", runLength(3)),
        RUNS_4("CONSECUTIVE_RUN_LENGTH", "RUN_LENGTH_4", "Sequências com pelo menos 4 dezenas", runLength(4)),
        RUNS_5("CONSECUTIVE_RUN_LENGTH", "RUN_LENGTH_5", "Sequências com pelo menos 5 dezenas", runLength(5)),
        RUNS_6("CONSECUTIVE_RUN_LENGTH", "RUN_LENGTH_6_PLUS", "Sequências com pelo menos 6 dezenas", runLength(6)),
        MAX_RUN("MAX_CONSECUTIVE_SEQUENCE", "MAX_SEQUENCE_LENGTH", "Comprimento da maior sequência", draw -> {
            int max = 1, current = 1;
            List<Integer> values = draw.drawnNumbers();
            for (int i = 1; i < values.size(); i++) {
                current = values.get(i) == values.get(i - 1) + 1 ? current + 1 : 1;
                max = Math.max(max, current);
            }
            return max;
        });

        private final String type;
        private final String key;
        private final String label;
        private final ToDoubleFunction<Contest> value;

        Metric(String type, String key, String label, ToDoubleFunction<Contest> value) {
            this.type = type;
            this.key = key;
            this.label = label;
            this.value = value;
        }

        private static ToDoubleFunction<Contest> band(int first, int last) {
            return draw -> draw.drawnNumbers().stream().filter(number -> number >= first && number <= last).count();
        }

        private static ToDoubleFunction<Contest> runLength(int minimum) {
            return draw -> {
                int total = 0, run = 1;
                List<Integer> numbers = draw.drawnNumbers();
                for (int i = 1; i <= numbers.size(); i++) {
                    if (i < numbers.size() && numbers.get(i) == numbers.get(i - 1) + 1) run++;
                    else {
                        if (run >= minimum) total++;
                        run = 1;
                    }
                }
                return total;
            };
        }
    }
}
