package org.example.migration.repository;

import org.example.migration.dto.aggregations.AggregationRoot;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;
import java.util.Map;

@Repository
public interface CustomizedInsightsRepository {

    AggregationRoot aggregateInsights(String accountId, String investigationId, Date after, Date before, Map<String, String> terms, String timezoneId);

    AggregationRoot aggregateInsightsFromMaterializedViews(String accountId, String investigationId, Date after, Date before, String searchTerm);

    // Used when a search term is present — queries insights_info directly with ILIKE filter
    AggregationRoot aggregateInsightsWithSearchFilter(String accountId, String investigationId, Date after, Date before, String searchTerm);

    AggregationRoot aggregateInsightsFromTimescaleDB(String accountId, String investigationId, Date after, Date before);
}
