package org.example.migration.repository;

import java.util.Date;
import java.util.Map;

public interface CustomizedForensicsRepository {

    String aggregateForensics(String accountId, Date after, Date before, Map<String, String> terms, Module module, String timezoneId);
}
