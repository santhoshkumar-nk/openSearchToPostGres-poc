package org.example.migration.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.migration.dto.aggregations.AggregationRoot;
import org.example.migration.dto.SearchRequest;
import org.example.migration.exceptions.OpenSearchToPostgresException;
import org.example.migration.models.ForensicInfoQueryRequest;
import org.example.migration.models.InsightInfo;
import org.example.migration.util.ConverterUtil;
import org.example.migration.models.ForensicInfoJson;
import org.example.migration.service.ForensicInfoMigrationService;
import org.example.migration.util.QueryTermUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;

import java.net.URI;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.example.migration.util.QueryTermUtils.combineFilterStrings;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/migration/opensearch-to-postgres", produces = MediaType.APPLICATION_JSON_VALUE)
public class OpenSearchToPostgresController {
    private final ForensicInfoMigrationService migrationService;

    // Handles POST requests to create and migrate forensic info from uploaded JSON file
    @PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ForensicInfoJson> upload(
            @RequestPart MultipartFile forensicsFile,
            HttpServletRequest request) throws OpenSearchToPostgresException {

        log.info("Received file: {}, size: {} bytes", forensicsFile.getOriginalFilename(), forensicsFile.getSize());

        ForensicInfoJson forensicInfoJson = migrationService.saveForensicInfo(forensicsFile);

        log.info("Successfully migrated forensic info with ID: {}", forensicInfoJson.getId());
        return ResponseEntity.created(URI.create("/migration/opensearch-to-postgres/" + forensicInfoJson.getId())).body(forensicInfoJson);
    }

