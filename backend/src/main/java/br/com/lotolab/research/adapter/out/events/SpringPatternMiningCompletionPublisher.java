package br.com.lotolab.research.adapter.out.events;

import br.com.lotolab.application.PatternMiningCompletedEvent;
import br.com.lotolab.infrastructure.logging.LogContext;
import br.com.lotolab.research.application.port.out.PatternMiningCompletionPublisher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.Objects;

/** Publishes the legacy Spring integration event while keeping Spring and correlation concerns outside application. */
@Component
public final class SpringPatternMiningCompletionPublisher implements PatternMiningCompletionPublisher {
    private final ApplicationEventPublisher publisher;
    public SpringPatternMiningCompletionPublisher(ApplicationEventPublisher publisher) {
        this.publisher = Objects.requireNonNull(publisher);
    }
    @Override public void publish(long runId, String origin) {
        publisher.publishEvent(new PatternMiningCompletedEvent(runId, LogContext.runId(), origin));
    }
}
