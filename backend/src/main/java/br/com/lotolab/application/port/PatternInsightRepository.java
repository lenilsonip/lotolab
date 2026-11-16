package br.com.lotolab.application.port;

import br.com.lotolab.domain.PatternInsight;

import java.util.List;
import java.util.OptionalLong;

public interface PatternInsightRepository {
    void save(PatternInsight insight);
    OptionalLong startRun(String agentKey, String algorithmVersion, String idempotencyKey,
                          Integer sourceFirstContest, Integer sourceLastContest);
    void save(long runId, PatternInsight insight);
    void completeRun(long runId);
    List<PatternInsight> findEligible(int targetContestNumber);
    List<PatternInsight> findLatestRunInsights();
    List<PatternInsight> findRunInsights(long runId);
    List<PatternInsight> findByIds(List<Long> ids);
}
