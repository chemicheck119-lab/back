ALTER TABLE incident_phone_transcripts
    ADD COLUMN reviewed_text TEXT;

ALTER TABLE incident_phone_transcripts
    ADD COLUMN review_revision BIGINT NOT NULL DEFAULT 0;

ALTER TABLE incident_phone_transcripts
    ADD COLUMN reviewed_by_user_id VARCHAR(128);

ALTER TABLE incident_phone_transcripts
    ADD COLUMN reviewed_by_organization_id VARCHAR(128);

ALTER TABLE incident_phone_transcripts
    ADD COLUMN reviewed_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE incident_phone_transcripts
    ADD COLUMN analyzed_analysis_id VARCHAR(128);

ALTER TABLE incident_phone_transcripts
    ADD COLUMN analyzed_at TIMESTAMP WITH TIME ZONE;

UPDATE incident_phone_transcripts
SET review_status = 'FINAL_PENDING_REVIEW'
WHERE review_status = 'PENDING_REVIEW';

CREATE INDEX idx_phone_transcript_incident_call_segment
    ON incident_phone_transcripts (
        incident_id, provider_call_id, segment_index DESC, occurred_at DESC
    );
