/*
package org.example.migration.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.example.migration.dto.aggregations.AggregationRoot;
import org.example.migration.dto.aggregations.Aggregations;
import org.example.migration.dto.aggregations.DateHistogramInsightsOverTime;
import org.example.migration.dto.aggregations.FiltersTypeCounts;
import org.example.migration.dto.aggregations.SimpleValueTotalDocCount;
import org.example.migration.dto.aggregations.StermsCategory;
import org.example.migration.postgres.InsightsInfo;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;
import java.util.Map;


@Repository
//public class CustomizedInsightsRepositoryImpl implements InsightsInfoRepository {
public class CustomizedInsightsRepositoryImplBack implements CustomizedInsightsRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public AggregationRoot aggregateInsights(String accountId, String investigationId, Date after, Date before, Map<String, String> terms, String timezoneId)  {
        // Single SQL Query for All Aggregations
        String sql = """
        WITH periods AS (
            SELECT to_char(d, 'YYYY-MM-DD') AS period
            FROM generate_series(
                :after::date,
                :before::date,
                INTERVAL '1 day'
            ) d
        )
        SELECT
            p.period,
            COUNT(i.insight_id) AS doc_count,
            COUNT(CASE WHEN i.type = 'Abnormal' THEN 1 END) AS abnormal_count,
            COUNT(CASE WHEN i.type = 'Indicator of Compromise' OR i.type = 'IoC' THEN 1 END) AS ioc_count,
            COUNT(CASE WHEN i.type = 'Informational' THEN 1 END) AS informational_count,
            COUNT(CASE WHEN i.category = 'CategoryA' THEN 1 END) AS categoryA_count,
            COUNT(CASE WHEN i.category = 'CategoryB' THEN 1 END) AS categoryB_count
        FROM periods p
        LEFT JOIN insights_info i
            ON to_char(i.original_insight_time, 'YYYY-MM-DD') = p.period
            AND i.account_id = :accountId
            AND i.forensic_info_id = :investigationId
            AND i.original_insight_time BETWEEN :after AND :before
        GROUP BY p.period
        ORDER BY p.period;
        """;
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("accountId", accountId);
        query.setParameter("investigationId", investigationId);
        query.setParameter("after", after);
        query.setParameter("before", before);
        @SuppressWarnings("unchecked")
        List<Object[]> results = query.getResultList();

        // Map SQL results to AggregationRoot POJO
        List<DateHistogramInsightsOverTime.Bucket> buckets = new java.util.ArrayList<>();
        long totalDocCount = 0L;
        long abnormalCount = 0L;
        long iocCount = 0L;
        long informationalCount = 0L;
        long categoryACount = 0L;
        long categoryBCount = 0L;
        for (Object[] row : results) {
            String period = (String) row[0];
            Long docCount = ((Number) row[1]).longValue();
            Long abnormal = ((Number) row[2]).longValue();
            Long ioc = ((Number) row[3]).longValue();
            Long informational = ((Number) row[4]).longValue();
            Long catA = ((Number) row[5]).longValue();
            Long catB = ((Number) row[6]).longValue();
            totalDocCount += docCount;
            abnormalCount += abnormal;
            iocCount += ioc;
            informationalCount += informational;
            categoryACount += catA;
            categoryBCount += catB;
            buckets.add(DateHistogramInsightsOverTime.Bucket.builder()
                    .key_as_string(period)
                    .key(java.time.LocalDate.parse(period).atStartOfDay(java.time.ZoneOffset.UTC).toEpochSecond())
                    .doc_count(docCount)
                    .build());
        }
        DateHistogramInsightsOverTime dhiot = DateHistogramInsightsOverTime.builder().buckets(buckets).build();
        // Map type counts
        FiltersTypeCounts.Buckets bucketsType = FiltersTypeCounts.Buckets.builder()
                .abnormal(FiltersTypeCounts.DocCount.builder().doc_count(abnormalCount).build())
                .indicatorOfCompromise(FiltersTypeCounts.DocCount.builder().doc_count(iocCount).build())
                .informational(FiltersTypeCounts.DocCount.builder().doc_count(informationalCount).build())
                .build();
        FiltersTypeCounts filtersTypeCounts = FiltersTypeCounts.builder().buckets(bucketsType).build();
        // Map category counts
        List<StermsCategory.Bucket> categoryBuckets = new java.util.ArrayList<>();
        categoryBuckets.add(StermsCategory.Bucket.builder().key("CategoryA").doc_count(categoryACount).build());
        categoryBuckets.add(StermsCategory.Bucket.builder().key("CategoryB").doc_count(categoryBCount).build());
        StermsCategory stermsCategory = StermsCategory.builder().buckets(categoryBuckets).build();
        // Map total doc count
        SimpleValueTotalDocCount simpleValueTotalDocCount = SimpleValueTotalDocCount.builder().value((double) totalDocCount).build();
        Aggregations aggregations = Aggregations.builder()
                .dateHistogramInsightsOverTime(dhiot)
                .filtersTypeCounts(filtersTypeCounts)
                .stermsCategory(stermsCategory)
                .simpleValueTotalDocCount(simpleValueTotalDocCount)
                .build();
        AggregationRoot aggregationRoot = AggregationRoot.builder().aggregations(aggregations).build();
        return aggregationRoot;
    }

    @Override
    public AggregationRoot aggregateInsightsFromMaterializedViews(String accountId, String investigationId, Date after, Date before, String searchTerm) {
        return null;
    }

    */
