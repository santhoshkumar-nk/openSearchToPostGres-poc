# Performance Fixes — Before vs After

---

## 1. Materialized View Refresh Removed from API Call

**Impact: ~8–12ms saved per request on `/statsByPostGresMaterializedViews`**

### ❌ BEFORE (`CustomizedInsightsRepositoryImpl.aggregateInsightsFromMaterializedViews`)
```java
@Transactional
public String aggregateInsightsFromMaterializedViews(...) {
    // 3 BLOCKING refresh calls executed on EVERY API request
    entityManager.createNativeQuery("REFRESH MATERIALIZED VIEW mv_insights_date_histogram").executeUpdate();
    entityManager.createNativeQuery("REFRESH MATERIALIZED VIEW mv_insights_type_counts").executeUpdate();
    entityManager.createNativeQuery("REFRESH MATERIALIZED VIEW mv_insights_category_counts").executeUpdate();

    // ...then query the views
}
```
**Problem:** The whole point of materialized views is **pre-computed data**. Refreshing on every API call defeats that — it re-scans the entire `insights_info` table 3 times BEFORE returning results.

### ✅ AFTER
```java
@Transactional(readOnly = true)  // read-only = less overhead
public String aggregateInsightsFromMaterializedViews(...) {
    // NO refresh — just query the pre-computed views
    // ...query the views directly
}
```
**Refresh moved to a background scheduled service** (`MaterializedViewRefreshService.java`):
```java
@Scheduled(fixedRate = 300_000) // every 5 minutes
@Transactional
public void refreshAllMaterializedViews() {
    entityManager.createNativeQuery("REFRESH MATERIALIZED VIEW CONCURRENTLY mv_insights_date_histogram").executeUpdate();
    entityManager.createNativeQuery("REFRESH MATERIALIZED VIEW CONCURRENTLY mv_insights_type_counts").executeUpdate();
    entityManager.createNativeQuery("REFRESH MATERIALIZED VIEW CONCURRENTLY mv_insights_category_counts").executeUpdate();
}
```
**Why it's faster:**
- API no longer waits for 3 full table scans before responding
- `CONCURRENTLY` keyword allows reads during refresh (no lock)
- Views are refreshed in background, not blocking user requests

---

## 2. Combined 3 Separate MV Queries → 1 UNION ALL

**Impact: ~2–4ms saved (eliminates 2 extra DB round-trips)**

### ❌ BEFORE (`aggregateInsightsFromMaterializedViews`)
```java
// Query 1 — date histogram
Query dateHistogramQuery = entityManager.createNativeQuery(dateHistogramSql);  // round-trip #1
List<Object[]> dateHistogramResults = dateHistogramQuery.getResultList();

// Query 2 — type counts
Query typeCountsQuery = entityManager.createNativeQuery(typeCountsSql);        // round-trip #2
List<Object[]> typeCountsResults = typeCountsQuery.getResultList();

// Query 3 — category counts
Query categoryCountsQuery = entityManager.createNativeQuery(categoryCountsSql); // round-trip #3
List<Object[]> categoryCountsResults = categoryCountsQuery.getResultList();
```
**Problem:** 3 separate SQL queries = 3 network round-trips to PostgreSQL, each with connection checkout, query planning, and result transfer overhead.

### ✅ AFTER
```java
// 1 single UNION ALL query — 1 round-trip for all 3 aggregations
String sql = """
    SELECT 'date_histogram' AS agg_type, period AS key, NULL AS type, NULL AS category, count AS doc_count
    FROM mv_insights_date_histogram WHERE ...
    UNION ALL
    SELECT 'type_counts', NULL, type, NULL, SUM(doc_count)
    FROM mv_insights_type_counts WHERE ... GROUP BY ...
    UNION ALL
    SELECT 'category_counts', NULL, NULL, category, SUM(doc_count)
    FROM mv_insights_category_counts WHERE ... GROUP BY ...
""";
Query query = entityManager.createNativeQuery(sql);  // single round-trip
List<Object[]> results = query.getResultList();
```
**Why it's faster:**
- 1 DB round-trip instead of 3
- PostgreSQL can optimize the combined execution plan
- Less connection overhead

