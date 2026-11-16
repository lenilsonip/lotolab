package br.com.lotolab.infrastructure.agent;

import br.com.lotolab.application.MachineLearningService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Dedicated durable ML job poller. Model fitting is never performed on the HTTP request thread. */
@Component
@ConditionalOnProperty(name = "lotolab.ml.worker.enabled", havingValue = "true", matchIfMissing = true)
public class MlTrainingJobWorker {
    private static final Logger LOGGER = LoggerFactory.getLogger(MlTrainingJobWorker.class);
    private final MachineLearningService service;
    private final String workerId = "lotolab-ml-" + UUID.randomUUID();

    public MlTrainingJobWorker(MachineLearningService service) { this.service = service; }

    @Scheduled(fixedDelayString = "${lotolab.ml.worker-delay-ms:1000}")
    public void poll() {
        try { service.processNextJob(workerId); }
        catch (RuntimeException exception) {
            LOGGER.error("ML training worker poll failed; category={}", exception.getClass().getSimpleName());
        }
    }
}
