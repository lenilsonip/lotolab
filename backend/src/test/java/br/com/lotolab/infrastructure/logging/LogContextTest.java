package br.com.lotolab.infrastructure.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

class LogContextTest {
    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void nestedAgentAndComponentScopesReuseRunIdAndRestoreOuterContext() {
        try (LogContext agent = LogContext.agentRun("Agent2", "generate", "MANUAL")) {
            String runId = LogContext.runId();
            assertThat(runId).isNotBlank();
            assertThat(MDC.get("origin")).isEqualTo("MANUAL");

            try (LogContext repository = LogContext.component("PredictionRepository", "save-generated")) {
                assertThat(LogContext.runId()).isEqualTo(runId);
                assertThat(MDC.get("component")).isEqualTo("PredictionRepository");
            }

            assertThat(LogContext.runId()).isEqualTo(runId);
            assertThat(MDC.get("component")).isEqualTo("Agent2");
        }

        assertThat(MDC.get("runId")).isNull();
        assertThat(MDC.get("origin")).isNull();
    }

    @Test
    void reattachesRunIdForAfterCommitWork() {
        try (LogContext ignored = LogContext.runId("stored-run-id", "Gemini", "interpretation", "MANUAL")) {
            assertThat(LogContext.runId()).isEqualTo("stored-run-id");
            assertThat(MDC.get("component")).isEqualTo("Gemini");
        }
        assertThat(LogContext.runId()).isNull();
    }

    @Test
    void separateExecutionsReceiveDifferentRunIdsAndDoNotLeakOrigin() {
        String firstRun;
        try (LogContext ignored = LogContext.agentRun("Agent1", "mine", "MANUAL")) {
            firstRun = LogContext.runId();
        }
        try (LogContext ignored = LogContext.agentRun("Agent1", "mine", "SCHEDULED")) {
            assertThat(LogContext.runId()).isNotEqualTo(firstRun);
            assertThat(MDC.get("origin")).isEqualTo("SCHEDULED");
        }
        assertThat(MDC.get("runId")).isNull();
        assertThat(MDC.get("origin")).isNull();
    }

    @Test
    void childComponentDoesNotInheritAnIncorrectParentPhase() {
        try (LogContext request = LogContext.request("request-1")) {
            LogContext.phase("START");
            try (LogContext component = LogContext.component("Backtesting", "load-history")) {
                assertThat(MDC.get("phase")).isNull();
                LogContext.phase("SUCCESS");
                assertThat(MDC.get("phase")).isEqualTo("SUCCESS");
            }
            assertThat(MDC.get("phase")).isEqualTo("START");
        }
        assertThat(MDC.get("phase")).isNull();
    }
}
