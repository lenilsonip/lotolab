package br.com.lotolab.api;

import br.com.lotolab.domain.MlModelSummary;

import java.time.Instant;

public record MlModelSummaryResponse(long id, int version, String status, String algorithmVersion,
                                    String featureSchemaVersion, String checksum, int trainingCutoffExclusive,
                                    Integer trainingFirstContest, Integer trainingLastContest,
                                    int trainingContestCount, int trainingExampleCount, Instant createdAt) {
    public static MlModelSummaryResponse from(MlModelSummary value) {
        return new MlModelSummaryResponse(value.id(), value.version(), value.status(), value.algorithmVersion(),
                value.featureSchemaVersion(), value.checksum(), value.trainingCutoffExclusive(),
                value.trainingFirstContest(), value.trainingLastContest(), value.trainingContestCount(),
                value.trainingExampleCount(), value.createdAt());
    }
}
