package org.example.migration.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.DTO.forensics.ForensicInfoDto;
import org.example.migration.dto.SearchRequest;
import org.example.migration.exceptions.OpenSearchToPostgresException;
import org.example.migration.models.ForensicInfoQueryRequest;
import org.example.migration.models.InsightInfo;
import org.example.migration.postgres.ForensicInfo;
import org.example.migration.util.ConverterUtil;
import org.example.migration.models.ForensicInfoJson;
import org.example.migration.service.ForensicInfoMigrationService;
import org.example.migration.util.QueryTermUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.example.migration.util.QueryTermUtils.combineFilterStrings;
import static org.springframework.util.StringUtils.hasText;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/migration/v1/opensearch-to-postgres", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "OpenSearchToPostgres", description = "OLD back up code APIs for migrating OpenSearch data to Postgres entities")
public class OpenSearchToPostgresControllerBack {
    private static final Logger logger = LoggerFactory.getLogger(OpenSearchToPostgresController.class);
    private final ForensicInfoMigrationService migrationService;

    @Operation(summary = "Migrate a forensic record to Postgres", description = "Saves a ForensicInfo entity with nested InsightsInfo from a DTO. " +
            "If id is omitted, a UUID will be generated.")
    @ApiResponse(responseCode = "201", description = "Created",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ForensicInfo.class)))
    @ApiResponse(responseCode = "400", description = "Bad Request")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ForensicInfoJson> createForensic(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "ForensicInfoDto payload to migrate", required = true,
                    content = @Content(schema = @Schema(implementation = ForensicInfoDto.class), mediaType = MediaType.APPLICATION_JSON_VALUE))
            @RequestBody ForensicInfoDto forensicInfoDto) throws OpenSearchToPostgresException {

        logger.info("Received request to migrate forensic info: {}", forensicInfoDto);

        if (forensicInfoDto.getId() == null || forensicInfoDto.getId().isBlank()) {
            String generatedId = UUID.randomUUID().toString();
            forensicInfoDto.setId(generatedId);
            logger.info("No ID provided. Generated new ID: {}", generatedId);
        }
        ForensicInfoJson forensicInfoJson = migrationService.saveForensicInfo(forensicInfoDto);

        logger.info("Successfully migrated forensic info with ID: {}", forensicInfoJson.getId());
        return ResponseEntity.created(URI.create("/migration/opensearch-to-postgres/" + forensicInfoJson.getId())).body(forensicInfoJson);
    }

    @Operation(
            summary = "Query forensic records",
            description = "Query forensic records with pagination, date filtering, and search terms. Requires appropriate roles.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Query parameters for forensic info search",
                    required = false
            ),
            parameters = {
                    @io.swagger.v3.oas.annotations.Parameter(name = "after", description = "Start date for filtering (ISO format)", required = false, example = "2024-01-01T00:00:00Z"),
                    @io.swagger.v3.oas.annotations.Parameter(name = "before", description = "End date for filtering (ISO format)", required = false, example = "2024-12-31T23:59:59Z"),
                    @io.swagger.v3.oas.annotations.Parameter(name = "terms", description = "Additional search terms as key-value pairs", required = false)
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "Page of forensic records",
                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ForensicInfoJson.class))),
                    @ApiResponse(responseCode = "400", description = "Bad Request")
            }
    )
    @PostMapping("/query")
    //@JsonView(ForensicInfoJson.Views.Summary.class)
    public Page<ForensicInfoJson> query(
            @RequestBody(required = false) ForensicInfoQueryRequest queryRequest,
            @RequestParam(value = "after", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date after,
            @RequestParam(value = "before", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date before,
            @RequestParam Map<String, String> terms,
            HttpServletRequest request) throws OpenSearchToPostgresException {

        logger.info("Processing query POST {}?{}", request.getRequestURL(), request.getQueryString());
        String accountId = lookupAccountId();
        Map<String, String> filteredTerms = filterTerms(terms);

        // Default values if not provided
        int page = (queryRequest != null && queryRequest.getPage() != null) ? queryRequest.getPage() : 0;
        int size = (queryRequest != null && queryRequest.getSize() != null) ? queryRequest.getSize() : 20;
        Sort sort = ConverterUtil.getSortFromQueryRequest(queryRequest);
        Pageable pageable = PageRequest.of(page, size, sort);

        return migrationService.query(accountId, after, before, pageable, filteredTerms);
    }

    @PostMapping("/insights")
    @Transactional
    @Operation(
            summary = "Query InsightsInfo records",
            description = "Query InsightsInfo records with pagination, date filtering, and search terms. Requires appropriate roles.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Query parameters for insights info search",
                    required = false
            ),
            parameters = {
                    @io.swagger.v3.oas.annotations.Parameter(name = "after", description = "Start date for filtering (ISO format)", required = false, example = "2024-01-01T00:00:00Z"),
                    @io.swagger.v3.oas.annotations.Parameter(name = "before", description = "End date for filtering (ISO format)", required = false, example = "2024-12-31T23:59:59Z"),
                    @io.swagger.v3.oas.annotations.Parameter(name = "terms", description = "Additional search terms as key-value pairs", required = false)
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "Page of insights info records",
                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = InsightInfo.class))),
                    @ApiResponse(responseCode = "400", description = "Bad Request")
            }
    )
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

    @GetMapping("/stats")
    public String stats(@RequestParam(value = "after", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date after,
                        @RequestParam(value = "before", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date before,
                        @RequestParam Map<String, String> terms,
                        @RequestHeader(value = "Z-Client-Timezone", defaultValue = "UTC") String timezoneId,
                        HttpServletRequest request) throws OpenSearchToPostgresException, JsonProcessingException {

        log.debug("Processing stats GET {}?{}", request.getRequestURL(), request.getQueryString());

        return migrationService.stats(before, after, terms, timezoneId, lookupAccountId());
    }

    @GetMapping("/statsByJavaAggregation")
    public String statsByJavaAggregation(@RequestParam(value = "after", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date after,
                                         @RequestParam(value = "before", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date before,
                                         @RequestParam Map<String, String> terms,
                                         @RequestHeader(value = "Z-Client-Timezone", defaultValue = "UTC") String timezoneId,
                                         HttpServletRequest request) throws OpenSearchToPostgresException, JsonProcessingException {

        log.debug("Processing stats GET {}?{}", request.getRequestURL(), request.getQueryString());

        return migrationService.statsByJavaAggregation(before, after, terms, timezoneId, lookupAccountId());
    }

    @GetMapping("/statsByPostGresMaterializedViews")
    public String statsByPostGresMaterializedViews(@RequestParam(value = "after", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date after,
                                                   @RequestParam(value = "before", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date before,
                                                   @RequestParam Map<String, String> terms,
                                                   @RequestHeader(value = "Z-Client-Timezone", defaultValue = "UTC") String timezoneId,
                                                   HttpServletRequest request) throws OpenSearchToPostgresException, JsonProcessingException {

        log.debug("Processing stats GET {}?{}", request.getRequestURL(), request.getQueryString());

        return migrationService.aggregateInsightsFromMaterializedViews(before, after, terms, timezoneId, lookupAccountId());
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


