package br.com.lotolab.application;

import br.com.lotolab.application.port.ContestRepository;
import br.com.lotolab.application.port.MachineLearningRepository;
import br.com.lotolab.domain.Contest;
import br.com.lotolab.domain.GameGenerationCriteria;
import br.com.lotolab.domain.LogisticModelArtifact;
import br.com.lotolab.domain.MlEvaluationSlice;
import br.com.lotolab.domain.MlModelSummary;
import br.com.lotolab.domain.MlTrainingJob;
import br.com.lotolab.domain.StoredMlModel;
import br.com.lotolab.domain.UniformLotofacilGameGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class MachineLearningService {
    public static final String ALGORITHM_VERSION = "logistic-regression-numberwise-v1";
    private final MachineLearningRepository mlRepository;
    private final ContestRepository contestRepository;
    private final NumberFeatureExtractor featureExtractor;
    private final LogisticRegressionTrainer trainer;
    private final TemporalMlEvaluationService evaluator;
    private final LogisticModelArtifactCodec codec;
    private final MlGameSelector selector;
    private final int epochs;
    private final double learningRate;
    private final double l2;
    private final long maxFitMillis;
    private final int minTrainingContests;

    public MachineLearningService(MachineLearningRepository mlRepository, ContestRepository contestRepository,
                                  NumberFeatureExtractor featureExtractor, LogisticRegressionTrainer trainer,
                                  TemporalMlEvaluationService evaluator, LogisticModelArtifactCodec codec,
                                  MlGameSelector selector,
                                  @Value("${lotolab.ml.epochs:200}") int epochs,
                                  @Value("${lotolab.ml.learning-rate:0.08}") double learningRate,
                                  @Value("${lotolab.ml.l2:0.10}") double l2,
                                  @Value("${lotolab.ml.max-fit-ms:20000}") long maxFitMillis,
                                  @Value("${lotolab.ml.min-training-contests:30}") int minTrainingContests) {
        this.mlRepository=Objects.requireNonNull(mlRepository); this.contestRepository=Objects.requireNonNull(contestRepository);
        this.featureExtractor=Objects.requireNonNull(featureExtractor); this.trainer=Objects.requireNonNull(trainer);
        this.evaluator=Objects.requireNonNull(evaluator); this.codec=Objects.requireNonNull(codec); this.selector=Objects.requireNonNull(selector);
        if (epochs < 1 || epochs > 500 || learningRate <= 0 || learningRate >= 1 || l2 < 0
                || maxFitMillis < 100 || minTrainingContests < 30) throw new IllegalArgumentException("Configuração ML inválida");
        this.epochs=epochs; this.learningRate=learningRate; this.l2=l2; this.maxFitMillis=maxFitMillis;
        this.minTrainingContests=minTrainingContests;
    }

    public MlTrainingJob enqueueTraining(Integer requestedCutoff, Long requestedSeed) {
        List<Contest> history = ordered(contestRepository.findAll());
        if (history.size() < Math.max(minTrainingContests, 34)) throw new IllegalArgumentException("Histórico insuficiente para treino, validação e holdout");
        int latest = history.getLast().number();
        int cutoff = requestedCutoff == null ? latest + 1 : requestedCutoff;
        if (cutoff <= 0 || cutoff > latest + 1) throw new IllegalArgumentException("Cutoff deve ser no máximo o próximo concurso conhecido");
        List<Contest> training = history.stream().filter(c -> c.number() < cutoff).toList();
        if (training.size() < Math.max(minTrainingContests, 34)) throw new IllegalArgumentException("Cutoff deixa histórico insuficiente para treino, validação e holdout");
        long seed = requestedSeed == null ? 20261112L : requestedSeed;
        String dataChecksum = TemporalMlEvaluationService.datasetChecksum(training, cutoff);
        String idempotencyKey = LogisticModelArtifactCodec.sha256(dataChecksum + "|" + cutoff + "|" + seed + "|"
                + epochs + "|" + learningRate + "|" + l2 + "|" + NumberFeatureExtractor.SCHEMA_VERSION);
        return mlRepository.enqueue(idempotencyKey, cutoff, dataChecksum, seed);
    }

    /** Claims one durable job, releases the claim transaction, computes in memory, then persists atomically. */
    public void processNextJob(String workerId) {
        Optional<MlTrainingJob> optional = mlRepository.claimNext(workerId, Instant.now().plusSeconds(300));
        if (optional.isEmpty()) return;
        MlTrainingJob job = optional.get();
        try {
            long started = System.nanoTime();
            List<Contest> all = ordered(contestRepository.findAll());
            List<Contest> training = all.stream().filter(c -> c.number() < job.trainingCutoffExclusive()).toList();
            if (training.size() < minTrainingContests
                    || !TemporalMlEvaluationService.datasetChecksum(training, job.trainingCutoffExclusive()).equals(job.datasetChecksum())) {
                mlRepository.failJob(job.id(), workerId, "DATASET_CHANGED_OR_INSUFFICIENT", null);
                return;
            }
            List<MlEvaluationSlice> evaluation = evaluator.evaluate(training, job.seed(), epochs, learningRate, l2, maxFitMillis);
            ensureWithinJobLimit(started);
            List<NumberFeatureExtractor.TrainingExample> examples = featureExtractor.examplesBefore(training, job.trainingCutoffExclusive());
            List<br.com.lotolab.domain.LogisticNumberModel> weights = trainer.fit(examples, epochs, learningRate, l2, maxFitMillis);
            ensureWithinJobLimit(started);
            LogisticModelArtifact artifact = new LogisticModelArtifact(1, NumberFeatureExtractor.SCHEMA_VERSION,
                    job.trainingCutoffExclusive(), training.getFirst().number(), training.getLast().number(),
                    training.size(), examples.size(), job.seed(), epochs, learningRate, l2, job.datasetChecksum(), weights);
            String checksum = codec.checksum(artifact);
            mlRepository.completeJob(job.id(), workerId, artifact, codec.serialize(artifact), checksum, evaluation);
        } catch (LogisticRegressionTrainer.TrainingTimeLimitException exception) {
            mlRepository.failJob(job.id(), workerId, "TRAINING_TIME_LIMIT", retryAt(job.attempts()));
        } catch (Exception exception) {
            mlRepository.failJob(job.id(), workerId, "TRAINING_FAILED", retryAt(job.attempts()));
        }
    }

    private void ensureWithinJobLimit(long started) {
        if ((System.nanoTime() - started) / 1_000_000L > maxFitMillis * 4) throw new LogisticRegressionTrainer.TrainingTimeLimitException();
    }
    private static Instant retryAt(int attempt) { return Instant.now().plusSeconds(Math.min(300, 5L << Math.min(attempt, 5))); }

    public Optional<MlTrainingJob> findJob(long id) { return mlRepository.findJob(id); }
    public List<MlModelSummary> listModels() { return mlRepository.listModels(); }
    public Optional<StoredMlModel> findModel(long id) { return mlRepository.findModel(id); }

    public ExperimentalPrediction generate(long modelId, Integer requestedTarget, Integer requestedEven,
                                           Integer requestedOdd, Long requestedSeed) {
        StoredMlModel model = mlRepository.findModel(modelId).orElseThrow(() -> new MlModelNotFoundException(modelId));
        List<Contest> history = ordered(contestRepository.findAll());
        int latest = history.isEmpty() ? 0 : history.getLast().number();
        int target = requestedTarget == null ? latest + 1 : requestedTarget;
        if (target <= latest) throw new IllegalArgumentException("O alvo deve ser posterior ao último resultado conhecido");
        if (target < model.artifact().trainingCutoffExclusive()) throw new IllegalArgumentException("O modelo contém dados posteriores ao alvo");
        int even = requestedEven == null ? 8 : requestedEven;
        int odd = requestedOdd == null ? 15 - even : requestedOdd;
        if (even + odd != 15 || even < 0 || odd < 0 || even > 12 || odd > 12) throw new IllegalArgumentException("Filtros de paridade incompatíveis");
        long seed = requestedSeed == null ? model.id() ^ target : requestedSeed;
        var selection = selector.select(model.artifact(), history, target, even, odd, seed);
        var baseline = new UniformLotofacilGameGenerator().generate(new GameGenerationCriteria(1, even, odd, seed)).getFirst();
        List<Double> scoreList = selection.scores().stream().map(MlGameSelector.ScoredNumber::score).toList();
        var saved = mlRepository.savePrediction(model.id(), model.version(), target,
                model.artifact().trainingCutoffExclusive(), selection.game().numbers(), scoreList, baseline.numbers(), seed);
        return new ExperimentalPrediction(saved, scoreList, "EXPERIMENTAL_RANKING_ONLY_NO_PRIZE_PROBABILITY_CLAIM");
    }

    private static List<Contest> ordered(List<Contest> contests) { return contests.stream().sorted(Comparator.comparingInt(Contest::number)).toList(); }

    public record ExperimentalPrediction(br.com.lotolab.domain.MlExperimentalPrediction prediction,
                                         List<Double> rankingScores, String notice) {
        public ExperimentalPrediction { rankingScores = List.copyOf(rankingScores); }
    }
    public static final class MlModelNotFoundException extends RuntimeException {
        public MlModelNotFoundException(long id) { super("Modelo experimental não encontrado: " + id); }
    }
}
