package br.com.lotolab.application;

public class PersistedDataInconsistencyException extends RuntimeException {

    public PersistedDataInconsistencyException(int contestNumber, RuntimeException cause) {
        super("Persisted data for contest " + contestNumber + " violates domain invariants", cause);
    }
}
