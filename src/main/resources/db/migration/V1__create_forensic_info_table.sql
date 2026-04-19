-- ============================================================
-- V1: Create forensic_info table
-- Mapped from: org.example.migration.postgres.ForensicInfo
-- ============================================================

CREATE TABLE IF NOT EXISTS forensic_info (
    id                      VARCHAR(64)     NOT NULL,
    account_id              VARCHAR(64),
    message                 VARCHAR(1000),
    output_log              VARCHAR(1000),
    backup_name             VARCHAR(255),
    sys_diagnose            TEXT,
    device_type             VARCHAR(100),
    os_version              VARCHAR(100),
    device_id               VARCHAR(100),
    zdevice_id              VARCHAR(100),
    checksum                VARCHAR(255),
    time_trigger_analysis   TIMESTAMP       NOT NULL,
    time_start_analysis     TIMESTAMP       NOT NULL,
    uploaded_time           TIMESTAMP       NOT NULL,
    location                VARCHAR(255),
    device_owner_name       VARCHAR(255),
    policy_trigger_info     VARCHAR(1000),
    investigation_file_size VARCHAR(100),
    investigation_location  VARCHAR(255),
    device_patch_level      VARCHAR(100),
    workstation_os          VARCHAR(100),
    workstation_usage       VARCHAR(255),
    collector_version       VARCHAR(100),
    collector_usage         VARCHAR(255),
    earliest_insight_time   TIMESTAMP       NOT NULL,
    latest_insight_time     TIMESTAMP       NOT NULL,
    suspicious_count        BIGINT          DEFAULT 0,
    ioc_count               BIGINT          DEFAULT 0,
    informational_count     BIGINT          DEFAULT 0,
    suspicious              BOOLEAN         NOT NULL DEFAULT FALSE,
    ioc                     BOOLEAN         NOT NULL DEFAULT FALSE,
    informational           BOOLEAN         NOT NULL DEFAULT FALSE,

    CONSTRAINT pk_forensic_info PRIMARY KEY (id)
);

-- Index for account-level lookups
CREATE INDEX IF NOT EXISTS idx_forensic_info_account_id
    ON forensic_info (account_id);

-- Index for time-range queries on insight bounds
CREATE INDEX IF NOT EXISTS idx_forensic_info_insight_time_range
    ON forensic_info (account_id, earliest_insight_time, latest_insight_time);

