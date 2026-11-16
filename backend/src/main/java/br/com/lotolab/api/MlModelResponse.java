package br.com.lotolab.api;

import br.com.lotolab.domain.LogisticModelArtifact;
import br.com.lotolab.domain.MlEvaluationSlice;
import br.com.lotolab.domain.StoredMlModel;

import java.time.Instant;
import java.util.List;

public record MlModelResponse(long id, int version, String status, String checksum,
                              LogisticModelArtifact artifact, List<MlEvaluationSlice> evaluations,
                              Instant createdAt) {
    public MlModelResponse { evaluations = List.copyOf(evaluations); }
    public static MlModelResponse from(StoredMlModel value) {
        return new MlModelResponse(value.id(), value.version(), value.status(), value.checksum(),
                value.artifact(), value.evaluations(), value.createdAt());
    }
}
