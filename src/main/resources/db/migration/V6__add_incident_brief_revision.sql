CREATE TABLE incident_brief_revisions (
    incident_id VARCHAR(128) PRIMARY KEY,
    revision BIGINT NOT NULL CHECK (revision > 0),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
