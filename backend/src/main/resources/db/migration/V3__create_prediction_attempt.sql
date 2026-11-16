CREATE TABLE prediction_attempt (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    target_contest_number INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_prediction_attempt_target_positive CHECK (target_contest_number > 0)
);

CREATE TABLE prediction_attempt_number (
    attempt_id BIGINT NOT NULL REFERENCES prediction_attempt (id),
    number SMALLINT NOT NULL,
    CONSTRAINT pk_prediction_attempt_number PRIMARY KEY (attempt_id, number),
    CONSTRAINT ck_prediction_attempt_number_range CHECK (number BETWEEN 1 AND 25)
);
