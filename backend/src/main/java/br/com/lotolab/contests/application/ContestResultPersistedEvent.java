package br.com.lotolab.contests.application;

/** Raised for a newly stored official result, inside the transaction that stores it. */
public record ContestResultPersistedEvent(int contestNumber) { }
