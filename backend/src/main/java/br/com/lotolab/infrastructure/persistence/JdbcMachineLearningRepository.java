package br.com.lotolab.infrastructure.persistence;

import br.com.lotolab.application.LogisticModelArtifactCodec;
import br.com.lotolab.application.port.MachineLearningRepository;
import br.com.lotolab.domain.LogisticModelArtifact;
import br.com.lotolab.domain.MlEvaluationSlice;
import br.com.lotolab.domain.MlExperimentalPrediction;
import br.com.lotolab.domain.MlModelSummary;
import br.com.lotolab.domain.MlTrainingJob;
import br.com.lotolab.domain.StoredMlModel;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class JdbcMachineLearningRepository implements MachineLearningRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final LogisticModelArtifactCodec codec;

    public JdbcMachineLearningRepository(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.json = Objects.requireNonNull(json);
        this.codec = new LogisticModelArtifactCodec(json);
    }

    @Override
    @Transactional
    public MlTrainingJob enqueue(String key, int cutoff, String datasetChecksum, long seed) {
        jdbc.update("""
                INSERT INTO ml_training_job(idempotency_key,training_cutoff_exclusive,dataset_checksum,seed)
                VALUES (?,?,?,?) ON CONFLICT(idempotency_key) DO NOTHING
                """, key, cutoff, datasetChecksum, seed);
        Long id = jdbc.queryForObject("SELECT id FROM ml_training_job WHERE idempotency_key=?", Long.class, key);
        return findJob(Objects.requireNonNull(id)).orElseThrow();
    }

    @Override
    @Transactional
    public Optional<MlTrainingJob> claimNext(String workerId, Instant leaseUntil) {
        jdbc.update("""
                UPDATE ml_training_job SET status='FAILED', error_category='LEASE_EXPIRED_MAX_ATTEMPTS', updated_at=CURRENT_TIMESTAMP
                WHERE status='RUNNING' AND lease_until <= CURRENT_TIMESTAMP AND attempts >= max_attempts
                """);
        List<MlTrainingJob> claimed = jdbc.query("""
                WITH candidate AS (
                    SELECT id FROM ml_training_job
                    WHERE attempts < max_attempts AND (
                        (status='PENDING' AND next_attempt_at <= CURRENT_TIMESTAMP)
                        OR (status='RUNNING' AND lease_until <= CURRENT_TIMESTAMP))
                    ORDER BY id FOR UPDATE SKIP LOCKED LIMIT 1
                )
                UPDATE ml_training_job j SET status='RUNNING', attempts=j.attempts+1, lease_owner=?,
                    lease_until=?, updated_at=CURRENT_TIMESTAMP, error_category=NULL
                FROM candidate c WHERE j.id=c.id
                RETURNING j.id,j.status,j.training_cutoff_exclusive,j.dataset_checksum,j.seed,j.attempts,
                    j.max_attempts,j.model_id,j.error_category,j.created_at,j.updated_at
                """, (rs, row) -> mapJob(rs), workerId, Timestamp.from(leaseUntil));
        return claimed.stream().findFirst();
    }

    @Override
    @Transactional
    public void failJob(long jobId, String workerId, String safeCategory, Instant retryAt) {
        jdbc.update("""
                UPDATE ml_training_job SET status=CASE WHEN attempts < max_attempts THEN 'PENDING' ELSE 'FAILED' END,
                    error_category=?, next_attempt_at=COALESCE(?,CURRENT_TIMESTAMP), lease_owner=NULL, lease_until=NULL,
                    updated_at=CURRENT_TIMESTAMP
                WHERE id=? AND status='RUNNING' AND lease_owner=?
                """, safeCategory, retryAt == null ? null : Timestamp.from(retryAt), jobId, workerId);
    }

    @Override
    @Transactional
    public StoredMlModel completeJob(long jobId, String workerId, LogisticModelArtifact artifact,
                                     String artifactJson, String artifactChecksum,
                                     List<MlEvaluationSlice> evaluations) {
        if (!codec.checksum(artifact).equals(artifactChecksum)) throw new IllegalArgumentException("Checksum inconsistente");
        if (!codec.serialize(artifact).equals(artifactJson)) throw new IllegalArgumentException("JSON do artefato não corresponde aos parâmetros treinados");
        jdbc.execute("SELECT pg_advisory_xact_lock(719240611)");
        Integer version = jdbc.queryForObject("SELECT COALESCE(MAX(version),0)+1 FROM ml_model_artifact", Integer.class);
        jdbc.update("""
                INSERT INTO ml_model_artifact(version,algorithm_version,feature_schema_version,training_cutoff_exclusive,
                    training_first_contest,training_last_contest,training_contest_count,training_example_count,seed,
                    epochs,learning_rate,l2,training_data_checksum,artifact_checksum,artifact_json)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?::jsonb) ON CONFLICT(artifact_checksum) DO NOTHING
                """, version, "logistic-regression-numberwise-v1", artifact.featureSchemaVersion(),
                artifact.trainingCutoffExclusive(), artifact.trainingFirstContest(), artifact.trainingLastContest(),
                artifact.trainingContestCount(), artifact.trainingExampleCount(), artifact.seed(), artifact.epochs(),
                artifact.learningRate(), artifact.l2(), artifact.datasetChecksum(), artifactChecksum, artifactJson);
        Long modelId = jdbc.queryForObject("SELECT id FROM ml_model_artifact WHERE artifact_checksum=?", Long.class, artifactChecksum);
        Integer storedVersion = jdbc.queryForObject("SELECT version FROM ml_model_artifact WHERE id=?", Integer.class, modelId);
        for (MlEvaluationSlice item : evaluations) {
            jdbc.update("""
                    INSERT INTO ml_model_evaluation(model_id,split,training_first_contest,training_last_contest,
                        target_first_contest,target_last_contest,sample_count,mean_model_hits,mean_uniform_hits,
                        mean_paired_difference,standard_error,confidence_95_lower,confidence_95_upper,
                        model_hit_distribution,uniform_hit_distribution,seed)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?::jsonb,?::jsonb,?) ON CONFLICT(model_id,split) DO NOTHING
                    """, modelId, item.split(), item.trainingFirstContest(), item.trainingLastContest(),
                    item.targetFirstContest(), item.targetLastContest(), item.sampleCount(), item.meanModelHits(),
                    item.meanUniformHits(), item.meanPairedDifference(), item.standardError(), item.confidence95Lower(),
                    item.confidence95Upper(), write(item.modelHitDistribution()), write(item.uniformHitDistribution()), item.seed());
        }
        int updated = jdbc.update("""
                UPDATE ml_training_job SET status='SUCCEEDED',model_id=?,lease_owner=NULL,lease_until=NULL,
                    error_category=NULL,updated_at=CURRENT_TIMESTAMP
                WHERE id=? AND status='RUNNING' AND lease_owner=? AND lease_until > CURRENT_TIMESTAMP
                """, modelId, jobId, workerId);
        if (updated != 1) throw new IllegalStateException("Lease de treinamento perdida antes da persistência final");
        return findModel(Objects.requireNonNull(modelId)).orElseThrow();
    }

    @Override public Optional<MlTrainingJob> findJob(long id) {
        return jdbc.query("""
                SELECT id,status,training_cutoff_exclusive,dataset_checksum,seed,attempts,max_attempts,model_id,
                    error_category,created_at,updated_at FROM ml_training_job WHERE id=?
                """, rs -> rs.next() ? Optional.of(mapJob(rs)) : Optional.empty(), id);
    }

    @Override public List<MlModelSummary> listModels() {
        return jdbc.query("""
                SELECT id,version,status,algorithm_version,feature_schema_version,artifact_checksum,
                    training_cutoff_exclusive,training_first_contest,training_last_contest,
                    training_contest_count,training_example_count,created_at
                FROM ml_model_artifact ORDER BY version DESC
                """, (rs, row) -> new MlModelSummary(rs.getLong("id"), rs.getInt("version"), rs.getString("status"),
                rs.getString("algorithm_version"), rs.getString("feature_schema_version"), rs.getString("artifact_checksum"),
                rs.getInt("training_cutoff_exclusive"), rs.getInt("training_first_contest"), rs.getInt("training_last_contest"),
                rs.getInt("training_contest_count"), rs.getInt("training_example_count"), instant(rs, "created_at")));
    }

    @Override public Optional<StoredMlModel> findModel(long id) {
        List<StoredMlModel> models = jdbc.query("""
                SELECT id,version,status,algorithm_version,feature_schema_version,training_cutoff_exclusive,
                    training_first_contest,training_last_contest,training_contest_count,training_example_count,
                    seed,epochs,learning_rate,l2,training_data_checksum,artifact_checksum,
                    artifact_json::text AS artifact_json,created_at
                FROM ml_model_artifact WHERE id=?
                """, (rs, row) -> {
            String checksum = rs.getString("artifact_checksum");
            LogisticModelArtifact artifact = codec.deserializeAndVerify(rs.getString("artifact_json"), checksum);
            if (!"logistic-regression-numberwise-v1".equals(rs.getString("algorithm_version"))
                    || !artifact.featureSchemaVersion().equals(rs.getString("feature_schema_version"))
                    || artifact.trainingCutoffExclusive() != rs.getInt("training_cutoff_exclusive")
                    || artifact.trainingFirstContest() != rs.getInt("training_first_contest")
                    || artifact.trainingLastContest() != rs.getInt("training_last_contest")
                    || artifact.trainingContestCount() != rs.getInt("training_contest_count")
                    || artifact.trainingExampleCount() != rs.getInt("training_example_count")
                    || artifact.seed() != rs.getLong("seed") || artifact.epochs() != rs.getInt("epochs")
                    || Double.compare(artifact.learningRate(), rs.getDouble("learning_rate")) != 0
                    || Double.compare(artifact.l2(), rs.getDouble("l2")) != 0
                    || !artifact.datasetChecksum().equals(rs.getString("training_data_checksum").trim())) {
                throw new IllegalArgumentException("Metadados persistidos não correspondem ao artefato ML");
            }
            return new StoredMlModel(rs.getLong("id"), rs.getInt("version"), rs.getString("status"), checksum,
                    artifact, findEvaluations(rs.getLong("id")), instant(rs, "created_at"));
        }, id);
        return models.stream().findFirst();
    }

    @Override @Transactional
    public MlExperimentalPrediction savePrediction(long modelId, int modelVersion, int targetContest,
                                                  int cutoffExclusive, List<Integer> numbers,
                                                  List<Double> rankingScores, List<Integer> baselineNumbers, long seed) {
        new br.com.lotolab.domain.LotofacilGame(numbers);
        new br.com.lotolab.domain.LotofacilGame(baselineNumbers);
        if (rankingScores.size() != 25 || rankingScores.stream().anyMatch(v -> v == null || !Double.isFinite(v))) {
            throw new IllegalArgumentException("Scores do ranking inválidos");
        }
        Long id = jdbc.queryForObject("""
                INSERT INTO ml_experimental_prediction(model_id,model_version,target_contest_number,training_cutoff_exclusive,
                    predicted_numbers,ranking_scores,uniform_baseline_numbers,seed)
                VALUES (?,?,?,?,?::jsonb,?::jsonb,?::jsonb,?) RETURNING id
                """, Long.class, modelId, modelVersion, targetContest, cutoffExclusive,
                write(numbers), write(rankingScores), write(baselineNumbers), seed);
        return new MlExperimentalPrediction(Objects.requireNonNull(id), modelId, modelVersion, targetContest,
                cutoffExclusive, numbers, baselineNumbers, seed, Instant.now());
    }

    private List<MlEvaluationSlice> findEvaluations(long modelId) {
        return jdbc.query("""
                SELECT split,training_first_contest,training_last_contest,target_first_contest,target_last_contest,
                    sample_count,mean_model_hits,mean_uniform_hits,mean_paired_difference,standard_error,
                    confidence_95_lower,confidence_95_upper,model_hit_distribution::text,uniform_hit_distribution::text,seed
                FROM ml_model_evaluation WHERE model_id=? ORDER BY CASE split WHEN 'VALIDATION' THEN 1 ELSE 2 END
                """, (rs, row) -> new MlEvaluationSlice(rs.getString(1),rs.getInt(2),rs.getInt(3),rs.getInt(4),rs.getInt(5),
                rs.getInt(6),rs.getDouble(7),rs.getDouble(8),rs.getDouble(9),rs.getDouble(10),rs.getDouble(11),rs.getDouble(12),
                readInts(rs.getString(13)),readInts(rs.getString(14)),rs.getLong(15)), modelId);
    }

    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalStateException("Falha ao serializar dado ML", exception); }
    }
    private List<Integer> readInts(String value) {
        try { return json.readValue(value, new TypeReference<>() { }); }
        catch (Exception exception) { throw new IllegalArgumentException("Métrica ML persistida inválida", exception); }
    }
    private static MlTrainingJob mapJob(ResultSet rs) throws SQLException {
        return new MlTrainingJob(rs.getLong("id"),rs.getString("status"),rs.getInt("training_cutoff_exclusive"),
                rs.getString("dataset_checksum").trim(),rs.getLong("seed"),rs.getInt("attempts"),rs.getInt("max_attempts"),
                (Long) rs.getObject("model_id"),rs.getString("error_category"),instant(rs,"created_at"),instant(rs,"updated_at"));
    }
    private static Instant instant(ResultSet rs, String column) throws SQLException { return rs.getTimestamp(column).toInstant(); }
}