/**
     * @param accountId
     * @param investigationId
     * @param after
     * @param before
     * @return
     *//*


    public AggregationRoot aggregateInsightsFromMaterializedViews1(String accountId, String investigationId, Date after, Date before) {
        return AggregationRoot.builder().build();
    }

    @Override
    public AggregationRoot aggregateInsightsFromTimescaleDB(String accountId, String investigationId, Date after, Date before) {
        return AggregationRoot.builder().build();
    }

    @Override
    public AggregationRoot aggregateInsightsWithSearchFilter(String accountId, String investigationId,
                                                              Date after, Date before, String searchTerm) {
        return AggregationRoot.builder().build();
    }



    public AggregationRoot aggregateFromInsightsList(List<InsightsInfo> insightsList, Date after, Date before, String timezoneId) {
        // 1. Date histogram buckets
        Map<String, Long> dateHistogramMap = new java.util.HashMap<>();
        java.time.LocalDate start = after.toInstant().atZone(java.time.ZoneOffset.UTC).toLocalDate();
        java.time.LocalDate end = before.toInstant().atZone(java.time.ZoneOffset.UTC).toLocalDate();
        for (java.time.LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            dateHistogramMap.put(date.toString(), 0L);
        }
        for (InsightsInfo info : insightsList) {
            //java.time.LocalDate date = info.getOriginalInsightTime().toInstant().atZone(java.time.ZoneOffset.UTC).toLocalDate();
            java.time.LocalDate date = info.getOriginalInsightTime().toInstant().atZone(java.time.ZoneId.of(timezoneId)).toLocalDate();
            String dateStr = date.toString();
            if (dateHistogramMap.containsKey(dateStr)) {
                dateHistogramMap.put(dateStr, dateHistogramMap.get(dateStr) + 1);
            }
        }
        List<DateHistogramInsightsOverTime.Bucket> buckets = new java.util.ArrayList<>();
        for (Map.Entry<String, Long> entry : dateHistogramMap.entrySet()) {
            String keyAsString = entry.getKey();
            Long key = java.time.LocalDate.parse(keyAsString).atStartOfDay(java.time.ZoneOffset.UTC).toEpochSecond();
            Long docCount = entry.getValue();
            buckets.add(DateHistogramInsightsOverTime.Bucket.builder()
                    .key_as_string(keyAsString)
                    .key(key)
                    .doc_count(docCount)
                    .build());
        }
        buckets.sort(java.util.Comparator.comparing(DateHistogramInsightsOverTime.Bucket::getKey_as_string));
        DateHistogramInsightsOverTime dhiot = DateHistogramInsightsOverTime.builder().buckets(buckets).build();

        // 2. Type counts
        FiltersTypeCounts.Buckets bucketsType = FiltersTypeCounts.Buckets.builder().build();
        Map<String, Long> typeCounts = new java.util.HashMap<>();
        for (InsightsInfo info : insightsList) {
            String type = info.getType();
            if (type != null) {
                typeCounts.put(type, typeCounts.getOrDefault(type, 0L) + 1);
            }
        }
        for (Map.Entry<String, Long> entry : typeCounts.entrySet()) {
            String type = entry.getKey();
            Long count = entry.getValue();
            if ("Abnormal".equalsIgnoreCase(type)) {
                bucketsType.setAbnormal(FiltersTypeCounts.DocCount.builder().doc_count(count).build());
            } else if ("Indicator of Compromise".equalsIgnoreCase(type) || "IoC".equalsIgnoreCase(type)) {
                bucketsType.setIndicatorOfCompromise(FiltersTypeCounts.DocCount.builder().doc_count(count).build());
            } else if ("Informational".equalsIgnoreCase(type)) {
                bucketsType.setInformational(FiltersTypeCounts.DocCount.builder().doc_count(count).build());
            }
        }
        FiltersTypeCounts filtersTypeCounts = FiltersTypeCounts.builder().buckets(bucketsType).build();

        // 3. Category counts
        Map<String, Long> categoryCounts = new java.util.HashMap<>();
        for (InsightsInfo info : insightsList) {
            String category = info.getCategory();
            if (category != null) {
                categoryCounts.put(category, categoryCounts.getOrDefault(category, 0L) + 1);
            }
        }
        List<StermsCategory.Bucket> categoryBuckets = new java.util.ArrayList<>();
        for (Map.Entry<String, Long> entry : categoryCounts.entrySet()) {
            categoryBuckets.add(StermsCategory.Bucket.builder()
                    .key(entry.getKey())
                    .doc_count(entry.getValue())
                    .build());
        }
        StermsCategory stermsCategory = StermsCategory.builder()
                .buckets(categoryBuckets)
                .build();

        // 4. Aggregate total doc_count from date_histogram buckets
        double totalDocCount = buckets.stream().mapToDouble(DateHistogramInsightsOverTime.Bucket::getDoc_count).sum();
        SimpleValueTotalDocCount simpleValueTotalDocCount = SimpleValueTotalDocCount.builder().value(totalDocCount).build();

        // 5. Build Aggregations and AggregationRoot
        Aggregations aggregations = Aggregations.builder()
                .dateHistogramInsightsOverTime(dhiot)
                .filtersTypeCounts(filtersTypeCounts)
                .stermsCategory(stermsCategory)
                .simpleValueTotalDocCount(simpleValueTotalDocCount)
                .build();
        AggregationRoot aggregationRoot = AggregationRoot.builder().aggregations(aggregations).build();
        return aggregationRoot;
    }

}
*/
