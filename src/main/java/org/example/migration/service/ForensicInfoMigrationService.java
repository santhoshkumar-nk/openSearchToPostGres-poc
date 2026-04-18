package org.example.migration.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.example.DTO.forensics.ForensicInfoDto;
import org.example.migration.dto.aggregations.AggregationRoot;
import org.example.migration.dto.aggregations.Aggregations;
import org.example.migration.dto.aggregations.DateHistogramInsightsOverTime;
import org.example.migration.dto.aggregations.FiltersTypeCounts;
import org.example.migration.dto.aggregations.SimpleValueTotalDocCount;
import org.example.migration.dto.aggregations.StermsCategory;
import org.example.migration.models.InsightInfo;
import org.example.migration.postgres.ForensicInfo;
import org.example.migration.postgres.InsightsInfo;
import org.example.migration.repository.InsightsInfoRepository;
import org.example.migration.util.ConverterUtil;
import org.example.migration.models.ForensicInfoJson;
import org.example.migration.repository.ForensicInfoRepository;
import org.example.migration.exceptions.OpenSearchToPostgresException;
import org.example.migration.util.ForensicsMapperPostGres;
import org.example.migration.util.QueryTermUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Comparator;

import static java.util.Objects.isNull;
import static org.example.migration.util.ConverterUtil.convertToPostgresEntity;

