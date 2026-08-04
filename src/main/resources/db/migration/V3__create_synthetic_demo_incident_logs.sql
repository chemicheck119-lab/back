CREATE TABLE synthetic_demo_incident_logs (
    demo_log_id VARCHAR(128) PRIMARY KEY,
    dataset_version VARCHAR(80) NOT NULL,
    scenario_id VARCHAR(80) NOT NULL,
    station_id VARCHAR(32) NOT NULL,
    station_display_name VARCHAR(200) NOT NULL,
    region VARCHAR(40) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    facility_name VARCHAR(200) NOT NULL,
    facility_address VARCHAR(300) NOT NULL,
    incident_substance_name VARCHAR(200) NOT NULL,
    incident_substance_cas VARCHAR(32) NOT NULL,
    conflict_substance_name VARCHAR(200) NOT NULL,
    conflict_substance_cas VARCHAR(32) NOT NULL,
    rule_id VARCHAR(200) NOT NULL,
    rule_version VARCHAR(120) NOT NULL,
    severity VARCHAR(80) NOT NULL,
    risk_level VARCHAR(40) NOT NULL,
    risk_level_ko VARCHAR(40) NOT NULL,
    hazard_codes TEXT NOT NULL,
    gas_products TEXT NOT NULL,
    risk_summary VARCHAR(500) NOT NULL,
    performed_action VARCHAR(40) NOT NULL,
    brief_application_status VARCHAR(40) NOT NULL,
    additional_factor VARCHAR(40) NOT NULL,
    final_response_outcome VARCHAR(40) NOT NULL,
    data_classification VARCHAR(40) NOT NULL,
    operational_record BOOLEAN NOT NULL,
    source_name VARCHAR(200) NOT NULL,
    source_url VARCHAR(500) NOT NULL,
    disclosure VARCHAR(500) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_synthetic_demo_station_scenario
        UNIQUE (dataset_version, station_id, scenario_id),
    CONSTRAINT ck_synthetic_demo_risk_level
        CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT ck_synthetic_demo_classification
        CHECK (data_classification = 'PUBLIC_SYNTHETIC'),
    CONSTRAINT ck_synthetic_demo_not_operational
        CHECK (operational_record = FALSE)
);

CREATE INDEX idx_synthetic_demo_station_occurred
    ON synthetic_demo_incident_logs (station_id, occurred_at DESC);

CREATE INDEX idx_synthetic_demo_region_risk
    ON synthetic_demo_incident_logs (region, risk_level, occurred_at DESC);