---

## 3. Lightweight Date Bounds Query (Replaced Full Entity Load)

**Impact: ~1–3ms saved on ALL 3 stats APIs**

### ❌ BEFORE (`ForensicInfoMigrationService.stats/aggregateInsightsFromMaterializedViews/statsByJavaAggregation`)
```java
// Loads the FULL ForensicInfo entity (all 30+ columns)
ForensicInfo forensicInfo = forensicInfoRepository.findByIdAndAccountId(investigationId, accountId);

// Then triggers lazy-loading of the ENTIRE insights collection just to find min/max dates
if (isNull(after)) {
    after = forensicInfo.getInsights().stream()        // ← loads ALL InsightsInfo rows
            .map(InsightsInfo::getOriginalInsightTime)
            .min(Date::compareTo).orElse(after);
}
if (isNull(before)) {
    before = forensicInfo.getInsights().stream()       // ← iterates ALL rows again
            .map(InsightsInfo::getOriginalInsightTime)
            .max(Date::compareTo).orElse(before);
}
```
**Problem:**
- Loads the full `ForensicInfo` entity (30+ columns) — most are unused
- `.getInsights()` triggers a **lazy-load query that fetches EVERY `InsightsInfo` row** into JVM memory
- Then iterates them **twice** (once for min, once for max) just to find 2 dates
- If there are 10,000 insights, that's 10,000 entities loaded into heap for nothing

### ✅ AFTER (`ForensicInfoRepository` + `ForensicInfoMigrationService.resolveTimeBounds`)
```sql
-- New lightweight native query in ForensicInfoRepository
SELECT MIN(i.original_insight_time), MAX(i.original_insight_time)
FROM insights_info i
WHERE i.account_id = :accountId AND i.forensic_info_id = :investigationId
```
```java
// Returns just 2 values — no entity loading, no lazy proxy
private Date[] resolveTimeBounds(String accountId, String investigationId, Date after, Date before) {
    Object[] results = forensicInfoRepository.findInsightTimeBounds(accountId, investigationId);
    // ... extract min/max from results[0] and results[1]
    return new Date[]{after, before};
}
```
**Why it's faster:**
- PostgreSQL computes `MIN/MAX` using the index — **never loads rows into Java**
- No entity hydration, no Hibernate proxy, no lazy-load cascade
- Returns exactly 2 scalar values instead of thousands of entities

---

## 4. Single-Pass Java Aggregation (Replaced 3 Separate Iterations)

**Impact: ~1–2ms saved on `/statsByJavaAggregation` (grows with data size)**

### ❌ BEFORE (`aggregateFromInsightsList`)
```java
// Iteration 1: date histogram
for (InsightsInfo info : insightsList) {
    // compute date histogram
}

// Iteration 2: type counts
for (InsightsInfo info : insightsList) {
    String type = info.getType();
    typeCounts.put(type, typeCounts.getOrDefault(type, 0L) + 1);
}

// Iteration 3: category counts
for (InsightsInfo info : insightsList) {
    String category = info.getCategory();
    categoryCounts.put(category, categoryCounts.getOrDefault(category, 0L) + 1);
}
```
**Problem:** Iterates the same list 3 times. For 10,000 insights = 30,000 iterations + 3x cache misses on the same data.

### ✅ AFTER
```java
// Single pass: compute ALL three aggregations at once
for (InsightsInfo info : insightsList) {
    // Date histogram
    LocalDate date = info.getOriginalInsightTime().toInstant().atZone(zone).toLocalDate();
    dateHistogramMap.merge(date.toString(), 1L, Long::sum);

    // Type counts
    if (info.getType() != null) {
        typeCounts.merge(info.getType(), 1L, Long::sum);
    }

    // Category counts
    if (info.getCategory() != null) {
        categoryCounts.merge(info.getCategory(), 1L, Long::sum);
    }
}
```
**Why it's faster:**
- 1 loop instead of 3 = better CPU cache locality
- Uses `Map.merge()` instead of `getOrDefault + put` (one hash lookup instead of two)
- For 10,000 insights: 10,000 iterations instead of 30,000

---

