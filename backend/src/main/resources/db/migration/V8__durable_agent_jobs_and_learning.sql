-- Durable narrative jobs plus a minimal, versioned online policy state.
-- The champion starts as the uniform baseline; promotion requires measured evidence.
ALTER TABLE agent_interpretation
    DROP CONSTRAINT ck_agent_interpretation_status;

ALTER TABLE agent_interpretation
    ADD CONSTRAINT ck_agent_interpretation_status CHECK (status IN (
        'PENDING', 'RUNNING', 'SUCCEEDED', 'SKIPPED', 'SKIPPED_DISABLED',
        'SKIPPED_NO_API_KEY', 'FAILED', 'INVALID_RESPONSE', 'CANCELLED'
    ));

CREATE TABLE agent_job (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    interpretation_id BIGINT NOT NULL UNIQUE REFERENCES agent_interpretation(id),
    job_type VARCHAR(40) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    correlation_id VARCHAR(80),
    run_id VARCHAR(80),
    origin VARCHAR(40) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    max_attempts INTEGER NOT NULL DEFAULT 4 CHECK (max_attempts BETWEEN 1 AND 8),
    next_attempt_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lease_owner VARCHAR(100),
    lease_until TIMESTAMP,
    error_category VARCHAR(80),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_agent_job_status CHECK (status IN ('PENDING','RUNNING','SUCCEEDED','FAILED','SKIPPED','CANCELLED')),
    CONSTRAINT ck_agent_job_type CHECK (job_type IN ('RESEARCH_INTERPRETATION','PREDICTION_INTERPRETATION'))
);
CREATE INDEX ix_agent_job_claim ON agent_job(status, next_attempt_at, lease_until, id);

CREATE TABLE model_version (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    model_key VARCHAR(80) NOT NULL,
    version INTEGER NOT NULL CHECK (version > 0),
    status VARCHAR(16) NOT NULL CHECK (status IN ('CHAMPION','CANDIDATE','REJECTED','ROLLED_BACK')),
    strategy_key VARCHAR(80) NOT NULL,
    parameters JSONB NOT NULL,
    training_first_contest INTEGER,
    training_last_contest INTEGER,
    sample_size INTEGER NOT NULL DEFAULT 0 CHECK (sample_size >= 0),
    mean_hits NUMERIC(8,5) NOT NULL DEFAULT 0,
    variance_hits NUMERIC(10,5) NOT NULL DEFAULT 0,
    decision_reason VARCHAR(160) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_model_version UNIQUE(model_key, version),
    CONSTRAINT ck_model_version_range CHECK (training_first_contest IS NULL OR training_last_contest >= training_first_contest)
);
CREATE UNIQUE INDEX uq_model_champion ON model_version(model_key) WHERE status = 'CHAMPION';

CREATE TABLE learning_event (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    attempt_id BIGINT NOT NULL UNIQUE REFERENCES prediction_attempt(id),
    actual_contest_number INTEGER NOT NULL REFERENCES lotofacil_contest(contest_number),
    model_version_before BIGINT NOT NULL REFERENCES model_version(id),
    model_version_after BIGINT REFERENCES model_version(id),
    observed_hits SMALLINT NOT NULL CHECK (observed_hits BETWEEN 0 AND 15),
    sample_size INTEGER NOT NULL CHECK (sample_size > 0),
    mean_hits_before NUMERIC(8,5) NOT NULL,
    mean_hits_after NUMERIC(8,5) NOT NULL,
    lower_95 NUMERIC(8,5) NOT NULL,
    upper_95 NUMERIC(8,5) NOT NULL,
    decision VARCHAR(16) NOT NULL CHECK (decision IN ('PROMOTED','REJECTED','RETAINED')),
    reason VARCHAR(160) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE prediction_attempt ADD COLUMN model_version_id BIGINT REFERENCES model_version(id);

INSERT INTO model_version(model_key, version, status, strategy_key, parameters,
    sample_size, mean_hits, variance_hits, decision_reason)
VALUES ('lotofacil-policy', 1, 'CHAMPION', 'UNIFORM',
    '{"minSamples":100,"expectedMeanHits":9.0,"confidenceZ":1.96}'::jsonb,
    0, 9.0, 0.0, 'Initial champion: uniform baseline pending sufficient prospective evidence');
