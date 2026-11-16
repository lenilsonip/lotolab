package br.com.lotolab.infrastructure.persistence;

import br.com.lotolab.contests.application.ContestResultPersistedEvent;
import br.com.lotolab.contests.application.port.out.ContestResultEventPublisher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.Objects;

/** Spring event adapter. Transactional listeners consume the event before the enclosing commit. */
@Component
public class SpringContestResultEventPublisher implements ContestResultEventPublisher {
    private final ApplicationEventPublisher publisher;

    public SpringContestResultEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = Objects.requireNonNull(publisher);
    }

    @Override
    public void publish(ContestResultPersistedEvent event) {
        publisher.publishEvent(Objects.requireNonNull(event));
    }
}
