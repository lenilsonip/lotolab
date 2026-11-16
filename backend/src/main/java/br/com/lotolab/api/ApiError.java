package br.com.lotolab.api;

import java.util.List;

public record ApiError(String code, String message, List<String> details) {
    public ApiError {
        details = List.copyOf(details);
    }
}
