ALTER TABLE incident_analysis_snapshots
    ADD COLUMN incident_confirmation_id VARCHAR(128);

ALTER TABLE incident_analysis_snapshots
    ADD COLUMN facility_confirmation_id VARCHAR(128);

ALTER TABLE incident_analysis_snapshots
    ADD CONSTRAINT fk_analysis_incident_confirmation
        FOREIGN KEY (incident_confirmation_id)
        REFERENCES substance_confirmations (confirmation_id);

ALTER TABLE incident_analysis_snapshots
    ADD CONSTRAINT fk_analysis_facility_confirmation
        FOREIGN KEY (facility_confirmation_id)
        REFERENCES substance_confirmations (confirmation_id);
