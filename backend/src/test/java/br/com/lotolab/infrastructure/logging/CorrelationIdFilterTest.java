package br.com.lotolab.infrastructure.logging;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CorrelationIdFilterTest {
    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void acceptsSafeIncomingIdEchoesItAndRestoresPreviousMdc() throws Exception {
        MDC.put("outer", "preserved");
        var request = new MockHttpServletRequest("GET", "/api/concursos/42");
        request.setServletPath("/api/concursos/42");
        request.addHeader(CorrelationIdFilter.HEADER_NAME, "trace-42.a");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            assertThat(MDC.get("correlationId")).isEqualTo("trace-42.a");
            assertThat(MDC.get("traceId")).isEqualTo("trace-42.a");
            assertThat(MDC.get("component")).isEqualTo("HTTP");
            request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/concursos/{number}");
        });

        assertThat(response.getHeader(CorrelationIdFilter.HEADER_NAME)).isEqualTo("trace-42.a");
        assertThat(MDC.get("outer")).isEqualTo("preserved");
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void replacesUnsafeIncomingIdAndCleansMdcAfterFailure() {
        var request = new MockHttpServletRequest("POST", "/api/experimentos/analise");
        request.setServletPath("/api/experimentos/analise");
        request.addHeader(CorrelationIdFilter.HEADER_NAME, "unsafe id");
        var response = new MockHttpServletResponse();

        assertThatThrownBy(() -> filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            assertThat(MDC.get("correlationId")).matches("[0-9a-f-]{36}");
            response.setStatus(500);
            throw new ServletException("safe-test-error");
        })).isInstanceOf(ServletException.class);

        assertThat(response.getHeader(CorrelationIdFilter.HEADER_NAME)).matches("[0-9a-f-]{36}");
        assertThat(MDC.get("correlationId")).isNull();
        assertThat(MDC.get("traceId")).isNull();
        assertThat(MDC.get("component")).isNull();
    }

    @Test
    void generatesAndEchoesIdWhenHeaderIsMissing() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/concursos");
        request.setServletPath("/api/concursos");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) ->
                assertThat(MDC.get("correlationId")).matches("[0-9a-f-]{36}"));

        assertThat(response.getHeader(CorrelationIdFilter.HEADER_NAME)).matches("[0-9a-f-]{36}");
        assertThat(MDC.get("correlationId")).isNull();
    }
}
