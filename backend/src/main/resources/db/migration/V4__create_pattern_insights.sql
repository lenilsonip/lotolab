CREATE TABLE pattern_insight (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    pattern_type VARCHAR(80) NOT NULL,
    description TEXT NOT NULL,
    confidence_score NUMERIC(5, 4) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_pattern_insight_confidence CHECK (confidence_score BETWEEN 0 AND 1)
);

CREATE INDEX ix_pattern_insight_type_created_at
    ON pattern_insight (pattern_type, created_at DESC);
