package br.com.lotolab.research.adapter.in.web;

import br.com.lotolab.domain.PatternInsight;

import java.math.BigDecimal;

public record PatternInsightResponse(long id, String hypothesisKey, String algorithmVersion, String patternType,
                                     String description, BigDecimal confidenceScore, Integer trainingFirstContest,
                                     Integer trainingLastContest, Integer validationFirstContest,
                                     Integer validationLastContest, Integer sampleSize, BigDecimal observedMetric,
                                     BigDecimal baselineMetric, String evidenceStatus, String evidenceJson) {
    static PatternInsightResponse from(PatternInsight insight) {
        return new PatternInsightResponse(insight.id() == null ? 0 : insight.id(), insight.hypothesisKey(),
                insight.algorithmVersion(), insight.patternType(), insight.description(), insight.confidenceScore(),
                insight.trainingFirstContest(), insight.trainingLastContest(), insight.validationFirstContest(),
                insight.validationLastContest(), insight.sampleSize(), insight.observedMetric(),
                insight.baselineMetric(), insight.evidenceStatus(), insight.evidenceJson());
    }
}
