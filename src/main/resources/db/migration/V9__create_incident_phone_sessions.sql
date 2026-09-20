CREATE TABLE incident_phone_sessions (
    incident_id VARCHAR(128) PRIMARY KEY,
    user_id VARCHAR(128) NOT NULL,
    organization_id VARCHAR(128) NOT NULL,
    station_display_name VARCHAR(200) NOT NULL,
    session_id VARCHAR(128) NOT NULL,
    provider_call_id VARCHAR(160),
    session_status VARCHAR(40) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ended_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_phone_session_incident
        FOREIGN KEY (incident_id) REFERENCES incidents (incident_id),
    CONSTRAINT uq_phone_session_provider_call UNIQUE (provider_call_id)
);

CREATE INDEX idx_phone_session_organization_created
    ON incident_phone_sessions (organization_id, created_at DESC);
