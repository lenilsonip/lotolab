package br.com.lotolab.domain;

import java.time.Instant;
import java.util.List;

public record StoredMlModel(long id, int version, String status, String checksum,
                            LogisticModelArtifact artifact, List<MlEvaluationSlice> evaluations,
                            Instant createdAt) {
    public StoredMlModel { evaluations = List.copyOf(evaluations); }
}
