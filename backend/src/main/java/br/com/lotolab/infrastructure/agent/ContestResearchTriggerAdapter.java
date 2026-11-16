package br.com.lotolab.infrastructure.agent;

import br.com.lotolab.contests.application.port.out.ContestResearchTrigger;
import br.com.lotolab.infrastructure.logging.LogContext;
import br.com.lotolab.research.application.port.in.MinePatternsCommand;
import br.com.lotolab.research.application.port.in.MinePatternsUseCase;
import br.com.lotolab.research.domain.PatternMiningAlgorithm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ContestResearchTriggerAdapter implements ContestResearchTrigger {
    private static final Logger LOGGER = LoggerFactory.getLogger(ContestResearchTriggerAdapter.class);
    private final MinePatternsUseCase miner;

    public ContestResearchTriggerAdapter(MinePatternsUseCase miner) {
        this.miner = miner;
    }

    @Override
    public boolean trigger(String origin) {
        try (LogContext ignored = LogContext.agentRun("Agent1", "pattern-mining", origin)) {
            LogContext.phase("STEP");
            LOGGER.info("origin={} trigger=after-contest-write algorithmVersion={}", origin,
                    PatternMiningAlgorithm.ALGORITHM_VERSION);
            try {
                miner.execute(new MinePatternsCommand(origin));
                LogContext.phase("SUCCESS");
                return true;
            } catch (RuntimeException exception) {
                LogContext.phase("ERROR");
                LOGGER.warn("origin={} cause={} stage=pattern-research-after-contest-write",
                        origin, exception.getClass().getSimpleName(), exception);
                return false;
            }
        }
    }
}
