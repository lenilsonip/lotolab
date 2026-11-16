package br.com.lotolab.research.adapter.in.scheduler;

import br.com.lotolab.infrastructure.logging.LogContext;
import br.com.lotolab.research.application.port.in.MinePatternsCommand;
import br.com.lotolab.research.application.port.in.MinePatternsUseCase;
import br.com.lotolab.research.domain.PatternMiningAlgorithm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class PatternMinerAgent {
    private static final Logger LOGGER = LoggerFactory.getLogger(PatternMinerAgent.class);
    private final MinePatternsUseCase mining;

    public PatternMinerAgent(MinePatternsUseCase mining) { this.mining = Objects.requireNonNull(mining); }

    @Scheduled(cron = "${lotolab.pattern-miner.cron:0 0 3 * * *}",
            zone = "${lotolab.pattern-miner.zone:America/Sao_Paulo}")
    public void minePatterns() {
        long startedAt = System.nanoTime();
        try (LogContext ignored = LogContext.agentRun("Agent1", "scheduled-trigger", "SCHEDULED")) {
            LogContext.phase("START");
            LOGGER.info("trigger=scheduled algorithmVersion={}", PatternMiningAlgorithm.ALGORITHM_VERSION);
            try {
                var result = mining.execute(new MinePatternsCommand("SCHEDULED"));
                LogContext.phase("SUCCESS");
                LOGGER.info("outcome={} agentRunId={} contestCount={} folds={} insightsWritten={} durationMs={}",
                        result.outcome(), result.runId(), result.contestCount(), result.foldCount(),
                        result.insightsWritten(), (System.nanoTime() - startedAt) / 1_000_000L);
            } catch (RuntimeException | Error exception) {
                LogContext.phase("ERROR");
                LOGGER.error("cause={} durationMs={}", exception.getClass().getSimpleName(),
                        (System.nanoTime() - startedAt) / 1_000_000L, exception);
                throw exception;
            }
        }
    }
}
