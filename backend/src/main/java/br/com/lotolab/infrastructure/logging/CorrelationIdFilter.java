package br.com.lotolab.infrastructure.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class CorrelationIdFilter extends OncePerRequestFilter {
    public static final String HEADER_NAME = "X-Correlation-ID";
    private static final Logger LOGGER = LoggerFactory.getLogger(CorrelationIdFilter.class);
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._-]{1,80}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String correlationId = validatedCorrelationId(request.getHeader(HEADER_NAME));
        long startedAt = System.nanoTime();
        String method = request.getMethod();
        String requestPath = safePath(request.getServletPath());
        String failure = null;

        try (LogContext ignored = LogContext.request(correlationId)) {
            response.setHeader(HEADER_NAME, correlationId);
            LogContext.phase("START");
            LOGGER.info("method={} route={}", method, requestPath);
            try {
                filterChain.doFilter(request, response);
            } catch (IOException | ServletException | RuntimeException exception) {
                failure = exception.getClass().getSimpleName();
                throw exception;
            } finally {
                String route = matchedRoute(request, requestPath);
                int status = response.getStatus();
                long durationMs = elapsedMs(startedAt);
                if (failure != null) {
                    LogContext.phase("ERROR");
                    LOGGER.error("method={} route={} status={} durationMs={} cause={}",
                            method, route, status, durationMs, failure);
                } else if (status >= 500) {
                    LogContext.phase("ERROR");
                    LOGGER.error("method={} route={} status={} durationMs={}",
                            method, route, status, durationMs);
                } else if (status >= 400) {
                    LogContext.phase("ERROR");
                    LOGGER.warn("method={} route={} status={} durationMs={}",
                            method, route, status, durationMs);
                } else {
                    LogContext.phase("SUCCESS");
                    LOGGER.info("method={} route={} status={} durationMs={}",
                            method, route, status, durationMs);
                }
            }
        }
    }

    private static String validatedCorrelationId(String candidate) {
        return candidate != null && SAFE_ID.matcher(candidate).matches()
                ? candidate : UUID.randomUUID().toString();
    }

    private static String matchedRoute(HttpServletRequest request, String fallback) {
        Object route = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        return safePath(route instanceof String value ? value : fallback);
    }

    private static String safePath(String path) {
        if (path == null || path.isBlank()) return "/";
        return path.length() <= 200 ? path : path.substring(0, 200);
    }

    private static long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }
}
