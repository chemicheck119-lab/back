CREATE TABLE incidents (
    incident_id VARCHAR(128) PRIMARY KEY,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_activity_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE incident_agent_memories (
    incident_id VARCHAR(128) PRIMARY KEY,
    revision INTEGER NOT NULL CHECK (revision > 0),
    memory_sha256 CHAR(64) NOT NULL,
    parent_memory_sha256 CHAR(64),
    memory_json TEXT NOT NULL,
    events_json TEXT NOT NULL,
    request_id VARCHAR(128) NOT NULL,
    run_id VARCHAR(128) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE incident_analysis_snapshots (
    analysis_id VARCHAR(128) PRIMARY KEY,
    incident_id VARCHAR(128) NOT NULL,
    request_id VARCHAR(128) NOT NULL,
    model_response_json TEXT NOT NULL,
    bff_response_json TEXT NOT NULL,
    agent_response_json TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_analysis_incident_created
    ON incident_analysis_snapshots (incident_id, created_at DESC);

CREATE TABLE substance_confirmations (
    confirmation_id VARCHAR(128) PRIMARY KEY,
    incident_id VARCHAR(128) NOT NULL,
    confirmation_role VARCHAR(20) NOT NULL,
    cas_number VARCHAR(32) NOT NULL,
    display_name VARCHAR(500),
    confirmation_basis VARCHAR(40) NOT NULL,
    observed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    confirmed_by_user_id VARCHAR(128) NOT NULL,
    confirmed_by_organization_id VARCHAR(128) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_request_id VARCHAR(128) NOT NULL,
    revision BIGINT NOT NULL CHECK (revision > 0),
    confirmation_status VARCHAR(20) NOT NULL,
    superseded_by_confirmation_id VARCHAR(128),
    superseded_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_confirmation_revision UNIQUE (incident_id, confirmation_role, revision),
    CONSTRAINT fk_confirmation_superseded_by
        FOREIGN KEY (superseded_by_confirmation_id)
        REFERENCES substance_confirmations (confirmation_id)
);

CREATE INDEX idx_confirmation_incident_role
    ON substance_confirmations (incident_id, confirmation_role, revision DESC);

CREATE TABLE incident_confirmation_heads (
    incident_id VARCHAR(128) NOT NULL,
    confirmation_role VARCHAR(20) NOT NULL,
    active_confirmation_id VARCHAR(128),
    revision BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (incident_id, confirmation_role),
    CONSTRAINT fk_confirmation_head_active
        FOREIGN KEY (active_confirmation_id)
        REFERENCES substance_confirmations (confirmation_id)
);

CREATE TABLE incident_movement_contexts (
    incident_id VARCHAR(128) PRIMARY KEY,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    location_label VARCHAR(200) NOT NULL,
    coordinate_source VARCHAR(80) NOT NULL,
    observed_at TIMESTAMP WITH TIME ZONE,
    simulation BOOLEAN NOT NULL,
    responder_label VARCHAR(200) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE incident_movement_states (
    incident_id VARCHAR(128) PRIMARY KEY,
    client_sequence BIGINT NOT NULL CHECK (client_sequence > 0),
    responder_latitude DOUBLE PRECISION NOT NULL,
    responder_longitude DOUBLE PRECISION NOT NULL,
    responder_accuracy_meters DOUBLE PRECISION,
    responder_observed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    responder_source VARCHAR(40) NOT NULL,
    journey_state VARCHAR(40) NOT NULL,
    accepted_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE response_records (
    record_id VARCHAR(128) PRIMARY KEY,
    incident_id VARCHAR(128) NOT NULL,
    record_fingerprint CHAR(64) NOT NULL UNIQUE,
    conversation_started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    saved_by_user_id VARCHAR(128) NOT NULL,
    saved_by_organization_id VARCHAR(128) NOT NULL,
    saved_request_id VARCHAR(128) NOT NULL,
    saved_at TIMESTAMP WITH TIME ZONE NOT NULL,
    agent_memory_revision INTEGER,
    agent_memory_sha256 CHAR(64),
    agent_memory_json TEXT,
    agent_events_json TEXT,
    CONSTRAINT fk_response_record_incident
        FOREIGN KEY (incident_id) REFERENCES incidents (incident_id)
);

CREATE INDEX idx_response_record_incident_saved
    ON response_records (incident_id, saved_at DESC);

CREATE TABLE response_record_messages (
    record_id VARCHAR(128) NOT NULL,
    message_id VARCHAR(128) NOT NULL,
    message_sequence INTEGER NOT NULL CHECK (message_sequence > 0),
    message_role VARCHAR(20) NOT NULL,
    message_text TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    analysis_id VARCHAR(128),
    PRIMARY KEY (record_id, message_id),
    CONSTRAINT uq_record_message_sequence UNIQUE (record_id, message_sequence),
    CONSTRAINT fk_record_message_record
        FOREIGN KEY (record_id) REFERENCES response_records (record_id) ON DELETE CASCADE,
    CONSTRAINT fk_record_message_analysis
        FOREIGN KEY (analysis_id) REFERENCES incident_analysis_snapshots (analysis_id)
);

CREATE TABLE response_record_analyses (
    record_id VARCHAR(128) NOT NULL,
    analysis_id VARCHAR(128) NOT NULL,
    reference_order INTEGER NOT NULL,
    PRIMARY KEY (record_id, analysis_id),
    CONSTRAINT uq_record_analysis_order UNIQUE (record_id, reference_order),
    CONSTRAINT fk_record_analysis_record
        FOREIGN KEY (record_id) REFERENCES response_records (record_id) ON DELETE CASCADE,
    CONSTRAINT fk_record_analysis_snapshot
        FOREIGN KEY (analysis_id) REFERENCES incident_analysis_snapshots (analysis_id)
);

CREATE TABLE response_record_confirmations (
    record_id VARCHAR(128) NOT NULL,
    confirmation_id VARCHAR(128) NOT NULL,
    reference_order INTEGER NOT NULL,
    PRIMARY KEY (record_id, confirmation_id),
    CONSTRAINT uq_record_confirmation_order UNIQUE (record_id, reference_order),
    CONSTRAINT fk_record_confirmation_record
        FOREIGN KEY (record_id) REFERENCES response_records (record_id) ON DELETE CASCADE,
    CONSTRAINT fk_record_confirmation_snapshot
        FOREIGN KEY (confirmation_id) REFERENCES substance_confirmations (confirmation_id)
);

CREATE TABLE response_record_movement_snapshots (
    record_id VARCHAR(128) PRIMARY KEY,
    incident_context_json TEXT,
    movement_state_json TEXT,
    CONSTRAINT fk_record_movement_record
        FOREIGN KEY (record_id) REFERENCES response_records (record_id) ON DELETE CASCADE
);
