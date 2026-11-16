package br.com.lotolab.domain;

import java.time.Instant;

public record MlModelSummary(long id, int version, String status, String algorithmVersion,
                             String featureSchemaVersion, String checksum, int trainingCutoffExclusive,
                             Integer trainingFirstContest, Integer trainingLastContest,
                             int trainingContestCount, int trainingExampleCount, Instant createdAt) { }
