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
    @Override
    public AggregationRoot aggregateInsightsFromMaterializedViews(String accountId, String investigationId, Date after, Date before) {
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
}
