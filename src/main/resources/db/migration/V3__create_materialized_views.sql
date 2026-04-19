-- ============================================================
-- V3: Create materialized views for insights aggregation
-- Used by: aggregateInsightsFromMaterializedViews endpoint
-- ============================================================

-- -------------------------------------------------------
-- MV 1: Daily date histogram (count per day per investigation)
-- -------------------------------------------------------
-- Step 1: Drop existing
-- DROP MATERIALIZED VIEW IF EXISTS mv_insights_date_histogram;

-- Step 2: Recreate using actual original_insight_time as period (not date_trunc which strips time to midnight)
CREATE MATERIALIZED VIEW mv_insights_date_histogram AS
SELECT
    original_insight_time AS period,
    account_id,
    forensic_info_id,
    COUNT(insight_id) AS count
FROM insights_info
WHERE account_id IS NOT NULL
GROUP BY original_insight_time, account_id, forensic_info_id
ORDER BY original_insight_time
WITH DATA;

-- UNIQUE index required for REFRESH CONCURRENTLY (no blocking reads during refresh)
-- Step 3: Recreate unique index for REFRESH CONCURRENTLY
CREATE UNIQUE INDEX idx_mv_date_hist_pk
    ON mv_insights_date_histogram(period, account_id, forensic_info_id);

-- -------------------------------------------------------
-- MV 2: Type counts (count per insight type per investigation)
-- -------------------------------------------------------
CREATE MATERIALIZED VIEW IF NOT EXISTS mv_insights_type_counts AS
SELECT
    account_id,
    forensic_info_id,
    original_insight_time,
    type,
    COUNT(*) AS doc_count
FROM insights_info
GROUP BY account_id, forensic_info_id, original_insight_time, type
WITH DATA;



-- UNIQUE index required for REFRESH CONCURRENTLY
CREATE UNIQUE INDEX IF NOT EXISTS idx_mv_type_pk
    ON mv_insights_type_counts (account_id, forensic_info_id, type, original_insight_time);

-- -------------------------------------------------------
-- MV 3: Category counts (count per insight category per investigation)
-- -------------------------------------------------------
CREATE MATERIALIZED VIEW IF NOT EXISTS mv_insights_category_counts AS
SELECT
    account_id,
    forensic_info_id,
    original_insight_time,
    category,
    COUNT(*) AS doc_count
FROM insights_info
GROUP BY account_id, forensic_info_id, original_insight_time, category
WITH DATA;

-- UNIQUE index required for REFRESH CONCURRENTLY
CREATE UNIQUE INDEX IF NOT EXISTS idx_mv_category_pk
    ON mv_insights_category_counts (account_id, forensic_info_id, category, original_insight_time);

