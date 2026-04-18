-- PostgreSQL table definition for ForensicInfo (converted from OpenSearch Java entity)
CREATE TABLE forensic_info (
    id VARCHAR PRIMARY KEY,
    _custom_all TEXT,
    account_id VARCHAR,
    message TEXT,
    output_log TEXT,
    backup_name VARCHAR,
    sys_diagnose TEXT,
    device_type VARCHAR,
    os_version VARCHAR,
    device_id VARCHAR,
    zdevice_id VARCHAR,
    checksum VARCHAR,
    time_trigger_analysis TIMESTAMP,
    time_start_analysis TIMESTAMP,
    uploaded_time TIMESTAMP,
    location VARCHAR,
    device_owner_name VARCHAR,
    policy_trigger_info TEXT,
    investigation_file_size VARCHAR,
    investigation_location VARCHAR,
    device_patch_level VARCHAR,
    workstation_os VARCHAR,
    workstation_usage VARCHAR,
    collector_version VARCHAR,
    collector_usage VARCHAR,
    earliest_insight_time TIMESTAMP,
    latest_insight_time TIMESTAMP,
    suspicious_count BIGINT DEFAULT 0,
    ioc_count BIGINT DEFAULT 0,
    informational_count BIGINT DEFAULT 0
);

-- Child table for insights_info, normalized from InsightInfo Java class
CREATE TABLE insights_info (
    insight_id VARCHAR PRIMARY KEY,
    forensic_info_id VARCHAR REFERENCES forensic_info(id),
    _custom_all TEXT,
    account_id VARCHAR,
    category VARCHAR,
    type VARCHAR,
    description TEXT,
    rule_name VARCHAR,
    rule_version VARCHAR,
    intention VARCHAR,
    generated_time TIMESTAMP,
    original_insight_time TIMESTAMP,
    location VARCHAR,
    device_owner_name VARCHAR,
    policy_trigger_info TEXT,
    source_file_name VARCHAR,
    related_investigations JSONB,
    attribute_information JSONB
);

-- Note: forensic_info_id is a foreign key to forensic_info(id). related_investigations and attribute_information are stored as JSONB for flexibility.
