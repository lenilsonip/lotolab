package br.com.lotolab.research.application.port.out;

import br.com.lotolab.domain.PatternInsight;
import java.util.List;
import java.util.OptionalLong;

public interface PatternResearchRepository {
    OptionalLong startRun(String agentKey, String algorithmVersion, String idempotencyKey,
                          Integer sourceFirstContest, Integer sourceLastContest);
    void save(long runId, PatternInsight insight);
    void completeRun(long runId);
    List<PatternInsight> findLatestRunInsights();
    List<PatternInsight> findRunInsights(long runId);
}
