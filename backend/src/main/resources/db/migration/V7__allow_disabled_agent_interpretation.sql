ALTER TABLE agent_interpretation
    DROP CONSTRAINT ck_agent_interpretation_status;

ALTER TABLE agent_interpretation
    ADD CONSTRAINT ck_agent_interpretation_status CHECK (status IN (
        'SUCCEEDED', 'SKIPPED_DISABLED', 'SKIPPED_NO_API_KEY', 'FAILED', 'INVALID_RESPONSE'
    ));
