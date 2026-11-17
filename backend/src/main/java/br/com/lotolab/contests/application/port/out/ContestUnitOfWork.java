package br.com.lotolab.contests.application.port.out;

import java.util.function.Supplier;

/** Executes one contest persistence operation in its own transaction. */
public interface ContestUnitOfWork {
    <T> T execute(Supplier<T> operation);
}
