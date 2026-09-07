CREATE TABLE confirmation_cancellations (
    confirmation_id VARCHAR(128) PRIMARY KEY,
    incident_id VARCHAR(128) NOT NULL,
    confirmation_role VARCHAR(20) NOT NULL,
    cancelled_by_user_id VARCHAR(128) NOT NULL,
    cancelled_by_organization_id VARCHAR(128) NOT NULL,
    cancelled_at TIMESTAMP WITH TIME ZONE NOT NULL,
    cancelled_request_id VARCHAR(128) NOT NULL,
    CONSTRAINT fk_confirmation_cancellation
        FOREIGN KEY (confirmation_id)
        REFERENCES substance_confirmations (confirmation_id)
);

CREATE INDEX idx_confirmation_cancellation_incident_role
    ON confirmation_cancellations (incident_id, confirmation_role, cancelled_at DESC);
