package br.com.lotolab.contests.application.port.out;

import br.com.lotolab.contests.application.ContestResultPersistedEvent;

/** Publishes the result event inside the current unit of work. */
public interface ContestResultEventPublisher {
    void publish(ContestResultPersistedEvent event);
}
