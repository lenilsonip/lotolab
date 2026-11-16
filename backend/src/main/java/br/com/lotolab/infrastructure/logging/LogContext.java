package br.com.lotolab.infrastructure.logging;

import org.slf4j.MDC;

import java.util.Map;
import java.util.UUID;

/** Thread-local MDC scope that restores the previous context when closed. */
public final class LogContext implements AutoCloseable {
    private final Map<String, String> previous;
    private boolean closed;

    private LogContext() {
        this.previous = MDC.getCopyOfContextMap();
    }

    public static LogContext request(String correlationId) {
        LogContext scope = new LogContext();
        MDC.clear();
        MDC.put("correlationId", correlationId);
        MDC.put("traceId", correlationId);
        MDC.put("component", "HTTP");
        MDC.put("operation", "request");
        return scope;
    }

    public static LogContext agentRun(String component, String operation, String origin) {
        LogContext scope = new LogContext();
        if (MDC.get("runId") == null) {
            MDC.put("runId", UUID.randomUUID().toString());
        }
        putIfPresent("component", component);
        putIfPresent("operation", operation);
        putIfPresent("origin", origin);
        return scope;
    }

    public static LogContext runId(String runId, String component, String operation, String origin) {
        LogContext scope = new LogContext();
        if (runId != null && !runId.isBlank()) {
            MDC.put("runId", runId);
        } else if (MDC.get("runId") == null) {
            MDC.put("runId", UUID.randomUUID().toString());
        }
        putIfPresent("component", component);
        putIfPresent("operation", operation);
        putIfPresent("origin", origin);
        return scope;
    }

    public static LogContext component(String component, String operation) {
        LogContext scope = new LogContext();
        MDC.remove("phase");
        putIfPresent("component", component);
        putIfPresent("operation", operation);
        return scope;
    }

    public static void phase(String phase) {
        if (phase == null || phase.isBlank()) {
            MDC.remove("phase");
        } else {
            MDC.put("phase", phase);
        }
    }

    public static String runId() {
        return MDC.get("runId");
    }

    public static String correlationId() {
        return MDC.get("correlationId");
    }

    private static void putIfPresent(String key, String value) {
        if (value != null && !value.isBlank()) {
            MDC.put(key, value);
        }
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        if (previous == null || previous.isEmpty()) {
            MDC.clear();
        } else {
            MDC.setContextMap(previous);
        }
    }
}
