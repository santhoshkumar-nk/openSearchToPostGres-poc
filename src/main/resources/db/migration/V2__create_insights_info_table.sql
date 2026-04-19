-- ============================================================
-- V2: Create insights_info table
-- Mapped from: org.example.migration.postgres.InsightsInfo
-- Depends on: V1 (forensic_info)
-- ============================================================

CREATE TABLE IF NOT EXISTS insights_info (
    insight_id              VARCHAR(64)     NOT NULL,
    forensic_info_id        VARCHAR(64)     NOT NULL,
    _custom_all             VARCHAR(255),
    account_id              VARCHAR(64),
    category                VARCHAR(100),
    type                    VARCHAR(100),
    description             VARCHAR(1000),
    rule_name               VARCHAR(255),
    rule_version            VARCHAR(100),
    intention               VARCHAR(255),
    generated_time          TIMESTAMP       NOT NULL,
    original_insight_time   TIMESTAMP       NOT NULL,
    location                VARCHAR(255),
    device_owner_name       VARCHAR(255),
    policy_trigger_info     VARCHAR(1000),
    source_file_name        VARCHAR(255),
    related_investigations  JSONB,
    "attributeInformation"  JSONB,
    suspicious              BOOLEAN         NOT NULL DEFAULT FALSE,
    ioc                     BOOLEAN         NOT NULL DEFAULT FALSE,
    informational           BOOLEAN         NOT NULL DEFAULT FALSE,

    CONSTRAINT pk_insights_info PRIMARY KEY (insight_id),
    CONSTRAINT fk_insights_forensic_info
        FOREIGN KEY (forensic_info_id)
        REFERENCES forensic_info (id)
        ON DELETE CASCADE
);

-- -------------------------------------------------------
-- Indexes for aggregation performance
-- -------------------------------------------------------

-- Composite index: primary filter used in all aggregation queries
CREATE INDEX IF NOT EXISTS idx_insights_account_forensic
    ON insights_info (account_id, forensic_info_id);

-- Covering index: avoids heap access for date histogram + type + category aggregations
CREATE INDEX IF NOT EXISTS idx_insights_agg_covering
    ON insights_info (account_id, forensic_info_id, original_insight_time DESC)
    INCLUDE (type, category);

-- Date range filter index
CREATE INDEX IF NOT EXISTS idx_insights_time
    ON insights_info (original_insight_time);

-- -------------------------------------------------------
-- pg_trgm GIN indexes for wildcard ILIKE search
-- (used by aggregateInsightsWithSearchFilter / statsByWildcardSearch)
-- -------------------------------------------------------

CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_insights_type_trgm
    ON insights_info USING GIN (type gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_insights_category_trgm
    ON insights_info USING GIN (category gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_insights_description_trgm
    ON insights_info USING GIN (description gin_trgm_ops);

