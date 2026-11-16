package br.com.lotolab.infrastructure.agent;

import br.com.lotolab.application.AgentInterpretationService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Polls the durable outbox. A lease allows another instance to recover work after a restart. */
@Component
@ConditionalOnProperty(name = "lotolab.agents.worker.enabled", havingValue = "true", matchIfMissing = true)
public class AgentJobWorker {
    private final AgentInterpretationService service;
    private final String workerId = "lotolab-" + UUID.randomUUID();

    public AgentJobWorker(AgentInterpretationService service) { this.service = service; }

    @Scheduled(fixedDelayString = "${lotolab.agents.worker-delay-ms:2000}")
    public void poll() { service.processNextJob(workerId); }
}
