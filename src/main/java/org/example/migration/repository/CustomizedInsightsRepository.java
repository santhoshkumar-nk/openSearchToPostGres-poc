package org.example.migration.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.Map;

@Repository
public interface CustomizedInsightsRepository {

    String aggregateInsights(String accountId, String investigationId, Date after, Date before, Map<String, String> terms, String timezoneId) throws JsonProcessingException;

    String aggregateInsightsFromMaterializedViews(String accountId, String investigationId, Date after, Date before) throws JsonProcessingException;
}
