ALTER TABLE incident_phone_sessions ADD COLUMN provider VARCHAR(80);
ALTER TABLE incident_phone_sessions ADD COLUMN start_event_id VARCHAR(160);
ALTER TABLE incident_phone_sessions ADD COLUMN end_event_id VARCHAR(160);
ALTER TABLE incident_phone_sessions ADD COLUMN started_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE incident_phone_sessions ADD COLUMN call_end_status VARCHAR(40);

CREATE UNIQUE INDEX uq_phone_session_provider_start_event
    ON incident_phone_sessions (provider, start_event_id);

CREATE UNIQUE INDEX uq_phone_session_provider_end_event
    ON incident_phone_sessions (provider, end_event_id);

CREATE INDEX idx_phone_session_waiting_created
    ON incident_phone_sessions (session_status, created_at);
