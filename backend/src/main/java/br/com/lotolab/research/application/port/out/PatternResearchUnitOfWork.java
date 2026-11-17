package br.com.lotolab.research.application.port.out;

import java.util.function.Supplier;

public interface PatternResearchUnitOfWork { <T> T execute(Supplier<T> work); }
