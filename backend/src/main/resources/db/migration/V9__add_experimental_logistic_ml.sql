CREATE TABLE ml_training_job (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    idempotency_key CHAR(64) NOT NULL UNIQUE,
    payload_version SMALLINT NOT NULL DEFAULT 1 CHECK (payload_version = 1),
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED')),
    training_cutoff_exclusive INTEGER NOT NULL CHECK (training_cutoff_exclusive > 0),
    dataset_checksum CHAR(64) NOT NULL CHECK (dataset_checksum ~ '^[0-9a-f]{64}$'),
    seed BIGINT NOT NULL,
    attempts SMALLINT NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    max_attempts SMALLINT NOT NULL DEFAULT 3 CHECK (max_attempts BETWEEN 1 AND 5),
    next_attempt_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lease_owner VARCHAR(100),
    lease_until TIMESTAMP,
    model_id BIGINT,
    error_category VARCHAR(80),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_ml_training_job_claim ON ml_training_job(status, next_attempt_at, lease_until, id);

CREATE TABLE ml_model_artifact (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    version INTEGER NOT NULL CHECK (version > 0),
    status VARCHAR(20) NOT NULL DEFAULT 'EXPERIMENTAL' CHECK (status = 'EXPERIMENTAL'),
    algorithm_version VARCHAR(80) NOT NULL,
    feature_schema_version VARCHAR(80) NOT NULL,
    training_cutoff_exclusive INTEGER NOT NULL CHECK (training_cutoff_exclusive > 0),
    training_first_contest INTEGER NOT NULL,
    training_last_contest INTEGER NOT NULL,
    training_contest_count INTEGER NOT NULL CHECK (training_contest_count >= 30),
    training_example_count INTEGER NOT NULL CHECK (training_example_count >= 750),
    seed BIGINT NOT NULL,
    epochs INTEGER NOT NULL CHECK (epochs BETWEEN 1 AND 500),
    learning_rate DOUBLE PRECISION NOT NULL CHECK (learning_rate > 0 AND learning_rate < 1),
    l2 DOUBLE PRECISION NOT NULL CHECK (l2 >= 0),
    training_data_checksum CHAR(64) NOT NULL CHECK (training_data_checksum ~ '^[0-9a-f]{64}$'),
    artifact_checksum CHAR(64) NOT NULL UNIQUE CHECK (artifact_checksum ~ '^[0-9a-f]{64}$'),
    artifact_json JSONB NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ml_model_version UNIQUE(version),
    CONSTRAINT ck_ml_model_time_range CHECK (training_first_contest <= training_last_contest
        AND training_last_contest < training_cutoff_exclusive)
);
ALTER TABLE ml_training_job ADD CONSTRAINT fk_ml_training_job_model
    FOREIGN KEY (model_id) REFERENCES ml_model_artifact(id);

CREATE TABLE ml_model_evaluation (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    model_id BIGINT NOT NULL REFERENCES ml_model_artifact(id),
    split VARCHAR(20) NOT NULL CHECK (split IN ('VALIDATION', 'HOLDOUT')),
    training_first_contest INTEGER NOT NULL,
    training_last_contest INTEGER NOT NULL,
    target_first_contest INTEGER NOT NULL,
    target_last_contest INTEGER NOT NULL,
    sample_count INTEGER NOT NULL CHECK (sample_count > 0),
    mean_model_hits DOUBLE PRECISION NOT NULL,
    mean_uniform_hits DOUBLE PRECISION NOT NULL,
    mean_paired_difference DOUBLE PRECISION NOT NULL,
    standard_error DOUBLE PRECISION NOT NULL CHECK (standard_error >= 0),
    confidence_95_lower DOUBLE PRECISION NOT NULL,
    confidence_95_upper DOUBLE PRECISION NOT NULL,
    model_hit_distribution JSONB NOT NULL,
    uniform_hit_distribution JSONB NOT NULL,
    seed BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ml_model_evaluation_split UNIQUE(model_id, split),
    CONSTRAINT ck_ml_evaluation_ranges CHECK (training_first_contest <= training_last_contest
        AND training_last_contest < target_first_contest AND target_first_contest <= target_last_contest)
);

CREATE TABLE ml_experimental_prediction (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    model_id BIGINT NOT NULL REFERENCES ml_model_artifact(id),
    model_version INTEGER NOT NULL,
    target_contest_number INTEGER NOT NULL CHECK (target_contest_number > 0),
    training_cutoff_exclusive INTEGER NOT NULL,
    predicted_numbers JSONB NOT NULL,
    ranking_scores JSONB NOT NULL,
    uniform_baseline_numbers JSONB NOT NULL,
    seed BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_ml_prediction_cutoff CHECK (target_contest_number >= training_cutoff_exclusive)
);
CREATE INDEX ix_ml_prediction_target ON ml_experimental_prediction(target_contest_number, model_id);
