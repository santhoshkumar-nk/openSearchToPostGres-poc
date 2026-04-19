package org.example.migration.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.extern.slf4j.Slf4j;
import org.example.migration.dto.aggregations.AggregationRoot;
import org.example.migration.dto.aggregations.Aggregations;
import org.example.migration.dto.aggregations.DateHistogramInsightsOverTime;
import org.example.migration.dto.aggregations.FiltersTypeCounts;
import org.example.migration.dto.aggregations.StermsCategory;
import org.example.migration.dto.aggregations.SimpleValueTotalDocCount;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Repository
//public class CustomizedInsightsRepositoryImpl implements InsightsInfoRepository {
public class CustomizedInsightsRepositoryImpl implements CustomizedInsightsRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public AggregationRoot aggregateInsights(String accountId, String investigationId, Date after, Date before, Map<String, String> terms, String timezoneId)  {
        // Format: 'YYYY-MM' for month, adjust as needed
        String format = "YYYY-MM-DD";
        String interval = "1 month"; // or '1 day', etc.

        String sql = """
        WITH periods AS (
            SELECT to_char(d, :format) AS period
            FROM generate_series(
                CAST(:after AS timestamptz),
                CAST(:before AS timestamptz),
                CAST(:interval AS interval)
            ) d
        ),
        date_histogram AS (
            SELECT p.period, COALESCE(COUNT(i.insight_id), 0) AS count
            FROM periods p
            LEFT JOIN insights_info i
                ON to_char(i.original_insight_time AT TIME ZONE :timezone, :format) = p.period
                AND i.account_id = :accountId
                AND i.forensic_info_id = :investigationId
                AND i.original_insight_time BETWEEN :after AND :before
            GROUP BY p.period
        ),
        type_counts AS (
            SELECT i.type, COUNT(*) as doc_count
            FROM insights_info i
            WHERE i.account_id = :accountId
              AND i.forensic_info_id = :investigationId
              AND i.original_insight_time BETWEEN :after AND :before
            GROUP BY i.type
        ),
        category_counts AS (
            SELECT i.category, COUNT(*) as doc_count
            FROM insights_info i
            WHERE i.account_id = :accountId
              AND i.forensic_info_id = :investigationId
              AND i.original_insight_time BETWEEN :after AND :before
            GROUP BY i.category
        )
        SELECT 'date_histogram' as agg_type, period as key, NULL as type, NULL as category, count as doc_count FROM date_histogram
        UNION ALL
        SELECT 'type_counts' as agg_type, NULL as key, type, NULL as category, doc_count FROM type_counts
        UNION ALL
        SELECT 'category_counts' as agg_type, NULL as key, NULL as type, category, doc_count FROM category_counts
        """;
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("format", format);
        query.setParameter("interval", interval);
        query.setParameter("after", after);
        query.setParameter("before", before);
        query.setParameter("timezone", timezoneId);
        query.setParameter("accountId", accountId);
        query.setParameter("investigationId", investigationId);
        @SuppressWarnings("unchecked")
        List<Object[]> results = query.getResultList();
        // Split results into date histogram, type counts, and category counts
        List<DateHistogramInsightsOverTime.Bucket> buckets = new java.util.ArrayList<>();
        FiltersTypeCounts.Buckets bucketsType = FiltersTypeCounts.Buckets.builder().build();
        List<StermsCategory.Bucket> categoryBuckets = new java.util.ArrayList<>();
        for (Object[] row : results) {
            String aggType = (String) row[0];
            if ("date_histogram".equals(aggType)) {
                String keyAsString = (String) row[1];
                Long key = java.time.LocalDate.parse(keyAsString).atStartOfDay(java.time.ZoneOffset.UTC).toEpochSecond();
                Long docCount = ((Number) row[4]).longValue();
                buckets.add(DateHistogramInsightsOverTime.Bucket.builder()
                        .key_as_string(keyAsString)
                        .key(key)
                        .doc_count(docCount)
                        .build());
            } else if ("type_counts".equals(aggType)) {
                String type = (String) row[2];
                Long count = ((Number) row[4]).longValue();
                if ("Abnormal".equalsIgnoreCase(type)) {
                    bucketsType.setAbnormal(FiltersTypeCounts.DocCount.builder().doc_count(count).build());
                } else if ("Indicator of Compromise".equalsIgnoreCase(type) || "IoC".equalsIgnoreCase(type)) {
                    bucketsType.setIndicatorOfCompromise(FiltersTypeCounts.DocCount.builder().doc_count(count).build());
                } else if ("Informational".equalsIgnoreCase(type)) {
                    bucketsType.setInformational(FiltersTypeCounts.DocCount.builder().doc_count(count).build());
                }
            } else if ("category_counts".equals(aggType)) {
                String category = (String) row[3];
                Long count = ((Number) row[4]).longValue();
                categoryBuckets.add(StermsCategory.Bucket.builder()
                        .key(category)
                        .doc_count(count)
                        .build());
            }
        }
        DateHistogramInsightsOverTime dhiot = DateHistogramInsightsOverTime.builder().buckets(buckets).build();
        FiltersTypeCounts filtersTypeCounts = FiltersTypeCounts.builder().buckets(bucketsType).build();
        StermsCategory stermsCategory = StermsCategory.builder()
                .buckets(categoryBuckets)
                .build();
        // Aggregate total doc_count from date_histogram buckets
        double totalDocCount = buckets.stream().mapToDouble(b -> b.getDoc_count()).sum();
        SimpleValueTotalDocCount simpleValueTotalDocCount = SimpleValueTotalDocCount.builder().value(totalDocCount).build();
        Aggregations aggregations = Aggregations.builder()
                .dateHistogramInsightsOverTime(dhiot)
                .filtersTypeCounts(filtersTypeCounts)
                .stermsCategory(stermsCategory)
                .simpleValueTotalDocCount(simpleValueTotalDocCount)
                .build();
        AggregationRoot aggregationRoot = AggregationRoot.builder().aggregations(aggregations).build();
        return aggregationRoot;
    }



    @Transactional
    public void refreshMaterializedView() {
        entityManager.createNativeQuery("REFRESH MATERIALIZED VIEW mv_insights_date_histogram").executeUpdate();
        entityManager.createNativeQuery("REFRESH MATERIALIZED VIEW mv_insights_type_counts").executeUpdate();
        entityManager.createNativeQuery("REFRESH MATERIALIZED VIEW mv_insights_category_counts").executeUpdate();
    }


    @Transactional(readOnly = true)
    //@Override
    public AggregationRoot aggregateInsightsFromMaterializedViews1(String accountId, String investigationId, Date after, Date before) {
        long start = System.currentTimeMillis();

        // Single UNION ALL query across all 3 materialized views — 1 round-trip instead of 3
        String sql = """
            SELECT 'date_histogram' AS agg_type, period AS key, NULL AS type, NULL AS category, count AS doc_count
            FROM mv_insights_date_histogram
            WHERE account_id = :accountId
              AND forensic_info_id = :investigationId
              AND period BETWEEN :after AND :before
            UNION ALL
            SELECT 'type_counts', NULL, type, NULL, SUM(doc_count)
            FROM mv_insights_type_counts
            WHERE original_insight_time BETWEEN :after AND :before
              AND account_id = :accountId
              AND forensic_info_id = :investigationId
            GROUP BY account_id, forensic_info_id, type
            UNION ALL
            SELECT 'category_counts', NULL, NULL, category, SUM(doc_count)
            FROM mv_insights_category_counts
            WHERE original_insight_time BETWEEN :after AND :before
              AND account_id = :accountId
              AND forensic_info_id = :investigationId
            GROUP BY account_id, forensic_info_id, category
        """;

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("accountId", accountId);
        query.setParameter("investigationId", investigationId);
        query.setParameter("after", after);
        query.setParameter("before", before);

        @SuppressWarnings("unchecked")
        List<Object[]> results = query.getResultList();
        long queryElapsed = System.currentTimeMillis() - start;
        log.info("MV UNION ALL query returned {} rows in {}ms", results.size(), queryElapsed);

        // Map results — same pattern as aggregateInsights
        // Step 1: Pre-fill ALL dates in after→before range with doc_count=0 (1-day interval)
        // Use LinkedHashMap to preserve insertion order (chronological)
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        Map<String, long[]> dateCountMap = new LinkedHashMap<>();
        LocalDate startDate = after.toInstant().atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate endDate   = before.toInstant().atZone(ZoneOffset.UTC).toLocalDate();
        for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
            // Represent each day at midnight UTC — matches MV period granularity
            LocalDateTime midnight = d.atStartOfDay();
            long epochMilli = midnight.toInstant(ZoneOffset.UTC).toEpochMilli();
            String keyAsString = midnight.format(formatter);
            dateCountMap.put(keyAsString, new long[]{epochMilli, 0L});
        }

        FiltersTypeCounts.Buckets bucketsType = FiltersTypeCounts.Buckets.builder().build();
        List<StermsCategory.Bucket> categoryBuckets = new ArrayList<>();
        double totalDocCount = 0;

        // Step 2: Overwrite pre-filled zeros with actual counts from MV query
        for (Object[] row : results) {
            String aggType = (String) row[0];
            if ("date_histogram".equals(aggType)) {
                java.sql.Timestamp periodTs = (java.sql.Timestamp) row[1];
                long docCount = ((Number) row[4]).longValue();
                totalDocCount += docCount;
                // Format the MV timestamp to the same key format used in the pre-fill map
                String keyAsString = periodTs.toInstant()
                        .atZone(ZoneOffset.UTC)
                        .toLocalDateTime()
                        .format(formatter);
                if (dateCountMap.containsKey(keyAsString)) {
                    // Overwrite the 0 with the real count
                    dateCountMap.get(keyAsString)[1] = docCount;
                } else {
                    // MV returned a period outside the pre-filled range — add it anyway
                    dateCountMap.put(keyAsString, new long[]{periodTs.toInstant().toEpochMilli(), docCount});
                }
            } else if ("type_counts".equals(aggType)) {
                String type = (String) row[2];
                long count = ((Number) row[4]).longValue();
                if ("Abnormal".equalsIgnoreCase(type)) {
                    bucketsType.setAbnormal(FiltersTypeCounts.DocCount.builder().doc_count(count).build());
                } else if ("Indicator of Compromise".equalsIgnoreCase(type) || "IoC".equalsIgnoreCase(type)) {
                    bucketsType.setIndicatorOfCompromise(FiltersTypeCounts.DocCount.builder().doc_count(count).build());
                } else if ("Informational".equalsIgnoreCase(type)) {
                    bucketsType.setInformational(FiltersTypeCounts.DocCount.builder().doc_count(count).build());
                }
            } else if ("category_counts".equals(aggType)) {
                String category = (String) row[3];
                long count = ((Number) row[4]).longValue();
                categoryBuckets.add(StermsCategory.Bucket.builder()
                        .key(category)
                        .doc_count(count)
                        .build());
            }
        }

        // Step 3: Build sorted bucket list from the pre-filled + merged map
        List<DateHistogramInsightsOverTime.Bucket> buckets = new ArrayList<>();
        dateCountMap.forEach((keyAsString, epochAndCount) ->
                buckets.add(DateHistogramInsightsOverTime.Bucket.builder()
                        .key_as_string(keyAsString)
                        .key(epochAndCount[0])
                        .doc_count(epochAndCount[1])
                        .build())
        );
        // Sort chronologically by key_as_string (LinkedHashMap is already ordered, but sort for safety)
        buckets.sort((a, b) -> a.getKey_as_string().compareTo(b.getKey_as_string()));

        DateHistogramInsightsOverTime dhiot = DateHistogramInsightsOverTime.builder().buckets(buckets).build();
        FiltersTypeCounts filtersTypeCounts = FiltersTypeCounts.builder().buckets(bucketsType).build();
        StermsCategory stermsCategory = StermsCategory.builder().buckets(categoryBuckets).build();
        SimpleValueTotalDocCount simpleValueTotalDocCount = SimpleValueTotalDocCount.builder().value(totalDocCount).build();

        Aggregations aggregations = Aggregations.builder()
                .dateHistogramInsightsOverTime(dhiot)
                .filtersTypeCounts(filtersTypeCounts)
                .stermsCategory(stermsCategory)
                .simpleValueTotalDocCount(simpleValueTotalDocCount)
                .build();
        AggregationRoot aggregationRoot = AggregationRoot.builder().aggregations(aggregations).build();

        long totalElapsed = System.currentTimeMillis() - start;
        log.info("aggregateInsightsFromMaterializedViews completed in {}ms (query: {}ms, mapping: {}ms)",
                totalElapsed, queryElapsed, totalElapsed - queryElapsed);
        return aggregationRoot;
    }

    @Transactional(readOnly = true)
    @Override
    public AggregationRoot aggregateInsightsFromMaterializedViews(
            String accountId, String investigationId, Date after, Date before, String searchTerm) {
        long start = System.currentTimeMillis();

        boolean hasSearch = searchTerm != null && !searchTerm.isBlank();
        String likePattern = hasSearch ? "%" + searchTerm.replace("*", "") + "%" : null;

        // When search is provided:
        //   CTE 'search_range' finds min/max time of matching records across type & category MVs
        //   CTE 'effective_range' collapses to a single (eff_after, eff_before) window
        //   All three MV queries then use COALESCE(eff_after, :after) / COALESCE(eff_before, :before)
        //   so that if no match is found, the original range is used and zero rows are returned.
        //
        // When no search: effective_range simply echoes the caller-supplied :after/:before.

        String searchRangeCte = hasSearch ? """
        search_range AS (
            SELECT MIN(original_insight_time) AS min_time, MAX(original_insight_time) AS max_time
            FROM mv_insights_type_counts
            WHERE account_id = :accountId
              AND forensic_info_id = :investigationId
              AND original_insight_time BETWEEN :after AND :before
              AND type ILIKE :search
            UNION ALL
            SELECT MIN(original_insight_time), MAX(original_insight_time)
            FROM mv_insights_category_counts
            WHERE account_id = :accountId
              AND forensic_info_id = :investigationId
              AND original_insight_time BETWEEN :after AND :before
              AND category ILIKE :search
        ),
        effective_range AS (
            SELECT MIN(min_time) AS eff_after, MAX(max_time) AS eff_before
            FROM search_range
            WHERE min_time IS NOT NULL
        )
        """ : """
        effective_range AS (
            SELECT CAST(:after AS timestamptz) AS eff_after,
                   CAST(:before AS timestamptz) AS eff_before
        )
        """;

        String sql = "WITH " + searchRangeCte + """
        SELECT 'date_histogram' AS agg_type,
               period AS key,
               NULL AS type,
               NULL AS category,
               count AS doc_count
        FROM mv_insights_date_histogram
        CROSS JOIN effective_range
        WHERE account_id = :accountId
          AND forensic_info_id = :investigationId
          AND period BETWEEN eff_after AND eff_before
        UNION ALL
        SELECT 'type_counts', NULL, type, NULL, SUM(doc_count)
        FROM mv_insights_type_counts
        CROSS JOIN effective_range
        WHERE account_id = :accountId
          AND forensic_info_id = :investigationId
          AND original_insight_time BETWEEN eff_after AND eff_before
        GROUP BY type
        UNION ALL
        SELECT 'category_counts', NULL, NULL, category, SUM(doc_count)
        FROM mv_insights_category_counts
        CROSS JOIN effective_range
        WHERE account_id = :accountId
          AND forensic_info_id = :investigationId
          AND original_insight_time BETWEEN eff_after AND eff_before
        GROUP BY category
    """;

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("accountId", accountId);
        query.setParameter("investigationId", investigationId);
        query.setParameter("after", after);
        query.setParameter("before", before);
        if (hasSearch) {
            query.setParameter("search", likePattern);
        }

        @SuppressWarnings("unchecked")
        List<Object[]> results = query.getResultList();
        long queryElapsed = System.currentTimeMillis() - start;
        log.info("MV UNION ALL query (search='{}') returned {} rows in {}ms",
                likePattern, results.size(), queryElapsed);

        return buildAggregationRoot(results, after, before, start, queryElapsed,
                "aggregateInsightsFromMaterializedViews");
    }


    /**
     * Queries insights_info directly with an ILIKE search filter applied across
     * type, category, and description columns.
     * Used when MVs cannot serve the request (e.g. search=Informational*).
     * Uses a single CTE scan of insights_info (instead of 3 separate scans) for performance.
     * Pre-fills missing date buckets with doc_count=0 for the full after→before range.
     */
    @Transactional(readOnly = true)
    @Override
    public AggregationRoot aggregateInsightsWithSearchFilter(String accountId, String investigationId,
                                                              Date after, Date before, String searchTerm) {
        long start = System.currentTimeMillis();

        // Strip wildcard suffix (*) and wrap in SQL ILIKE pattern (%term%)
        String likePattern = "%" + searchTerm.replace("*", "") + "%";

        // CTE 'matched' scans insights_info ONCE and is reused by all three aggregations,
        // avoiding 3 separate sequential scans of the same filtered dataset.
        String sql = """
            WITH matched AS (
                SELECT original_insight_time, type, category
                FROM insights_info
                WHERE account_id = :accountId
                  AND forensic_info_id = :investigationId
                  AND original_insight_time BETWEEN :after AND :before
                  AND (type        ILIKE :search
                    OR category    ILIKE :search
                    OR description ILIKE :search)
            )
            SELECT 'date_histogram' AS agg_type,
                    original_insight_time AS key,
                   NULL AS type,
                   NULL AS category,
                   COUNT(*) AS doc_count
            FROM matched
            GROUP BY original_insight_time
            UNION ALL
            SELECT 'type_counts', NULL, type, NULL, COUNT(*)
            FROM matched
            GROUP BY type
            UNION ALL
            SELECT 'category_counts', NULL, NULL, category, COUNT(*)
            FROM matched
            GROUP BY category
        """;

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("accountId", accountId);
        query.setParameter("investigationId", investigationId);
        query.setParameter("after", after);
        query.setParameter("before", before);
        query.setParameter("search", likePattern);

        @SuppressWarnings("unchecked")
        List<Object[]> results = query.getResultList();
        long queryElapsed = System.currentTimeMillis() - start;
        log.info("aggregateInsightsWithSearchFilter search='{}' returned {} rows in {}ms",
                likePattern, results.size(), queryElapsed);

        return buildAggregationRoot(results, after, before, start, queryElapsed, "aggregateInsightsWithSearchFilter");
    }

    // Aggregates insights using TimescaleDB continuous aggregate (ca_insights_daily)
    @Transactional(readOnly = true)
    @Override
    public AggregationRoot aggregateInsightsFromTimescaleDB(String accountId, String investigationId, Date after, Date before) {
        long start = System.currentTimeMillis();

        String sql = """
            SELECT 'date_histogram' AS agg_type,
                   to_char(period, 'YYYY-MM-DD HH24:MI:SS') AS key,
                   NULL AS type,
                   NULL AS category,
                   SUM(doc_count) AS doc_count
            FROM ca_insights_daily
            WHERE account_id = :accountId
              AND forensic_info_id = :investigationId
              AND period BETWEEN :after AND :before
            GROUP BY period
            UNION ALL
            SELECT 'type_counts', NULL, type, NULL, SUM(doc_count)
            FROM ca_insights_daily
            WHERE account_id = :accountId
              AND forensic_info_id = :investigationId
              AND period BETWEEN :after AND :before
            GROUP BY type
            UNION ALL
            SELECT 'category_counts', NULL, NULL, category, SUM(doc_count)
            FROM ca_insights_daily
            WHERE account_id = :accountId
              AND forensic_info_id = :investigationId
              AND period BETWEEN :after AND :before
            GROUP BY category
        """;

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("accountId", accountId);
        query.setParameter("investigationId", investigationId);
        query.setParameter("after", after);
        query.setParameter("before", before);

        @SuppressWarnings("unchecked")
        List<Object[]> results = query.getResultList();
        long queryElapsed = System.currentTimeMillis() - start;
        log.info("TimescaleDB CA query returned {} rows in {}ms", results.size(), queryElapsed);

        return buildAggregationRoot(results, after, before, start, queryElapsed, "aggregateInsightsFromTimescaleDB");
    }

    /**
     * Shared helper: maps raw query result rows into AggregationRoot.
     * Pre-fills all dates in the after→before range with doc_count=0 at 1-day intervals,
     * then overwrites with actual counts from the query results.
     */
    private AggregationRoot buildAggregationRoot(List<Object[]> results, Date after, Date before,
                                                  long start, long queryElapsed, String callerName) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        // Step 1: Pre-fill all dates with doc_count=0 (1-day interval, midnight UTC)
        Map<String, long[]> dateCountMap = new LinkedHashMap<>();
        LocalDate startDate = after.toInstant().atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate endDate   = before.toInstant().atZone(ZoneOffset.UTC).toLocalDate();
        for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
            LocalDateTime midnight = d.atStartOfDay();
            long epochMilli = midnight.toInstant(ZoneOffset.UTC).toEpochMilli();
            String keyAsString = midnight.format(formatter);
            dateCountMap.put(keyAsString, new long[]{epochMilli, 0L});
        }

        FiltersTypeCounts.Buckets bucketsType = FiltersTypeCounts.Buckets.builder().build();
        List<StermsCategory.Bucket> categoryBuckets = new ArrayList<>();
        double totalDocCount = 0;

        // Step 2: Overwrite zeros with actual counts from query
        for (Object[] row : results) {
            String aggType = (String) row[0];
            if ("date_histogram".equals(aggType)) {
                // row[1] may be a String (direct SQL with to_char) or a Timestamp (MV period column)
                String keyAsString;
                long epochMilli;
                if (row[1] instanceof java.sql.Timestamp ts) {
                    LocalDateTime ldt = ts.toInstant().atZone(ZoneOffset.UTC).toLocalDateTime();
                    keyAsString = ldt.format(formatter);
                    epochMilli  = ts.toInstant().toEpochMilli();
                } else {
                    keyAsString = (String) row[1];
                    epochMilli  = LocalDateTime.parse(keyAsString, formatter)
                            .toInstant(ZoneOffset.UTC).toEpochMilli();
                }
                long docCount = ((Number) row[4]).longValue();
                totalDocCount += docCount;
                if (dateCountMap.containsKey(keyAsString)) {
                    dateCountMap.get(keyAsString)[1] = docCount;
                } else {
                    dateCountMap.put(keyAsString, new long[]{epochMilli, docCount});
                }
            } else if ("type_counts".equals(aggType)) {
                String type = (String) row[2];
                long count = ((Number) row[4]).longValue();
                if ("Abnormal".equalsIgnoreCase(type)) {
                    bucketsType.setAbnormal(FiltersTypeCounts.DocCount.builder().doc_count(count).build());
                } else if ("Indicator of Compromise".equalsIgnoreCase(type) || "IoC".equalsIgnoreCase(type)) {
                    bucketsType.setIndicatorOfCompromise(FiltersTypeCounts.DocCount.builder().doc_count(count).build());
                } else if ("Informational".equalsIgnoreCase(type)) {
                    bucketsType.setInformational(FiltersTypeCounts.DocCount.builder().doc_count(count).build());
                }
            } else if ("category_counts".equals(aggType)) {
                String category = (String) row[3];
                long count = ((Number) row[4]).longValue();
                categoryBuckets.add(StermsCategory.Bucket.builder().key(category).doc_count(count).build());
            }
        }

        // Step 3: Build sorted bucket list
        List<DateHistogramInsightsOverTime.Bucket> buckets = new ArrayList<>();
        dateCountMap.forEach((keyAsString, epochAndCount) ->
                buckets.add(DateHistogramInsightsOverTime.Bucket.builder()
                        .key_as_string(keyAsString)
                        .key(epochAndCount[0])
                        .doc_count(epochAndCount[1])
                        .build())
        );
        buckets.sort(Comparator.comparing(DateHistogramInsightsOverTime.Bucket::getKey_as_string));

        DateHistogramInsightsOverTime dhiot = DateHistogramInsightsOverTime.builder().buckets(buckets).build();
        FiltersTypeCounts filtersTypeCounts = FiltersTypeCounts.builder().buckets(bucketsType).build();
        StermsCategory stermsCategory = StermsCategory.builder().buckets(categoryBuckets).build();
        SimpleValueTotalDocCount simpleValueTotalDocCount = SimpleValueTotalDocCount.builder().value(totalDocCount).build();

        Aggregations aggregations = Aggregations.builder()
                .dateHistogramInsightsOverTime(dhiot)
                .filtersTypeCounts(filtersTypeCounts)
                .stermsCategory(stermsCategory)
                .simpleValueTotalDocCount(simpleValueTotalDocCount)
                .build();

        long totalElapsed = System.currentTimeMillis() - start;
        log.info("{} completed in {}ms (query: {}ms, mapping: {}ms)",
                callerName, totalElapsed, queryElapsed, totalElapsed - queryElapsed);
        return AggregationRoot.builder().aggregations(aggregations).build();
    }
}
