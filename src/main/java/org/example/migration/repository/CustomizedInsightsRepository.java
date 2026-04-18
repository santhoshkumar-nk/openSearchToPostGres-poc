package org.example.migration.repository;

import org.example.migration.dto.aggregations.AggregationRoot;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.Map;

@Repository
public interface CustomizedInsightsRepository {

    AggregationRoot aggregateInsights(String accountId, String investigationId, Date after, Date before, Map<String, String> terms, String timezoneId);

    AggregationRoot aggregateInsightsFromMaterializedViews(String accountId, String investigationId, Date after, Date before);
}