    // Handles POST requests to query forensic info with optional filters and pagination
    @PostMapping("/query")
    public Page<ForensicInfoJson> query(
            @RequestBody(required = false) ForensicInfoQueryRequest queryRequest,
            @RequestParam(value = "after", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date after,
            @RequestParam(value = "before", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date before,
            @RequestParam Map<String, String> terms,
            HttpServletRequest request) throws OpenSearchToPostgresException {

        log.info("Processing query POST {}?{}", request.getRequestURL(), request.getQueryString());
        String accountId = lookupAccountId();
        Map<String, String> filteredTerms = filterTerms(terms);

        // Default values if not provided
        int page = (queryRequest != null && queryRequest.getPage() != null) ? queryRequest.getPage() : 0;
        int size = (queryRequest != null && queryRequest.getSize() != null) ? queryRequest.getSize() : 20;
        Sort sort = ConverterUtil.getSortFromQueryRequest(queryRequest);
        Pageable pageable = PageRequest.of(page, size, sort);

        return migrationService.query(accountId, after, before, pageable, filteredTerms);
    }

    // Handles POST requests to fetch insights with filters, pagination, and search terms
    @PostMapping("/insights")
    public Page<InsightInfo> insights(Pageable pageable,
                                      @RequestParam(value = "after", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date after,
                                      @RequestParam(value = "before", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date before,
                                      @RequestParam Map<String, String> terms,
                                      @RequestBody SearchRequest requestBody,
                                      HttpServletRequest request) throws OpenSearchToPostgresException {

        log.debug("Processing insights POST {}?{}", request.getRequestURL(), request.getQueryString());

        Map<String, String> filteredTerms = filterTerms(terms);
        Map<String, String> queryTermsFromRequest = QueryTermUtils.extractQueryAndSearchFromRequest(requestBody);
        Map<String, String> mergedQueryTerms = new HashMap<>(filteredTerms);

        combineFilterStrings(mergedQueryTerms, queryTermsFromRequest);

        return migrationService.search(lookupAccountId(), before, after, pageable, mergedQueryTerms);
    }

    // Handles GET requests to fetch statistics using PostgreSQL CTE-based aggregation (single UNION ALL query on live table)
    @GetMapping("/stats")
    public ResponseEntity<AggregationRoot> stats(@RequestParam(value = "after", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date after,
                        @RequestParam(value = "before", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date before,
                        @RequestParam Map<String, String> terms,
                        @RequestHeader(value = "Z-Client-Timezone", defaultValue = "UTC") String timezoneId,
                        HttpServletRequest request) throws OpenSearchToPostgresException {

        log.debug("Processing stats GET {}?{}", request.getRequestURL(), request.getQueryString());

        return ResponseEntity.ok(migrationService.stats(before, after, terms, timezoneId, lookupAccountId()));
    }

    // Handles GET requests to fetch statistics using Java aggregation
    @GetMapping("/statsByJavaAggregation")
    public ResponseEntity<AggregationRoot> statsByJavaAggregation(@RequestParam(value = "after", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date after,
                    @RequestParam(value = "before", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date before,
                    @RequestParam Map<String, String> terms,
                    @RequestHeader(value = "Z-Client-Timezone", defaultValue = "UTC") String timezoneId,
                    HttpServletRequest request) throws OpenSearchToPostgresException {

        log.debug("Processing stats GET {}?{}", request.getRequestURL(), request.getQueryString());

        return ResponseEntity.ok(migrationService.statsByJavaAggregation(before, after, terms, timezoneId, lookupAccountId()));
    }

    // Handles GET requests to fetch statistics using Postgres materialized views
    @GetMapping("/statsByPostGresMaterializedViews")
    public ResponseEntity<AggregationRoot> statsByPostGresMaterializedViews(@RequestParam(value = "after", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date after,
                    @RequestParam(value = "before", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date before,
                    @RequestParam Map<String, String> terms,
                    @RequestHeader(value = "Z-Client-Timezone", defaultValue = "UTC") String timezoneId,
                    HttpServletRequest request) throws OpenSearchToPostgresException {

        log.debug("Processing stats GET {}?{}", request.getRequestURL(), request.getQueryString());

        return ResponseEntity.ok(migrationService.aggregateInsightsFromMaterializedViews(before, after, terms, timezoneId, lookupAccountId()));
    }

    // Handles GET requests to fetch statistics using direct wildcard (ILIKE) search on insights_info
    // Requires 'search' query param (e.g. ?search=Informational*) and 'forensicInfo.keyword' for investigationId
    // Uses a single CTE scan for date histogram + type counts + category counts
    @GetMapping("/statsByWildcardSearch")
    public ResponseEntity<AggregationRoot> statsByWildcardSearch(
                    @RequestParam(value = "after", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date after,
                    @RequestParam(value = "before", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date before,
                    @RequestParam Map<String, String> terms,
                    @RequestHeader(value = "Z-Client-Timezone", defaultValue = "UTC") String timezoneId,
                    HttpServletRequest request) throws OpenSearchToPostgresException {

        log.debug("Processing statsByWildcardSearch GET {}?{}", request.getRequestURL(), request.getQueryString());

        return ResponseEntity.ok(migrationService.statsByWildcardSearch(before, after, terms, timezoneId, lookupAccountId()));
    }

    // Handles GET requests to fetch statistics using PostgreSQL full-text search (tsvector/tsquery)
    // Requires 'search' query param (e.g. ?search=Informational) and 'forensicInfo.keyword' for investigationId
    // Uses a GIN-indexed tsvector column for fast full-text search aggregation
    @GetMapping("/statsByFullTextTsvector")
    public ResponseEntity<AggregationRoot> statsByFullTextTsvector(
                    @RequestParam(value = "after", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date after,
                    @RequestParam(value = "before", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date before,
                    @RequestParam Map<String, String> terms,
                    @RequestHeader(value = "Z-Client-Timezone", defaultValue = "UTC") String timezoneId,
                    HttpServletRequest request) throws OpenSearchToPostgresException {

        log.debug("Processing statsByFullTextTsvector GET {}?{}", request.getRequestURL(), request.getQueryString());

        return ResponseEntity.ok(migrationService.statsByFullTextTsvector(before, after, terms, timezoneId, lookupAccountId()));
    }

    // Placeholder for lookupAccountId (implement as needed)
    private String lookupAccountId() {
        // TODO: Implement account ID lookup logic
        return "09ddea50-2dda-4804-9b1c-906eacf41197";
    }

    // Placeholder for filterTerms (implement as needed)
    private Map<String, String> filterTerms(Map<String, String> terms) {
        // TODO: Implement filtering logic if needed
        return terms;
    }

}
