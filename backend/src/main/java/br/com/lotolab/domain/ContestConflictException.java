package br.com.lotolab.domain;

public class ContestConflictException extends RuntimeException {

    public ContestConflictException(int contestNumber) {
        super("Contest " + contestNumber + " already exists with different data");
    }
}
