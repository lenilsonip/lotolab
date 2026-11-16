CREATE TABLE agent_interpretation (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    agent_run_id BIGINT UNIQUE REFERENCES agent_run (id),
    prediction_attempt_id BIGINT UNIQUE REFERENCES prediction_attempt (id),
    agent_key VARCHAR(80) NOT NULL,
    provider VARCHAR(40) NOT NULL DEFAULT 'GOOGLE_GEMINI',
    model VARCHAR(80) NOT NULL,
    prompt_version VARCHAR(40) NOT NULL,
    status VARCHAR(32) NOT NULL,
    summary TEXT,
    hypotheses JSONB NOT NULL DEFAULT '[]'::jsonb,
    explanation TEXT,
    statistical_input JSONB NOT NULL DEFAULT '{}'::jsonb,
    error_category VARCHAR(80),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_agent_interpretation_parent CHECK (
        (agent_run_id IS NOT NULL AND prediction_attempt_id IS NULL)
        OR (agent_run_id IS NULL AND prediction_attempt_id IS NOT NULL)
    ),
    CONSTRAINT ck_agent_interpretation_status CHECK (status IN (
        'SUCCEEDED', 'SKIPPED_NO_API_KEY', 'FAILED', 'INVALID_RESPONSE'
    ))
);

CREATE INDEX ix_agent_interpretation_created_at
    ON agent_interpretation (created_at DESC);
