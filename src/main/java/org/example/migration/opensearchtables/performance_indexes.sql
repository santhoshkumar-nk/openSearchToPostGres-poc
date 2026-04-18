-- ============================================================
-- Performance indexes for insights_info table
-- Run these once in your PostgreSQL database.
-- ============================================================

-- Composite index for the most common filter pattern (account + investigation)
CREATE INDEX IF NOT EXISTS idx_insights_account_forensic
ON insights_info(account_id, forensic_info_id);

-- Covering index for aggregation queries — avoids heap lookup for type/category
CREATE INDEX IF NOT EXISTS idx_insights_agg_covering
ON insights_info(account_id, forensic_info_id, original_insight_time)
INCLUDE (type, category);

-- Index on original_insight_time for date range filters
CREATE INDEX IF NOT EXISTS idx_insights_time
ON insights_info(original_insight_time);

-- ============================================================
-- UNIQUE indexes on materialized views (required for REFRESH CONCURRENTLY)
-- ============================================================

CREATE UNIQUE INDEX IF NOT EXISTS idx_mv_date_hist_pk
ON mv_insights_date_histogram(period, account_id, forensic_info_id);

CREATE UNIQUE INDEX IF NOT EXISTS idx_mv_type_pk
ON mv_insights_type_counts(account_id, forensic_info_id, type, original_insight_time);

CREATE UNIQUE INDEX IF NOT EXISTS idx_mv_category_pk
ON mv_insights_category_counts(account_id, forensic_info_id, category, original_insight_time);

