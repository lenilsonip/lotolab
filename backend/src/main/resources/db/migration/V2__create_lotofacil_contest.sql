CREATE TABLE lotofacil_contest (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    contest_number INTEGER NOT NULL,
    draw_date DATE NOT NULL,
    CONSTRAINT uq_lotofacil_contest_number UNIQUE (contest_number),
    CONSTRAINT ck_lotofacil_contest_number_positive CHECK (contest_number > 0)
);

CREATE TABLE lotofacil_drawn_number (
    contest_id BIGINT NOT NULL REFERENCES lotofacil_contest (id),
    drawn_number SMALLINT NOT NULL,
    CONSTRAINT pk_lotofacil_drawn_number PRIMARY KEY (contest_id, drawn_number),
    CONSTRAINT ck_lotofacil_drawn_number_range CHECK (drawn_number BETWEEN 1 AND 25)
);
