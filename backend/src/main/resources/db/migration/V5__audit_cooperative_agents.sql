CREATE TABLE agent_run (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    agent_key VARCHAR(80) NOT NULL,
    algorithm_version VARCHAR(40) NOT NULL,
    idempotency_key VARCHAR(160) NOT NULL UNIQUE,
    source_first_contest INTEGER,
    source_last_contest INTEGER,
    status VARCHAR(24) NOT NULL,
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    configuration JSONB NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT ck_agent_run_status CHECK (status IN ('RUNNING', 'COMPLETED', 'FAILED')),
    CONSTRAINT ck_agent_run_range CHECK (source_first_contest IS NULL OR source_first_contest > 0),
    CONSTRAINT ck_agent_run_last CHECK (source_last_contest IS NULL OR source_last_contest > 0)
);

ALTER TABLE pattern_insight
    ADD COLUMN agent_run_id BIGINT REFERENCES agent_run (id),
    ADD COLUMN hypothesis_key VARCHAR(120),
    ADD COLUMN algorithm_version VARCHAR(40),
    ADD COLUMN training_first_contest INTEGER,
    ADD COLUMN training_last_contest INTEGER,
    ADD COLUMN validation_first_contest INTEGER,
    ADD COLUMN validation_last_contest INTEGER,
    ADD COLUMN sample_size INTEGER,
    ADD COLUMN observed_metric NUMERIC(16, 6),
    ADD COLUMN baseline_metric NUMERIC(16, 6),
    ADD COLUMN evidence_status VARCHAR(40) NOT NULL DEFAULT 'LEGACY_UNASSESSED',
    ADD COLUMN evidence JSONB NOT NULL DEFAULT '{}'::jsonb;

ALTER TABLE pattern_insight
    ADD CONSTRAINT ck_pattern_insight_evidence_status CHECK (evidence_status IN (
        'LEGACY_UNASSESSED', 'EXPERIMENTAL', 'NO_DEMONSTRATED_ADVANTAGE', 'REJECTED', 'INSUFFICIENT_DATA'
    )),
    ADD CONSTRAINT ck_pattern_insight_sample_size CHECK (sample_size IS NULL OR sample_size >= 0),
    ADD CONSTRAINT uq_pattern_insight_version UNIQUE (agent_run_id, hypothesis_key);

CREATE INDEX ix_pattern_insight_eligible
    ON pattern_insight (evidence_status, pattern_type, created_at DESC);

CREATE TABLE strategy_evaluation (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    strategy_key VARCHAR(100) NOT NULL,
    strategy_version VARCHAR(40) NOT NULL,
    target_contest_number INTEGER NOT NULL CHECK (target_contest_number > 0),
    source_cutoff_contest INTEGER NOT NULL CHECK (source_cutoff_contest >= 0),
    random_seed BIGINT,
    candidate_budget INTEGER NOT NULL CHECK (candidate_budget > 0),
    filters JSONB NOT NULL DEFAULT '{}'::jsonb,
    evidence_status VARCHAR(40) NOT NULL,
    performance_status VARCHAR(40) NOT NULL DEFAULT 'NOT_EVALUATED',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_strategy_evaluation_evidence CHECK (evidence_status IN (
        'EVIDENCE_USED', 'NO_ELIGIBLE_EVIDENCE'
    )),
    CONSTRAINT ck_strategy_evaluation_performance CHECK (performance_status IN (
        'NOT_EVALUATED', 'SCORE_EXCEEDS_BASELINE', 'NO_DEMONSTRATED_ADVANTAGE'
    ))
);

CREATE TABLE strategy_evaluation_fold (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    evaluation_id BIGINT NOT NULL REFERENCES strategy_evaluation (id),
    fold_number INTEGER NOT NULL CHECK (fold_number > 0),
    training_first_contest INTEGER NOT NULL,
    training_last_contest INTEGER NOT NULL,
    validation_first_contest INTEGER NOT NULL,
    validation_last_contest INTEGER NOT NULL,
    training_sample_size INTEGER NOT NULL CHECK (training_sample_size > 0),
    validation_sample_size INTEGER NOT NULL CHECK (validation_sample_size > 0),
    optimized_score BIGINT NOT NULL CHECK (optimized_score >= 0),
    random_baseline_score BIGINT NOT NULL CHECK (random_baseline_score >= 0),
    random_seed BIGINT NOT NULL,
    candidate_budget INTEGER NOT NULL CHECK (candidate_budget > 0),
    CONSTRAINT uq_strategy_evaluation_fold UNIQUE (evaluation_id, fold_number),
    CONSTRAINT ck_strategy_fold_time CHECK (
        training_first_contest <= training_last_contest
        AND training_last_contest < validation_first_contest
        AND validation_first_contest <= validation_last_contest
    )
);

CREATE TABLE strategy_evaluation_evidence (
    evaluation_id BIGINT NOT NULL REFERENCES strategy_evaluation (id),
    pattern_insight_id BIGINT NOT NULL REFERENCES pattern_insight (id),
    PRIMARY KEY (evaluation_id, pattern_insight_id)
);

ALTER TABLE prediction_attempt
    ADD COLUMN strategy_evaluation_id BIGINT REFERENCES strategy_evaluation (id),
    ADD COLUMN source VARCHAR(24) NOT NULL DEFAULT 'LEGACY',
    ADD COLUMN is_simulation BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN target_was_overridden BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN strategy_version VARCHAR(40),
    ADD COLUMN experimental_score BIGINT,
    ADD COLUMN baseline_score BIGINT,
    ADD COLUMN evidence_status VARCHAR(40) NOT NULL DEFAULT 'NOT_EVALUATED',
    ADD COLUMN performance_status VARCHAR(40) NOT NULL DEFAULT 'NOT_EVALUATED',
    ADD COLUMN evidence_ids JSONB NOT NULL DEFAULT '[]'::jsonb;

ALTER TABLE prediction_attempt
    ADD CONSTRAINT ck_prediction_attempt_source CHECK (source IN (
        'LEGACY', 'MANUAL', 'RANDOM', 'OPTIMIZED', 'BACKTEST'
    )),
    ADD CONSTRAINT ck_prediction_attempt_score CHECK (experimental_score IS NULL OR experimental_score >= 0),
    ADD CONSTRAINT ck_prediction_attempt_baseline CHECK (baseline_score IS NULL OR baseline_score >= 0),
    ADD CONSTRAINT ck_prediction_attempt_evidence CHECK (evidence_status IN (
        'NOT_EVALUATED', 'EVIDENCE_USED', 'NO_ELIGIBLE_EVIDENCE'
    )),
    ADD CONSTRAINT ck_prediction_attempt_performance CHECK (performance_status IN (
        'NOT_EVALUATED', 'SCORE_EXCEEDS_BASELINE', 'NO_DEMONSTRATED_ADVANTAGE'
    ));

CREATE TABLE prediction_evaluation (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    attempt_id BIGINT NOT NULL UNIQUE REFERENCES prediction_attempt (id),
    actual_contest_number INTEGER NOT NULL REFERENCES lotofacil_contest (contest_number),
    hit_count SMALLINT NOT NULL CHECK (hit_count BETWEEN 0 AND 15),
    measured_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_prediction_evaluation_target CHECK (actual_contest_number > 0)
);

CREATE INDEX ix_prediction_attempt_target_source
    ON prediction_attempt (target_contest_number, source, id);
CREATE INDEX ix_prediction_evaluation_contest
    ON prediction_evaluation (actual_contest_number);
