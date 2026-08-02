CREATE TABLE incident_response_summaries (
    record_id VARCHAR(128) PRIMARY KEY,
    incident_id VARCHAR(128) NOT NULL,
    facility_name VARCHAR(200) NOT NULL,
    facility_address VARCHAR(300),
    incident_substance_name VARCHAR(500),
    incident_substance_cas VARCHAR(32),
    brief_application_status VARCHAR(40) NOT NULL,
    final_response_outcome VARCHAR(40) NOT NULL,
    structured_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_incident_response_summary_record
        FOREIGN KEY (record_id) REFERENCES response_records (record_id) ON DELETE CASCADE,
    CONSTRAINT ck_brief_application_status CHECK (brief_application_status IN (
        'NOT_REVIEWED', 'REVIEWED_NOT_APPLIED', 'PARTIALLY_APPLIED', 'APPLIED'
    )),
    CONSTRAINT ck_final_response_outcome CHECK (final_response_outcome IN (
        'LEAK_STOPPED', 'SPREAD_CONTAINED', 'EVACUATION_COMPLETED',
        'MATERIAL_RECOVERED', 'TRANSFERRED_TO_SPECIALIST', 'FALSE_ALARM',
        'MONITORING_CONTINUES', 'OTHER'
    ))
);

CREATE INDEX idx_incident_response_summary_analysis
    ON incident_response_summaries (structured_at DESC, incident_id);

CREATE TABLE incident_response_actions (
    record_id VARCHAR(128) NOT NULL,
    action_code VARCHAR(40) NOT NULL,
    action_order INTEGER NOT NULL CHECK (action_order > 0),
    PRIMARY KEY (record_id, action_code),
    CONSTRAINT uq_incident_response_action_order UNIQUE (record_id, action_order),
    CONSTRAINT fk_incident_response_action_record
        FOREIGN KEY (record_id) REFERENCES response_records (record_id) ON DELETE CASCADE,
    CONSTRAINT ck_incident_response_action CHECK (action_code IN (
        'ZONE_CONTROL', 'EVACUATION', 'LEAK_SOURCE_CONTROL',
        'ADSORPTION_OR_RECOVERY', 'WATER_SPRAY_OR_DILUTION', 'VENTILATION',
        'DECONTAMINATION', 'RESCUE_OR_EMS', 'OTHER'
    ))
);

CREATE TABLE incident_additional_factors (
    record_id VARCHAR(128) NOT NULL,
    factor_code VARCHAR(40) NOT NULL,
    factor_order INTEGER NOT NULL CHECK (factor_order > 0),
    PRIMARY KEY (record_id, factor_code),
    CONSTRAINT uq_incident_additional_factor_order UNIQUE (record_id, factor_order),
    CONSTRAINT fk_incident_additional_factor_record
        FOREIGN KEY (record_id) REFERENCES response_records (record_id) ON DELETE CASCADE,
    CONSTRAINT ck_incident_additional_factor CHECK (factor_code IN (
        'ACTUAL_MIXING_CONFIRMED', 'ENCLOSED_SPACE', 'HEAT_OR_PRESSURE',
        'DRAIN_OR_WATERWAY_CONNECTION', 'LABEL_OR_MSDS_MISMATCH',
        'ADDITIONAL_SUBSTANCE_FOUND', 'CASUALTY_OR_EXPOSURE',
        'WEATHER_INFLUENCE', 'OTHER'
    ))
);

CREATE TABLE incident_conflict_risks (
    record_id VARCHAR(128) PRIMARY KEY,
    analysis_id VARCHAR(128) NOT NULL,
    incident_cas VARCHAR(32),
    facility_substance_name VARCHAR(500),
    facility_substance_cas VARCHAR(32),
    rule_id VARCHAR(200),
    rule_version VARCHAR(200),
    severity VARCHAR(80),
    risk_level VARCHAR(40),
    risk_level_ko VARCHAR(40),
    brief_text TEXT,
    expert_reviewed BOOLEAN NOT NULL,
    human_confirmation_required BOOLEAN NOT NULL,
    CONSTRAINT fk_incident_conflict_risk_record
        FOREIGN KEY (record_id) REFERENCES response_records (record_id) ON DELETE CASCADE,
    CONSTRAINT fk_incident_conflict_risk_analysis
        FOREIGN KEY (analysis_id) REFERENCES incident_analysis_snapshots (analysis_id)
);

CREATE INDEX idx_incident_conflict_risk_level
    ON incident_conflict_risks (risk_level, facility_substance_cas);

CREATE TABLE incident_conflict_hazards (
    record_id VARCHAR(128) NOT NULL,
    hazard_code VARCHAR(80) NOT NULL,
    hazard_order INTEGER NOT NULL CHECK (hazard_order > 0),
    PRIMARY KEY (record_id, hazard_code),
    CONSTRAINT uq_incident_conflict_hazard_order UNIQUE (record_id, hazard_order),
    CONSTRAINT fk_incident_conflict_hazard_record
        FOREIGN KEY (record_id) REFERENCES incident_conflict_risks (record_id) ON DELETE CASCADE
);

CREATE TABLE incident_conflict_gas_products (
    record_id VARCHAR(128) NOT NULL,
    gas_product VARCHAR(200) NOT NULL,
    product_order INTEGER NOT NULL CHECK (product_order > 0),
    PRIMARY KEY (record_id, gas_product),
    CONSTRAINT uq_incident_conflict_gas_order UNIQUE (record_id, product_order),
    CONSTRAINT fk_incident_conflict_gas_record
        FOREIGN KEY (record_id) REFERENCES incident_conflict_risks (record_id) ON DELETE CASCADE
);
