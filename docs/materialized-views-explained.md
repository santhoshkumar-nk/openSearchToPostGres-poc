# Materialized Views — What, Why & How They Help

> **Project:** OpenSearch to PostgreSQL Migration  
> **Date:** April 15, 2026

---

## Table of Contents

1. [What Are Materialized Views?](#1-what-are-materialized-views)
2. [Why Do We Need Them?](#2-why-do-we-need-them)
3. [How Materialized Views Work in This Project](#3-how-materialized-views-work-in-this-project)
4. [The Three Aggregation Approaches — Compared](#4-the-three-aggregation-approaches--compared)
   - [statsByPostgresAggregate (stats)](#41-statsbypostgresaggregate-stats)
   - [statsByJavaAggregation](#42-statsbyjavaaggregation)
   - [statsByPostGresMaterializedViews](#43-statsbypostgresmaterializedviews)
5. [Architecture Diagram](#5-architecture-diagram)
6. [Data Flow Comparison](#6-data-flow-comparison)
7. [Performance Comparison](#7-performance-comparison)
8. [When to Use Which Approach](#8-when-to-use-which-approach)
9. [Trade-offs](#9-trade-offs)
10. [Alternative PostgreSQL Approaches (Beyond Materialized Views)](#10-alternative-postgresql-approaches-beyond-materialized-views)
    - [Trigger-Based Summary Tables](#101-trigger-based-summary-tables-real-time-zero-staleness)
    - [GROUPING SETS / CUBE / ROLLUP](#102-grouping-sets--cube--rollup-single-query-multi-aggregation)
    - [TimescaleDB Continuous Aggregates](#103-timescaledb-continuous-aggregates-automatic-incremental-refresh)

---

## 1. What Are Materialized Views?

A **Materialized View** is a database object that stores the **pre-computed result** of a query physically on disk — unlike a regular view, which re-executes the query every time it's accessed.

```
┌──────────────────────────────────────────────────────────────────────────┐
│                        Regular View vs Materialized View                │
├─────────────────────────────────┬────────────────────────────────────────┤
│         Regular View            │         Materialized View              │
├─────────────────────────────────┼────────────────────────────────────────┤
│ Virtual — no data stored        │ Physical — data stored on disk         │
│ Re-executes query every time    │ Returns pre-computed result instantly  │
│ Always up-to-date               │ Stale until refreshed                  │
│ Slow for complex aggregations   │ Fast reads, even for heavy queries     │
│ No indexes allowed              │ Can have indexes for faster lookup     │
└─────────────────────────────────┴────────────────────────────────────────┘
```

### SQL Example

```sql
-- Create a materialized view that pre-computes daily insight counts
CREATE MATERIALIZED VIEW mv_insights_date_histogram AS
SELECT
    to_char(i.original_insight_time, 'YYYY-MM-DD') AS period,
    i.account_id,
    i.forensic_info_id,
    COUNT(i.insight_id) AS count
FROM insights_info i
GROUP BY period, i.account_id, i.forensic_info_id
ORDER BY period;
```

The first time this runs, PostgreSQL executes the `SELECT`, computes all the aggregations, and **stores the result as a table**. Subsequent reads are just table scans on this small, pre-computed table — not the full `insights_info` table.

### Refreshing

Since materialized views are snapshots, they need to be **refreshed** to reflect new data:

```sql
-- Blocking: locks the view during refresh (no reads allowed)
REFRESH MATERIALIZED VIEW mv_insights_date_histogram;

-- Non-blocking: allows reads during refresh (requires a UNIQUE index)
REFRESH MATERIALIZED VIEW CONCURRENTLY mv_insights_date_histogram;
```

---

## 2. Why Do We Need Them?

### The Problem: Aggregation Queries Are Expensive

In this **OpenSearch to PostgreSQL** migration project, the application needs to compute aggregation statistics for forensic investigation insights:

- **Date histogram** — how many insights per day/month
- **Type counts** — how many insights of each type (Abnormal, IoC, Informational)
- **Category counts** — how many insights per category

These aggregations require scanning the entire `insights_info` table (potentially thousands of rows), grouping, counting, and returning results — **on every single API call**.

### The Impact Without Materialized Views

| Operation | What Happens | Cost |
|-----------|-------------|------|
| Every API call to `/stats` | Full table scan of `insights_info` | O(n) — grows with data |
| 3 separate aggregations | 3 full scans of the same table | 3 × O(n) |
| Concurrent users | Each user triggers their own full scans | Multiplied load |
| Growing dataset | Response time degrades linearly | Unbounded |

### The Solution: Pre-Compute Once, Read Many Times

Materialized views flip the model:

```
❌ WITHOUT MVs: Every API request → Full table scan → Aggregate → Return
✅ WITH MVs:    Background job → Full table scan → Store result
                Every API request → Read pre-computed table → Return instantly
```

The expensive computation happens **once** (in the background), and every API request just reads a small, pre-computed table.

---

## 3. How Materialized Views Work in This Project

### 3.1 Three Materialized Views

| Materialized View | Purpose | Source Table |
|-------------------|---------|-------------|
| `mv_insights_date_histogram` | Pre-computed daily insight counts | `insights_info` |
| `mv_insights_type_counts` | Pre-computed counts per insight type | `insights_info` |
| `mv_insights_category_counts` | Pre-computed counts per insight category | `insights_info` |

### 3.2 Background Refresh Service

Instead of refreshing on every API call (which defeats the purpose), the project uses a **scheduled background service**:

```java
// MaterializedViewRefreshService.java
@Scheduled(fixedRate = 300_000) // every 5 minutes
@Transactional
public void refreshAllMaterializedViews() {
    entityManager.createNativeQuery(
        "REFRESH MATERIALIZED VIEW CONCURRENTLY mv_insights_date_histogram"
    ).executeUpdate();
    entityManager.createNativeQuery(
        "REFRESH MATERIALIZED VIEW CONCURRENTLY mv_insights_type_counts"
    ).executeUpdate();
    entityManager.createNativeQuery(
        "REFRESH MATERIALIZED VIEW CONCURRENTLY mv_insights_category_counts"
    ).executeUpdate();
}
```

Key details:
- **`CONCURRENTLY`** — allows reads during refresh (no downtime)
- **Every 5 minutes** — configurable; balances freshness vs. DB load
- **Requires UNIQUE indexes** on each MV for `CONCURRENTLY` to work

### 3.3 Single UNION ALL Query

At query time, all 3 MVs are queried in a **single SQL statement** using `UNION ALL`:

```sql
SELECT 'date_histogram' AS agg_type, period AS key, NULL AS type, NULL AS category, count AS doc_count
FROM mv_insights_date_histogram WHERE ...
UNION ALL
SELECT 'type_counts', NULL, type, NULL, SUM(doc_count)
FROM mv_insights_type_counts WHERE ... GROUP BY ...
UNION ALL
SELECT 'category_counts', NULL, NULL, category, SUM(doc_count)
FROM mv_insights_category_counts WHERE ... GROUP BY ...
```

This means **1 database round-trip** instead of 3 separate queries.

### 3.4 Required Indexes

```sql
-- Enable REFRESH CONCURRENTLY
CREATE UNIQUE INDEX idx_mv_date_hist_pk
    ON mv_insights_date_histogram(period, account_id, forensic_info_id);

CREATE UNIQUE INDEX idx_mv_type_pk
    ON mv_insights_type_counts(account_id, forensic_info_id, type, original_insight_time);

CREATE UNIQUE INDEX idx_mv_category_pk
    ON mv_insights_category_counts(account_id, forensic_info_id, category, original_insight_time);
```

---

## 4. The Three Aggregation Approaches — Compared

This project implements **three different approaches** to compute the same aggregation result. Each serves a different purpose and has different trade-offs.

---

### 4.1 statsByPostgresAggregate (`/stats`)

> **API:** `GET /migration/opensearch-to-postgres/stats`  
> **Time:** 16 ms (old) → **14 ms** (new)  
> **Approach:** PostgreSQL CTE-based aggregation — single SQL query with `WITH ... UNION ALL` on the live `insights_info` table

> ⚠️ **Note:** Despite the project name referencing "OpenSearch", this endpoint does **NOT** call OpenSearch. It runs a native PostgreSQL query using Common Table Expressions (CTEs) to compute all 3 aggregations in a single database round-trip.

#### How It Works

```
Client → Controller → Service → Repository → PostgreSQL (native CTE query)
                                                    ↓
                                              PostgreSQL computes via CTE:
                                              • date_histogram (generate_series + LEFT JOIN)
                                              • type_counts (GROUP BY type)
                                              • category_counts (GROUP BY category)
                                              All combined via UNION ALL
                                                    ↓
                                              Returns result rows
                                                    ↓
                                           Map to AggregationRoot POJO
                                                    ↓
                                              Return to client
```

#### What Happens Under the Hood

1. The repository builds a **native SQL CTE query** with `WITH periods AS ... date_histogram AS ... type_counts AS ... category_counts AS ...`
2. PostgreSQL executes all 3 aggregations **server-side** in a single query using `UNION ALL`
3. `generate_series()` creates the date periods, `LEFT JOIN` fills zero-count days
4. Java maps the flat result rows to the `AggregationRoot` POJO

#### Key Characteristics

| Aspect | Detail |
|--------|--------|
| **Data Source** | PostgreSQL (`insights_info` table) |
| **Aggregation Engine** | PostgreSQL (server-side CTE + UNION ALL) |
| **OpenSearch Involved?** | ❌ No |
| **Table Scans** | Filtered by account_id, forensic_info_id, time range (uses indexes) |
| **DB Round-Trips** | 2 (1 for time bounds + 1 for CTE aggregation) |
| **Data Freshness** | Real-time (queries live table) |
| **Java Processing** | Minimal — just row mapping |

#### When to Use
- When you need **real-time** aggregation results from PostgreSQL
- When you want **all 3 aggregations in a single SQL query** (no Java loops)
- As the **default approach** post-migration from OpenSearch

---

### 4.2 statsByJavaAggregation (`/statsByJavaAggregation`)

> **API:** `GET /migration/opensearch-to-postgres/statsByJavaAggregation`  
> **Time:** 19 ms (old) → **19 ms** (new)  
> **Approach:** Fetch raw entities from PostgreSQL, aggregate in Java

#### How It Works

```
Client → Controller → Service → Repository → PostgreSQL
                                                   ↓
                                           SELECT * FROM insights_info
                                           WHERE account_id = ? AND ...
                                                   ↓
                                           Returns List<InsightsInfo> entities
                                                   ↓
                                        Java single-pass aggregation loop:
                                        ┌──────────────────────────────────┐
                                        │  for each InsightsInfo:          │
                                        │    • dateHistogramMap.merge()    │
                                        │    • typeCounts.merge()          │
                                        │    • categoryCounts.merge()      │
                                        └──────────────────────────────────┘
                                                   ↓
                                           Map to AggregationRoot POJO
                                                   ↓
                                             Return to client
```

#### What Happens Under the Hood

1. JPA `Specification` builds a dynamic `WHERE` clause from query parameters
2. `findAll(spec)` loads all matching `InsightsInfo` entities into JVM memory
3. A **single-pass loop** iterates once over the list, computing all 3 aggregations simultaneously:
   - Date histogram (count per day)
   - Type counts (Abnormal, IoC, Informational)
   - Category counts
4. Results are mapped to the `AggregationRoot` POJO

#### Key Characteristics

| Aspect | Detail |
|--------|--------|
| **Data Source** | PostgreSQL (`insights_info` table) |
| **Aggregation Engine** | Java (in-memory, single-pass loop) |
| **PostgreSQL Involved?** | ✅ Yes — fetches raw rows |
| **Table Scans** | No full scan — uses composite indexes |
| **DB Round-Trips** | 2 (1 for time bounds + 1 for entity list) |
| **Data Freshness** | Real-time (queries live table) |
| **Java Processing** | Heavy — loads all entities into heap, iterates |

#### When to Use
- When you need **real-time** results from PostgreSQL
- When the dataset is **small to medium** (entities fit in JVM memory)
- When you want **flexibility** to add custom Java logic to aggregation
- When materialized views are not available or not set up

---

### 4.3 statsByPostGresMaterializedViews (`/statsByPostGresMaterializedViews`)

> **API:** `GET /migration/opensearch-to-postgres/statsByPostGresMaterializedViews`  
> **Time:** 23 ms (old) → **14 ms** (new) — **39.1% improvement**  
> **Approach:** Query pre-computed materialized views in PostgreSQL

#### How It Works

```
                    ┌──────────────────────────────────────────────┐
                    │       Background: @Scheduled (every 5 min)   │
                    │                                              │
                    │  REFRESH MATERIALIZED VIEW CONCURRENTLY      │
                    │    • mv_insights_date_histogram              │
                    │    • mv_insights_type_counts                 │
                    │    • mv_insights_category_counts             │
                    └──────────────────────────────────────────────┘

Client → Controller → Service → Repository → PostgreSQL
                                                   ↓
                                           Single UNION ALL query across
                                           3 pre-computed MVs:
                                           ┌─────────────────────────────┐
                                           │ mv_insights_date_histogram  │
                                           │ mv_insights_type_counts     │
                                           │ mv_insights_category_counts │
                                           └─────────────────────────────┘
                                                   ↓
                                           Returns aggregated rows
                                           (already computed, just filtered)
                                                   ↓
                                           Map to AggregationRoot POJO
                                                   ↓
                                             Return to client
```

#### What Happens Under the Hood

1. Materialized views are **pre-computed** in the background every 5 minutes
2. At query time, a single `UNION ALL` SQL query reads from 3 small MV tables
3. The MVs already contain the aggregated counts — no `GROUP BY` on the raw table
4. The `@Transactional(readOnly = true)` annotation skips Hibernate dirty-checking
5. Results are mapped to the `AggregationRoot` POJO

#### Key Characteristics

| Aspect | Detail |
|--------|--------|
| **Data Source** | PostgreSQL (materialized views) |
| **Aggregation Engine** | PostgreSQL (pre-computed at refresh time) |
| **PostgreSQL Involved?** | ✅ Yes — reads from MVs |
| **Table Scans** | No — reads small pre-computed tables |
| **DB Round-Trips** | 1 (single UNION ALL query) |
| **Data Freshness** | Near real-time (up to 5 min stale) |
| **Java Processing** | Minimal — just result mapping |

#### When to Use
- When **speed is critical** (dashboards, KPI tiles)
- When **slightly stale data is acceptable** (up to 5 min delay)
- When the dataset is **large** (MVs avoid full table scans)
- When the same aggregation is queried **frequently** by multiple users

---

## 5. Architecture Diagram

```mermaid
flowchart TD
    subgraph Client
        A[Browser / Postman]
    end

    subgraph Controller
        B[OpenSearchToPostgresController]
    end

    subgraph Service
        C[ForensicInfoMigrationService]
    end

    subgraph "Approach 1: PostgreSQL CTE Aggregate"
        D1[CustomizedInsightsRepository]
        E1[insights_info table — CTE query]
    end

    subgraph "Approach 2: Java Aggregation"
        D2[InsightsInfoRepository.findAll]
        E2[insights_info table]
        F2[Java Single-Pass Loop]
    end

    subgraph "Approach 3: Materialized Views"
        D3[CustomizedInsightsRepository]
        E3[mv_insights_date_histogram]
        E4[mv_insights_type_counts]
        E5[mv_insights_category_counts]
        G3[MaterializedViewRefreshService]
        H3[insights_info table]
    end

    A -->|GET /stats| B
    A -->|GET /statsByJavaAggregation| B
    A -->|GET /statsByPostGresMaterializedViews| B

    B --> C

    C -->|stats| D1 --> E1
    C -->|statsByJavaAggregation| D2 --> E2
    D2 --> F2

    C -->|statsByPostGresMaterializedViews| D3
    D3 -->|UNION ALL| E3
    D3 -->|UNION ALL| E4
    D3 -->|UNION ALL| E5

    G3 -->|Scheduled every 5min| H3
    H3 -->|REFRESH CONCURRENTLY| E3
    H3 -->|REFRESH CONCURRENTLY| E4
    H3 -->|REFRESH CONCURRENTLY| E5
```

---

## 6. Data Flow Comparison

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                              DATA FLOW PER API CALL                                     │
├──────────────────────────────┬──────────────────────────────┬────────────────────────────┤
│ statsByPostgresAggregate  │     statsByJavaAggregation   │ statsByPostGresMVs         │
├──────────────────────────────┼──────────────────────────────┼────────────────────────────┤
│                              │                              │                            │
│  API Request                 │  API Request                 │  API Request               │
│       │                      │       │                      │       │                    │
│       ▼                      │       ▼                      │       ▼                    │
│  MIN/MAX time bounds query   │  MIN/MAX time bounds query   │  MIN/MAX time bounds query │
│       │                      │       │                      │       │                    │
│       ▼                      │       ▼                      │       ▼                    │
│  PostgreSQL CTE query        │  SELECT * FROM insights_info │  UNION ALL across 3 MVs    │
│  (WITH ... UNION ALL)        │       │                      │  (pre-computed data)       │
│       │                      │       ▼                      │       │                    │
│       ▼                      │  Load N entities into JVM    │       ▼                    │
│  Return aggregated rows      │       │                      │  Return aggregated rows    │
│       │                      │       ▼                      │       │                    │
│       ▼                      │  Single-pass loop:           │       ▼                    │
│  Map to POJO                 │  compute 3 aggregations      │  Map to POJO              │
│       │                      │       │                      │       │                    │
│       ▼                      │       ▼                      │       ▼                    │
│  Response (14ms)             │  Map to POJO → Response      │  Response (14ms)           │
│                              │  (19ms)                      │                            │
├──────────────────────────────┼──────────────────────────────┼────────────────────────────┤
│  Entities loaded: 0          │  Entities loaded: N          │  Entities loaded: 0        │
│  Table scans: 0 (indexed)    │  Table scans: 0 (indexed)    │  Table scans: 0 (MVs)     │
│  DB round-trips: 2           │  DB round-trips: 2           │  DB round-trips: 1         │
│  Java processing: minimal    │  Java processing: heavy      │  Java processing: minimal  │
└──────────────────────────────┴──────────────────────────────┴────────────────────────────┘
```

---

## 7. Performance Comparison

### Response Times

| Endpoint | Old Code | New Code | Improvement |
|----------|:--------:|:--------:|:-----------:|
| statsByPostgresAggregate | 16 ms | **14 ms** | 🟢 -2 ms (12.5%) |
| statsByJavaAggregation | 19 ms | **19 ms** | 🟡 0 ms |
| statsByPostGresMaterializedViews | 23 ms | **14 ms** | 🟢 -9 ms (39.1%) |

### Why Materialized Views Improved the Most

The MV approach had the **biggest performance gain** (-9ms, 39.1%) because the old code was:

1. **Refreshing 3 MVs on every API call** — defeating the entire purpose of pre-computation
2. **Running 3 separate SQL queries** — 3 DB round-trips per request
3. **Using a read-write transaction** — unnecessary Hibernate overhead

After optimization:
- Refresh moved to background `@Scheduled` job → **-8–12 ms**
- 3 queries consolidated into 1 `UNION ALL` → **-2–4 ms**
- `@Transactional(readOnly = true)` → marginal improvement

### Visual Comparison


```mermaid
xychart-beta
    title "Response Time — Old vs New (ms)"
    x-axis ["Postgres CTE Aggregate", "Java Aggregation", "Postgres MVs"]
    y-axis "Time (ms)" 0 --> 30
    bar "Old" [16, 19, 23]
    bar "New" [14, 19, 14]
```

> **Old times:** Postgres CTE Aggregate: 16ms | Java Aggregation: 19ms | Postgres MVs: 23ms
> **New times:** Postgres CTE Aggregate: 14ms | Java Aggregation: 19ms | Postgres MVs: 14ms

### Resource Usage Comparison

| Metric | Postgres CTE Aggregate | Java Aggregation | Materialized Views |
|--------|:-------------------:|:----------------:|:-----------------:|
| **JVM Heap Usage** | Low | High (loads entities) | Low |
| **PostgreSQL CPU** | Medium (CTE query) | Medium (query + index scan) | Low (read pre-computed) |
| **OpenSearch CPU** | None | None | None |
| **Network I/O** | 2 calls to PostgreSQL | 2 calls to PostgreSQL | 1 call to PostgreSQL |
| **Scales with data?** | Good (indexed) | Degrades (more entities in heap) | Constant (MVs are small) |

---

## 8. When to Use Which Approach

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        DECISION MATRIX                                  │
├───────────────────────┬───────────┬─────────────┬───────────────────────┤
│ Requirement           │ PG CTE    │ Java Agg    │ Materialized Views   │
│                       │ Aggregate │             │                      │
├───────────────────────┼───────────┼─────────────┼───────────────────────┤
│ Real-time data        │ ✅ Yes    │ ✅ Yes      │ ⚠️ Up to 5 min stale │
│ Speed (low latency)   │ ✅ 14ms   │ ⚠️ 19ms    │ ✅ 14ms              │
│ Large datasets        │ ✅ Yes    │ ❌ Slow     │ ✅ Yes               │
│ No OpenSearch needed  │ ✅ Yes    │ ✅ Yes      │ ✅ Yes               │
│ No extra DB objects   │ ✅ Yes    │ ✅ Yes      │ ❌ Needs MVs+indexes │
│ Custom Java logic     │ ❌ No     │ ✅ Yes      │ ❌ No                │
│ Dashboard / KPI tiles │ ✅ Good   │ ⚠️ OK      │ ✅ Best              │
│ Post-migration (no OS)│ ✅ Yes    │ ✅ Yes      │ ✅ Yes               │
└───────────────────────┴───────────┴─────────────┴───────────────────────┘
```

### Recommendations

| Scenario | Recommended Approach | Reason |
|----------|---------------------|--------|
| **Dashboard KPIs & charts** | Materialized Views (14ms) | Speed + PostgreSQL-native, slight staleness is acceptable |
| **Real-time + single SQL query** | PostgreSQL CTE Aggregate (14ms) | Queries live table, no extra DB objects |
| **Post-migration (no OpenSearch)** | Materialized Views (14ms) | Fastest PostgreSQL-only approach |
| **Need exact real-time from PostgreSQL** | PostgreSQL CTE Aggregate (14ms) | Queries live table, no staleness |
| **Small dataset + custom logic** | Java Aggregation (19ms) | Flexible, no DB setup needed |
| **High-concurrency dashboards** | Materialized Views (14ms) | MVs handle concurrent reads without load on source table |

---

## 9. Trade-offs

### Materialized Views — Pros & Cons

| ✅ Pros | ❌ Cons |
|---------|--------|
| Extremely fast reads (pre-computed) | Data can be up to 5 min stale |
| No JVM heap pressure | Requires DB setup (CREATE, indexes) |
| Handles large datasets efficiently | Refresh consumes DB resources |
| Concurrent reads during refresh | Needs UNIQUE indexes for `CONCURRENTLY` |
| 1 DB round-trip (UNION ALL) | Additional storage for MV tables |
| `readOnly` transaction — minimal overhead | Must manage refresh schedule |

### PostgreSQL CTE Aggregate — Pros & Cons

| ✅ Pros | ❌ Cons |
|---------|--------|
| Real-time data from live table | Slightly heavier on PostgreSQL CPU than MVs |
| No extra DB objects needed | 2 DB round-trips (time bounds + CTE query) |
| Single SQL query computes all 3 aggregations | Performance degrades with very large datasets |
| No OpenSearch dependency | CTE complexity can be harder to debug |
| Minimal Java processing | No pre-computation — recomputes every time |

### Java Aggregation — Pros & Cons

| ✅ Pros | ❌ Cons |
|---------|--------|
| Real-time data from PostgreSQL | Loads all entities into JVM heap |
| Full control over aggregation logic | Scales poorly with large datasets |
| No extra DB objects needed | 2 DB round-trips |
| Easy to add custom business logic | Higher Java processing overhead |

---

## 10. Alternative PostgreSQL Approaches (Beyond Materialized Views)

Beyond the three approaches already implemented, there are additional PostgreSQL-native strategies that can match or beat OpenSearch aggregation performance — with **zero staleness** or **lower operational cost**.

---

### 10.1 Trigger-Based Summary Tables (Real-Time, Zero Staleness)

> **Best for:** Real-time dashboards where 5-min staleness is unacceptable  
> **Speed:** ~14ms (same as MVs)  
> **Staleness:** ✅ **Zero** — updated on every INSERT/UPDATE/DELETE

Unlike materialized views that require periodic `REFRESH`, summary tables are **incrementally updated in real-time** using database triggers.

#### How It Works

```
INSERT INTO insights_info → Trigger fires → UPDATE summary table (increment count)
DELETE FROM insights_info → Trigger fires → UPDATE summary table (decrement count)

API Request → SELECT from summary table → Return instantly (no aggregation needed)
```

#### SQL Setup

```sql
-- 1. Create summary table
CREATE TABLE insights_date_summary (
    account_id       VARCHAR NOT NULL,
    forensic_info_id VARCHAR NOT NULL,
    period           DATE    NOT NULL,
    type             VARCHAR,
    category         VARCHAR,
    doc_count        BIGINT  DEFAULT 0,
    PRIMARY KEY (account_id, forensic_info_id, period, COALESCE(type,''), COALESCE(category,''))
);

-- 2. Create trigger function (incremental update)
CREATE OR REPLACE FUNCTION fn_update_insights_summary()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        INSERT INTO insights_date_summary (account_id, forensic_info_id, period, type, category, doc_count)
        VALUES (NEW.account_id, NEW.forensic_info_id, DATE(NEW.original_insight_time), NEW.type, NEW.category, 1)
        ON CONFLICT (account_id, forensic_info_id, period, COALESCE(type,''), COALESCE(category,''))
        DO UPDATE SET doc_count = insights_date_summary.doc_count + 1;
    ELSIF TG_OP = 'DELETE' THEN
        UPDATE insights_date_summary
        SET doc_count = doc_count - 1
        WHERE account_id = OLD.account_id
          AND forensic_info_id = OLD.forensic_info_id
          AND period = DATE(OLD.original_insight_time)
          AND type = OLD.type
          AND category = OLD.category;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

-- 3. Attach trigger
CREATE TRIGGER trg_insights_summary
AFTER INSERT OR DELETE ON insights_info
FOR EACH ROW EXECUTE FUNCTION fn_update_insights_summary();
```

#### Query at API Time

```sql
-- All 3 aggregations in a single query from the summary table
SELECT period, type, category, SUM(doc_count) AS doc_count
FROM insights_date_summary
WHERE account_id = :accountId
  AND forensic_info_id = :investigationId
  AND period BETWEEN :after AND :before
GROUP BY period, type, category;
```

#### Comparison with Materialized Views

```
┌──────────────────────────┬─────────────────────┬──────────────────────────┐
│                          │ Materialized Views   │ Trigger Summary Tables   │
├──────────────────────────┼─────────────────────┼──────────────────────────┤
│ Staleness                │ Up to 5 min          │ Zero (real-time)         │
│ Read speed               │ ~14ms                │ ~14ms                    │
│ Write overhead           │ None on INSERT       │ Small (trigger per row)  │
│ Refresh needed?          │ Yes (scheduled)      │ No                       │
│ Storage                  │ Full snapshot         │ Compact (aggregated)     │
│ Complexity               │ Low                  │ Medium (trigger logic)   │
│ Handles DELETE/UPDATE?   │ Yes (on refresh)     │ Yes (trigger handles it) │
└──────────────────────────┴─────────────────────┴──────────────────────────┘
```

---

### 10.2 GROUPING SETS / CUBE / ROLLUP (Single-Query Multi-Aggregation)

> **Best for:** Computing all aggregation dimensions in **one SQL pass**  
> **Speed:** ~15–17ms  
> **Staleness:** ✅ Zero — queries live table

PostgreSQL's `GROUPING SETS` lets you compute **multiple GROUP BY dimensions** in a single query — eliminating the need for `UNION ALL` or multiple queries.

#### SQL Example

```sql
SELECT
    CASE WHEN GROUPING(period) = 0 AND GROUPING(type) = 1 AND GROUPING(category) = 1
         THEN 'date_histogram'
         WHEN GROUPING(period) = 1 AND GROUPING(type) = 0 AND GROUPING(category) = 1
         THEN 'type_counts'
         WHEN GROUPING(period) = 1 AND GROUPING(type) = 1 AND GROUPING(category) = 0
         THEN 'category_counts'
    END AS agg_type,
    to_char(original_insight_time, 'YYYY-MM-DD') AS period,
    type,
    category,
    COUNT(*) AS doc_count
FROM insights_info
WHERE account_id = :accountId
  AND forensic_info_id = :investigationId
  AND original_insight_time BETWEEN :after AND :before
GROUPING SETS (
    (to_char(original_insight_time, 'YYYY-MM-DD')),  -- date histogram
    (type),                                            -- type counts
    (category)                                         -- category counts
)
ORDER BY agg_type, period;
```

**Key advantage:** PostgreSQL scans the table **once** and computes all 3 aggregations in a single pass — instead of 3 separate `GROUP BY` queries or `UNION ALL`.

#### When to Use
- When you want **real-time results** without MVs or triggers
- When the table has **proper composite indexes** (makes the single scan fast)
- When you want the **simplest approach** — no extra DB objects, no triggers, no refresh jobs

---

### 10.3 TimescaleDB Continuous Aggregates (Automatic, Incremental Refresh)

> **Best for:** Time-series data with automatic, incremental MV refresh  
> **Speed:** ~10–14ms  
> **Staleness:** Configurable (can be near-zero)  
> **Requires:** [TimescaleDB extension](https://www.timescale.com/) (free, open-source)

TimescaleDB is a PostgreSQL extension purpose-built for time-series data. Its **continuous aggregates** are like materialized views but with **automatic incremental refresh** — only new/changed data is recomputed.

#### Setup

```sql
-- 1. Convert insights_info into a hypertable (one-time)
SELECT create_hypertable('insights_info', 'original_insight_time');

-- 2. Create continuous aggregate (replaces your materialized view)
CREATE MATERIALIZED VIEW ca_insights_daily
WITH (timescaledb.continuous) AS
SELECT
    time_bucket('1 day', original_insight_time) AS period,
    account_id,
    forensic_info_id,
    type,
    category,
    COUNT(*) AS doc_count
FROM insights_info
GROUP BY period, account_id, forensic_info_id, type, category;

-- 3. Add automatic refresh policy (refresh last 1 hour of data every 5 min)
SELECT add_continuous_aggregate_policy('ca_insights_daily',
    start_offset    => INTERVAL '1 hour',
    end_offset      => INTERVAL '1 minute',
    schedule_interval => INTERVAL '5 minutes');
```

#### Why It's Better Than Plain Materialized Views

```
┌─────────────────────────────┬──────────────────────┬───────────────────────────┐
│                             │ PostgreSQL MVs        │ TimescaleDB Cont. Aggs    │
├─────────────────────────────┼──────────────────────┼───────────────────────────┤
│ Refresh type                │ Full recompute        │ Incremental (only new)    │
│ Refresh cost (10M rows)     │ Scans all 10M rows    │ Scans only new rows       │
│ Automatic refresh?          │ No (need @Scheduled)  │ Yes (built-in policy)     │
│ Concurrent refresh?         │ Needs UNIQUE index    │ Built-in                  │
│ Time-series optimized?      │ No                    │ Yes (chunked storage)     │
│ Compression?                │ No                    │ Yes (90%+ compression)    │
│ Setup complexity            │ Medium                │ Low (one SQL command)     │
└─────────────────────────────┴──────────────────────┴───────────────────────────┘
```

---

### 10.4 Comparison — All Approaches

```
┌────────────────────────────────┬────────┬───────────┬──────────┬───────────┬──────────────┐
│ Approach                       │ Speed  │ Staleness │ Write    │ Extra DB  │ Cost         │
│                                │        │           │ Overhead │ Objects   │              │
├────────────────────────────────┼────────┼───────────┼──────────┼───────────┼──────────────┤
│ PostgreSQL CTE Aggregate       │ 14ms   │ Real-time │ None     │ None      │ $ (query)    │
│ Java Aggregation               │ 19ms   │ Real-time │ None     │ None      │ $ (JVM heap) │
│ Materialized Views             │ 14ms   │ ~5 min    │ None     │ 3 MVs     │ $ (refresh)  │
│ Trigger Summary Tables  (NEW)  │ ~14ms  │ Real-time │ Small    │ 1 table   │ $ (trigger)  │
│ GROUPING SETS            (NEW) │ ~16ms  │ Real-time │ None     │ None      │ $ (index)    │
│ TimescaleDB Cont. Aggs   (NEW) │ ~12ms  │ ~1 min    │ None     │ 1 CA      │ $ (extension)│
└────────────────────────────────┴────────┴───────────┴──────────┴───────────┴──────────────┘
```

### Recommendation

| Scenario | Best Approach |
|----------|--------------|
| **Drop OpenSearch entirely, keep real-time** | Trigger Summary Tables |
| **Drop OpenSearch, simplest migration** | GROUPING SETS (no extra DB objects) |
| **Drop OpenSearch, large dataset + auto-refresh** | TimescaleDB Continuous Aggregates |
| **Already using MVs, want zero staleness** | Migrate MVs → Trigger Summary Tables |
| **Already using MVs, happy with ~5 min staleness** | Keep Materialized Views (current) |

---

## Summary

> **Materialized Views** are the optimal approach for this OpenSearch-to-PostgreSQL migration when the primary use case is **dashboard aggregation** (date histograms, type counts, category counts).
>
> They eliminate the need for OpenSearch while matching its performance (**14ms**), at the cost of data being up to **5 minutes stale** — a trade-off that is perfectly acceptable for dashboard visualizations.
>
> For use cases requiring **exact real-time data** from PostgreSQL, **PostgreSQL CTE Aggregate** (14ms) or **Java Aggregation** (19ms) remain available as fallbacks.
>
> ⚠️ **Note:** None of the stats endpoints in this project invoke OpenSearch. All aggregation is performed entirely within PostgreSQL. The `OpenSearchConfig.java` exists as a connection bean but is not used by any stats API.

---

*Related docs:*
- *[Performance Comparison Report](performance-comparison.md) — Benchmark numbers and screenshots*
- *[Before vs After Performance Fixes](before-vs-after-performance-fixes.md) — Code-level changes and optimizations*

