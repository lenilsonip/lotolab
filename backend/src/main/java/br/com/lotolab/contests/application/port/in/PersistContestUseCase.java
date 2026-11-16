package br.com.lotolab.contests.application.port.in;

import br.com.lotolab.domain.Contest;

/** Persists a contest result, evaluates pending attempts and publishes its transactional event. */
public interface PersistContestUseCase {
    boolean persist(Contest contest);
}
