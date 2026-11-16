package br.com.lotolab.research.application.port.in;

import java.util.Objects;

public record MinePatternsCommand(String origin) {
    public MinePatternsCommand {
        origin = Objects.requireNonNull(origin, "origin is required").trim();
        if (origin.isEmpty() || origin.length() > 80) throw new IllegalArgumentException("origin is invalid");
    }
}
