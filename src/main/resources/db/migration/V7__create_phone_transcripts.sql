CREATE TABLE incident_phone_transcripts (
    transcript_id VARCHAR(128) PRIMARY KEY,
    incident_id VARCHAR(128) NOT NULL,
    provider VARCHAR(80) NOT NULL,
    provider_call_id VARCHAR(160) NOT NULL,
    provider_event_id VARCHAR(160) NOT NULL,
    transcript_text TEXT NOT NULL,
    language VARCHAR(32),
    is_final BOOLEAN NOT NULL,
    segment_index INTEGER,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    accepted_at TIMESTAMP WITH TIME ZONE NOT NULL,
    received_request_id VARCHAR(128) NOT NULL,
    review_status VARCHAR(40) NOT NULL,
    CONSTRAINT uq_phone_transcript_provider_event UNIQUE (provider, provider_event_id),
    CONSTRAINT fk_phone_transcript_incident
        FOREIGN KEY (incident_id) REFERENCES incidents (incident_id)
);

CREATE INDEX idx_phone_transcript_incident_received
    ON incident_phone_transcripts (incident_id, accepted_at DESC);