@Slf4j
@Service
@RequiredArgsConstructor
public class ForensicInfoMigrationService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ForensicInfoRepository forensicInfoRepository;
    private final InsightsInfoRepository insightsInfoRepository;
    private final ForensicsMapperPostGres forensicsMapperPostGres;
    private final ConverterUtil converterUtil;
    private final QueryTermUtils queryTermUtils;

    @Transactional
    public ForensicInfoJson saveForensicInfo(ForensicInfoDto dto) throws OpenSearchToPostgresException {
        ForensicInfoJson json = converterUtil.convertDto(dto);
        ForensicInfo entity = convertToPostgresEntity(json);
        if (entity == null) {
            throw new OpenSearchToPostgresException("Failed to convert ForensicInfoJson to ForensicInfo entity");
        }
        entity.computeCount();
        if (entity.getInsights() != null) {
            entity.getInsights().forEach(i -> i.setForensicInfo(entity));
        }
        try {
            forensicInfoRepository.save(entity);
        } catch (Exception e) {
            throw new OpenSearchToPostgresException("Failed to save ForensicInfo entity", e);
        }
        ForensicInfoJson result = forensicsMapperPostGres.toForensicInfoJson(entity);
        if (result == null) {
            throw new OpenSearchToPostgresException("Failed to map ForensicInfo entity to ForensicInfoJson");
        }
        return result;
    }


    public Page<ForensicInfoJson> query1(String accountId, Date before, Date after, Pageable pageable, Map<String, String> terms) {
        return null;
    }

    public Page<ForensicInfoJson> query(String accountId, Date after, Date before, Pageable pageable, Map<String, String> terms) {
        Specification<ForensicInfo> spec = queryTermUtils.constructQueryBuilder(accountId, terms);
        log.info("Pageable ForensicInfo query params: pageNumber={}, pageSize={}, offset={}, sort={}",
                pageable.getPageNumber(), pageable.getPageSize(), pageable.getOffset(), pageable.getSort());
        Page<ForensicInfo> page = forensicInfoRepository.findAll(spec, pageable);
        return page.map(forensicsMapperPostGres::toForensicInfoJson);
    }

    public Page<InsightInfo> search(String accountId, Date before, Date after, Pageable pageable, Map<String, String> terms) throws OpenSearchToPostgresException {
        String investigationId = terms.get("forensicInfo.keyword");
        if (StringUtils.isEmpty(investigationId)) {
            throw new OpenSearchToPostgresException("investigation not found");
        }

        Specification<InsightsInfo> spec = queryTermUtils.constructQueryBuilderForInsights(accountId, terms);
        log.info("Pageable insights search params: pageNumber={}, pageSize={}, offset={}, sort={}",
                pageable.getPageNumber(), pageable.getPageSize(), pageable.getOffset(), pageable.getSort());
        Page<InsightsInfo> page = insightsInfoRepository.findAll(spec, pageable);
        return page.map(forensicsMapperPostGres::toInsightInfo);
    }

    /**
     * Resolves after/before date bounds using a lightweight native query
     * instead of loading the full ForensicInfo entity + lazy insights proxy.
     * Returns Date[]{after, before} with nulls filled in from DB.
     */
    private Date[] resolveTimeBounds(String accountId, String investigationId, Date after, Date before) {
        if (isNull(after) || isNull(before)) {
            long start = System.currentTimeMillis();
            Object[] results = forensicInfoRepository.findInsightTimeBounds(accountId, investigationId);
            long elapsed = System.currentTimeMillis() - start;
            log.info("findInsightTimeBounds completed in {}ms", elapsed);

            if (results != null && results.length > 0) {
                // Native query returns Object[] where each element is a row.
                // Single row with 2 columns: results[0] is the row (Object[]) containing [min, max].
                Object[] row;
                if (results[0] instanceof Object[]) {
                    row = (Object[]) results[0];
                } else {
                    // If Spring unwraps it directly (single row), results itself is [min, max]
                    row = results;
                }

                if (row.length >= 2) {
                    if (isNull(after) && row[0] != null) {
                        after = toDate(row[0]);
                    }
                    if (isNull(before) && row[1] != null) {
                        before = toDate(row[1]);
                    }
                }
            }
        }
        return new Date[]{after, before};
    }

    private Date toDate(Object value) {
        if (value instanceof Date) {
            return (Date) value;
        } else if (value instanceof Timestamp) {
            return new Date(((Timestamp) value).getTime());
        } else if (value instanceof java.time.LocalDateTime) {
            return Date.from(((java.time.LocalDateTime) value).atZone(ZoneOffset.UTC).toInstant());
        } else if (value instanceof java.time.OffsetDateTime) {
            return Date.from(((java.time.OffsetDateTime) value).toInstant());
        }
        throw new IllegalArgumentException("Cannot convert " + value.getClass().getName() + " to Date");
    }

    private String validateAndGetInvestigationId(Map<String, String> terms) throws OpenSearchToPostgresException {
        String investigationId = terms.get("forensicInfo.keyword");
        if (StringUtils.isEmpty(investigationId)) {
            throw new OpenSearchToPostgresException("investigation not found");
        }
        return investigationId;
    }

    public String stats(Date before, Date after, Map<String, String> terms, String timezoneId, String accountId) throws OpenSearchToPostgresException, JsonProcessingException {
        String investigationId = validateAndGetInvestigationId(terms);

        // Use lightweight query instead of loading full entity
        Date[] bounds = resolveTimeBounds(accountId, investigationId, after, before);
        after = bounds[0];
        before = bounds[1];

        return insightsInfoRepository.aggregateInsights(accountId, investigationId, after, before, terms, timezoneId);
    }

    public String aggregateInsightsFromMaterializedViews(Date before, Date after, Map<String, String> terms, String timezoneId, String accountId) throws OpenSearchToPostgresException, JsonProcessingException {
        String investigationId = validateAndGetInvestigationId(terms);

        // Use lightweight query instead of loading full entity
        Date[] bounds = resolveTimeBounds(accountId, investigationId, after, before);
        after = bounds[0];
        before = bounds[1];

        return insightsInfoRepository.aggregateInsightsFromMaterializedViews(accountId, investigationId, after, before);
    }

    public String statsByJavaAggregation(Date before, Date after, Map<String, String> terms, String timezoneId, String accountId) throws OpenSearchToPostgresException, JsonProcessingException {
        String investigationId = validateAndGetInvestigationId(terms);

        // Use lightweight query instead of loading full entity
        Date[] bounds = resolveTimeBounds(accountId, investigationId, after, before);
        after = bounds[0];
        before = bounds[1];

        Specification<InsightsInfo> spec = queryTermUtils.constructQueryBuilderForInsights(accountId, terms);

        long fetchStart = System.currentTimeMillis();
        List<InsightsInfo> insightsList = insightsInfoRepository.findAll(spec);
        long fetchElapsed = System.currentTimeMillis() - fetchStart;
        log.info("findAll(spec) returned {} rows in {}ms", insightsList.size(), fetchElapsed);

        return aggregateFromInsightsList(insightsList, after, before, timezoneId);
    }

    /**
     * Single-pass aggregation: iterates insightsList once to compute
     * date histogram, type counts, and category counts simultaneously.
     */
    public String aggregateFromInsightsList(List<InsightsInfo> insightsList, Date after, Date before, String timezoneId) {
        long start = System.currentTimeMillis();

        ZoneId zone = ZoneId.of(timezoneId);
        LocalDate startDate = after.toInstant().atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate endDate = before.toInstant().atZone(ZoneOffset.UTC).toLocalDate();

        // Pre-fill date histogram with zeros
        Map<String, Long> dateHistogramMap = new HashMap<>();
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            dateHistogramMap.put(date.toString(), 0L);
        }

        // Single pass: compute all three aggregations at once
        Map<String, Long> typeCounts = new HashMap<>();
        Map<String, Long> categoryCounts = new HashMap<>();

        for (InsightsInfo info : insightsList) {
            // Date histogram
            LocalDate date = info.getOriginalInsightTime().toInstant().atZone(zone).toLocalDate();
            String dateStr = date.toString();
            dateHistogramMap.merge(dateStr, 1L, Long::sum);

            // Type counts
            String type = info.getType();
            if (type != null) {
                typeCounts.merge(type, 1L, Long::sum);
            }

            // Category counts
            String category = info.getCategory();
            if (category != null) {
                categoryCounts.merge(category, 1L, Long::sum);
            }
        }

        // Build date histogram buckets
        List<DateHistogramInsightsOverTime.Bucket> buckets = new ArrayList<>();
        for (Map.Entry<String, Long> entry : dateHistogramMap.entrySet()) {
            String keyAsString = entry.getKey();
            long key = LocalDate.parse(keyAsString).atStartOfDay(ZoneOffset.UTC).toEpochSecond();
            buckets.add(DateHistogramInsightsOverTime.Bucket.builder()
                    .key_as_string(keyAsString)
                    .key(key)
                    .doc_count(entry.getValue())
                    .build());
        }
        buckets.sort(Comparator.comparing(DateHistogramInsightsOverTime.Bucket::getKey_as_string));
        DateHistogramInsightsOverTime dhiot = DateHistogramInsightsOverTime.builder().buckets(buckets).build();

        // Build type counts
        FiltersTypeCounts.Buckets bucketsType = FiltersTypeCounts.Buckets.builder().build();
        typeCounts.forEach((type, count) -> {
            if ("Abnormal".equalsIgnoreCase(type)) {
                bucketsType.setAbnormal(FiltersTypeCounts.DocCount.builder().doc_count(count).build());
            } else if ("Indicator of Compromise".equalsIgnoreCase(type) || "IoC".equalsIgnoreCase(type)) {
                bucketsType.setIndicatorOfCompromise(FiltersTypeCounts.DocCount.builder().doc_count(count).build());
            } else if ("Informational".equalsIgnoreCase(type)) {
                bucketsType.setInformational(FiltersTypeCounts.DocCount.builder().doc_count(count).build());
            }
        });
        FiltersTypeCounts filtersTypeCounts = FiltersTypeCounts.builder().buckets(bucketsType).build();

        // Build category counts
        List<StermsCategory.Bucket> categoryBuckets = new ArrayList<>();
        categoryCounts.forEach((category, count) ->
                categoryBuckets.add(StermsCategory.Bucket.builder()
                        .key(category)
                        .doc_count(count)
                        .build())
        );
        StermsCategory stermsCategory = StermsCategory.builder().buckets(categoryBuckets).build();

        // Total doc count
        double totalDocCount = buckets.stream().mapToDouble(DateHistogramInsightsOverTime.Bucket::getDoc_count).sum();
        SimpleValueTotalDocCount simpleValueTotalDocCount = SimpleValueTotalDocCount.builder().value(totalDocCount).build();

        // Build final result
        Aggregations aggregations = Aggregations.builder()
                .dateHistogramInsightsOverTime(dhiot)
                .filtersTypeCounts(filtersTypeCounts)
                .stermsCategory(stermsCategory)
                .simpleValueTotalDocCount(simpleValueTotalDocCount)
                .build();
        AggregationRoot aggregationRoot = AggregationRoot.builder().aggregations(aggregations).build();

        try {
            String json = OBJECT_MAPPER.writeValueAsString(aggregationRoot);
            long elapsed = System.currentTimeMillis() - start;
            log.info("aggregateFromInsightsList completed in {}ms for {} insights", elapsed, insightsList.size());
            return json;
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