## 5. Singleton ObjectMapper (Replaced Per-Request Instantiation)

**Impact: Marginal per-request, but avoids GC pressure over time**

### ❌ BEFORE (in both `CustomizedInsightsRepositoryImpl` and `ForensicInfoMigrationService`)
```java
// NEW ObjectMapper created on EVERY API call
return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(aggregationRoot);
```
**Problem:** `ObjectMapper` constructor is expensive (reflection, module discovery). Creates garbage on every call.

### ✅ AFTER
```java
private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
// ...
return OBJECT_MAPPER.writeValueAsString(aggregationRoot);
```
**Why it's better:** `ObjectMapper` is thread-safe — one instance for the lifetime of the JVM. Zero allocation overhead per request.

---

## 6. Database Indexes (`performance_indexes.sql`)

**Impact: Significant at scale — turns full table scans into index lookups**

### ❌ BEFORE
No indexes on `insights_info` beyond the primary key. Every query with `WHERE account_id = ? AND forensic_info_id = ?` does a **sequential full table scan**.

### ✅ AFTER
```sql
-- Composite index for WHERE account_id = ? AND forensic_info_id = ?
CREATE INDEX idx_insights_account_forensic
ON insights_info(account_id, forensic_info_id);

-- Covering index: avoids heap lookup for aggregation queries
CREATE INDEX idx_insights_agg_covering
ON insights_info(account_id, forensic_info_id, original_insight_time)
INCLUDE (type, category);

-- Date range filter index
CREATE INDEX idx_insights_time
ON insights_info(original_insight_time);

-- UNIQUE indexes on MVs (required for REFRESH CONCURRENTLY)
CREATE UNIQUE INDEX idx_mv_date_hist_pk ON mv_insights_date_histogram(...);
CREATE UNIQUE INDEX idx_mv_type_pk ON mv_insights_type_counts(...);
CREATE UNIQUE INDEX idx_mv_category_pk ON mv_insights_category_counts(...);
```
**Why it's faster:**
- `idx_insights_account_forensic`: Turns `WHERE account_id AND forensic_info_id` from O(n) scan to O(log n) lookup
- `idx_insights_agg_covering`: **Index-only scan** — PostgreSQL reads `type` and `category` directly from the index without touching the table heap
- `idx_insights_time`: Enables range scan for `BETWEEN :after AND :before`
- MV UNIQUE indexes: Enables `REFRESH CONCURRENTLY` so reads aren't blocked during refresh

---

## 7. Read-Only Transaction on MV Query

### ❌ BEFORE
```java
@Transactional  // full read-write transaction
public String aggregateInsightsFromMaterializedViews(...) {
```

### ✅ AFTER
```java
@Transactional(readOnly = true)  // read-only hint
public String aggregateInsightsFromMaterializedViews(...) {
```
**Why:** Tells Hibernate to skip dirty-checking and flush, and PostgreSQL can use a read-only snapshot — slightly less overhead.

---

## Summary Table

| # | Fix | Area | Before | After | Expected Gain |
|---|---|---|---|---|---|
| 1 | MV refresh out of API | DB / MV | 3 `REFRESH` on every call | Background `@Scheduled` | **-8–12ms** |
| 2 | 3 queries → 1 UNION ALL | DB round-trips | 3 queries, 3 round-trips | 1 query, 1 round-trip | **-2–4ms** |
| 3 | Lightweight MIN/MAX query | DB / entity load | Load full entity + all insights | `SELECT MIN, MAX` — 2 scalars | **-1–3ms** |
| 4 | Single-pass aggregation | Java / CPU | 3 iterations over list | 1 iteration | **-1–2ms** |
| 5 | Singleton ObjectMapper | Java / GC | `new ObjectMapper()` per call | Static final singleton | **marginal** |
| 6 | Database indexes | DB / table scans | Full seq scan | Index lookup + index-only scan | **significant at scale** |
| 7 | Read-only transaction | DB / Hibernate | Read-write txn | Read-only txn | **marginal** |

**Estimated total improvement: ~12–21ms** (bringing MV API from 23ms down to ~8–11ms, Java agg from 19ms down to ~12–15ms).

