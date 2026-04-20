-- ============================================================
-- V4__add_tsvector_full_text_search.sql
-- Adds PostgreSQL full-text search support to insights_info
-- via a GENERATED STORED tsvector column + GIN index.
--
-- Why GENERATED ALWAYS AS ... STORED?
--   • PostgreSQL automatically backfills ALL existing rows on
--     ALTER TABLE — no manual UPDATE needed.
--   • New/updated rows are kept in sync automatically.
--
-- Performance indexes added:
--   1. GIN index on search_vector  → powers search_vector @@ tsquery
--   2. Composite B-tree index on (account_id, forensic_info_id,
--      original_insight_time) INCLUDE (type, category)
--      → narrows rows via B-tree before GIN is evaluated;
--        INCLUDE avoids heap fetches for type/category in CTE.
-- ============================================================

-- Step 1: Add generated tsvector column
--         Concatenates type + category + description for full-text indexing.
--         COALESCE guards against NULL columns producing NULL tsvector.
--         Backfills all existing rows automatically.
ALTER TABLE insights_info
    ADD COLUMN IF NOT EXISTS search_vector tsvector
        GENERATED ALWAYS AS (
            to_tsvector('english',
                COALESCE(type, '')        || ' ' ||
                COALESCE(category, '')    || ' ' ||
                COALESCE(description, ''))
        ) STORED;

-- Step 2: GIN index — enables fast @@ full-text search operator
--         Used by: search_vector @@ plainto_tsquery / to_tsquery / websearch_to_tsquery
CREATE INDEX IF NOT EXISTS idx_insights_search_vector
    ON insights_info USING GIN (search_vector);

-- Step 3: Composite B-tree index for the WHERE clause filters
--         Covers: account_id, forensic_info_id, original_insight_time
--         INCLUDE: type, category — avoids heap fetches in the CTE SELECT
CREATE INDEX IF NOT EXISTS idx_insights_account_inv_time
    ON insights_info (account_id, forensic_info_id, original_insight_time)
    INCLUDE (type, category);

-- ============================================================
-- Verification (run manually after migration):
--   SELECT COUNT(*) FROM insights_info WHERE search_vector IS NULL;
--   → Should return 0 if backfill completed successfully.
--
-- Example query using this index:
--   SELECT * FROM insights_info
--   WHERE account_id = 'xxx'
--     AND search_vector @@ plainto_tsquery('english', 'Informational');
-- ============================================================

