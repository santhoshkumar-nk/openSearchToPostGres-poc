# openSearchToPostGres

A POC for migrating OpenSearch tables to PostgreSQL database.

## Project Structure

This project is organized under the `org.example.api.migration` package, which contains the main logic for migration, API endpoints, models, repositories, services, utilities, and exception handling.

### Main Packages and Their Responsibilities

- **api**: Contains REST controllers for migration APIs, including endpoints for forensic and insights data.
- **models**: DTOs and data models used for API requests and responses (e.g., `ForensicInfoJson`, `ForensicInfoQueryRequest`, `InsightInfo`).
- **postgres**: JPA entity classes representing the PostgreSQL tables (e.g., `ForensicInfo`, `InsightsInfo`).
- **repository**: Spring Data JPA repositories for database access (e.g., `ForensicInfoRepository`, `InsightsInfoRepository`).
- **service**: Business logic and migration services (e.g., `ForensicInfoMigrationService`).
- **util**: Utility classes for mapping, conversion, and query building (e.g., `ConverterUtil`).
- **exceptions**: Custom exception classes and global exception handlers for API error management.

## Key Features

- **Migration APIs**: Endpoints to migrate data from OpenSearch to PostgreSQL, including creation and querying of forensic and insights records.
- **DTO and Entity Mapping**: Clear separation between API models (DTOs) and database entities, with conversion utilities.
- **Advanced Querying**: Support for filtering, full-text search, wildcard search, and RSQL-based dynamic queries.
- **Pagination and Sorting**: All query APIs support pagination and sorting via request parameters or body.
- **Swagger/OpenAPI Documentation**: All APIs are documented using Swagger annotations for easy exploration and testing.
- **Exception Handling**: Centralized exception handling for consistent API error responses.

## Main API Endpoints

### ForensicInfo APIs
- `POST /migration/opensearch-to-postgres`  
  Create a new forensic record. Accepts a `ForensicInfoDto` payload and returns the created `ForensicInfoJson`.
- `POST /migration/opensearch-to-postgres/query`  
  Query forensic records with pagination, filtering, and search. Accepts a `ForensicInfoQueryRequest` in the body and returns a paginated list of `ForensicInfoJson`.

### InsightsInfo APIs
- `POST /migration/opensearch-to-postgres/insights`  
  Create a new insights record. Accepts an `InsightsInfo` payload and returns the created entity.
- `POST /migration/opensearch-to-postgres/insights`  
  Query insights records with pagination, filtering, and search. Accepts a `SearchRequest` in the body and returns a paginated list of `InsightInfo`.

## Entities and DTOs

### ForensicInfo (Entity)
Represents the `forensic_info` table in PostgreSQL. Key fields:
- id, accountId, message, outputLog, backupName, sysDiagnose, deviceType, osVersion, deviceId, zdeviceId, checksum
- timeTriggerAnalysis, timeStartAnalysis, uploadedTime, location, deviceOwnerName, policyTriggerInfo
- investigationFileSize, investigationLocation, devicePatchLevel, workstationOs, workstationUsage
- collectorVersion, collectorUsage, earliestInsightTime, latestInsightTime
- suspiciousCount, iocCount, informationalCount, suspicious, ioc, informational
- List<InsightsInfo> insights (child table)

### InsightsInfo (Entity)
Represents the `insights_info` table in PostgreSQL. Key fields:
- insightId, forensicInfo (parent reference), customAll, accountId, category, type, description, ruleName, ruleVersion
- intention, generatedTime, originalInsightTime, location, deviceOwnerName, policyTriggerInfo, sourceFileName
- relatedInvestigations (JSONB), attributeInformation (JSONB), suspicious, ioc, informational

### ForensicInfoJson (DTO)
API model for forensic records, used in requests and responses. Mirrors most fields of `ForensicInfo` entity, but designed for API use.

### ForensicInfoQueryRequest (DTO)
API model for querying forensic records. Fields: page, size, sort, and other filter/search terms.

### InsightInfo (DTO)
API model for insights records, used in requests and responses.

## Utilities
- **ConverterUtil**: Handles conversion between DTOs and entities, builds dynamic query specifications, and supports wildcard and RSQL search.
- **ForensicsMapper**: Maps between OpenSearch and Postgres models.

## Exception Handling
- All exceptions from migration APIs are handled by custom exception classes and a global exception handler in the `exceptions` package.

## How to Run
1. Build the project with Gradle: `./gradlew build`
2. Run the Spring Boot application: `./gradlew bootRun`
3. Access Swagger UI at: `http://localhost:8180/swagger-ui.html` (or the configured port)

## Git Ignore
Make sure your `.gitignore` includes:
```
build/
.gradle/
.idea/
```

## License
This project is for demonstration and POC purposes.

