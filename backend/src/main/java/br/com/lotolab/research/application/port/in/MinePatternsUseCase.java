package br.com.lotolab.research.application.port.in;

public interface MinePatternsUseCase {
    PatternMiningResult execute(MinePatternsCommand command);
}
