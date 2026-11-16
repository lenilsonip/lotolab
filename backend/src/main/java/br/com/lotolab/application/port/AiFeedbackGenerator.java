package br.com.lotolab.application.port;

public interface AiFeedbackGenerator {
    String generateFeedback(String promptText);

    default boolean isAvailable() { return true; }
    default String modelName() { return "configured-model"; }
}
