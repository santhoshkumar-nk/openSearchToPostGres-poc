package org.example.migration.util;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import cz.jirutka.rsql.parser.RSQLParser;
import lombok.AllArgsConstructor;
import org.example.migration.dto.SearchRequest;
import org.example.migration.postgres.ForensicInfo;
import org.example.migration.postgres.InsightsInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import static org.example.migration.impl.ForensicInfoRepositoryImpl.RSQLToPredicateConverter;
import static org.springframework.util.StringUtils.hasText;

@Service
@AllArgsConstructor
public class QueryTermUtils {
    private final ConverterUtil converterUtil;
    private final RSQLParser rsqlParser;
    private static final Logger log = LoggerFactory.getLogger(QueryTermUtils.class);

    public static Map<String, String> extractQueryAndSearchFromRequest(SearchRequest request) {
        Map<String, String> termsFromRequestBody = new HashMap<>();
        if (request != null) {
            if (hasText(request.getQuery())) {
                termsFromRequestBody.put("query", request.getQuery());
            }
            if (hasText(request.getSearch())) {
                termsFromRequestBody.put("search", request.getSearch());
            }
        }
        return termsFromRequestBody;
    }

    public static void combineFilterStrings(Map<String, String> mergedQueryTerms, Map<String, String> queryTermsFromRequest) {
        final String queryString = "query";
        final String searchString = "search";
        String newQueryStringValue = null;
        String newSearchStringValue = null;
        if (mergedQueryTerms.containsKey(queryString) && queryTermsFromRequest.containsKey(queryString)) {
            if (hasText(queryTermsFromRequest.get(queryString))) {
                newQueryStringValue = "(" + mergedQueryTerms.get(queryString) + ");(" + queryTermsFromRequest.get(queryString) + ")";
            }
        } else if (!mergedQueryTerms.containsKey(queryString) && queryTermsFromRequest.containsKey(queryString)) {
            if (hasText(queryTermsFromRequest.get(queryString))) {
                newQueryStringValue = queryTermsFromRequest.get(queryString);
            }
        }
        if (hasText(newQueryStringValue)) {
            mergedQueryTerms.put(queryString, newQueryStringValue);
        }
        if (mergedQueryTerms.containsKey(searchString) && queryTermsFromRequest.containsKey(searchString)) {
            if (hasText(queryTermsFromRequest.get(searchString))) {
                newSearchStringValue = mergedQueryTerms.get(searchString) + " " + queryTermsFromRequest.get(searchString);
            }
        } else if (!mergedQueryTerms.containsKey(searchString) && queryTermsFromRequest.containsKey(searchString)) {
            if (hasText(queryTermsFromRequest.get(searchString))) {
                newSearchStringValue = queryTermsFromRequest.get(searchString);
            }
        }
        if (hasText(newSearchStringValue)) {
            mergedQueryTerms.put(searchString, newSearchStringValue);
        }
    }

    public static Map<String, String> mergeQueryTerms(Map<String, String> terms, SearchRequest requestBody) {
        Map<String, String> filteredTerms = terms != null ? new HashMap<>(terms) : new HashMap<>();
        Map<String, String> queryTermsFromRequest = extractQueryAndSearchFromRequest(requestBody);
        Map<String, String> mergedQueryTerms = new HashMap<>(filteredTerms);
        combineFilterStrings(mergedQueryTerms, queryTermsFromRequest);
        return mergedQueryTerms;
    }

    private static <T> Specification<T> constructQueryBuilderGeneric(
            String accountId,
            Map<String, String> terms,
            BiFunction<Root<T>, CriteriaBuilder, Predicate> searchFilter,
            RSQLParser rsqlParser,
            String entityName) {
        return (Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            List<String> logConditions = new ArrayList<>();
            List<Object> logParams = new ArrayList<>();

            // Filter by accountId
            predicates.add(cb.equal(root.get("accountId"), accountId));
            logConditions.add("accountId = ?");
            logParams.add(accountId);

            // Filter by terms
            if (terms != null && !terms.isEmpty()) {
                for (Map.Entry<String, String> entry : terms.entrySet()) {
                    String key = entry.getKey();
                    String value = entry.getValue();

                    if (key.endsWith(".keyword")) {
                        if (entityName.equals("insightInfo")) {
                            predicates.add(cb.equal(root.get(key.replace(".keyword", "")).get("id"), value));
                        } else {
                            predicates.add(cb.equal(root.get(key.replace(".keyword", "")), value));
                        }
                        logConditions.add(key.replace(".keyword", "") + " = ?");
                        logParams.add(value);
                    } else if (key.equals("search")) {
                        predicates.add(searchFilter.apply(root, cb));
                        logConditions.add("full-text search on multiple fields");
                        logParams.add(value);
                    } else if (key.equals("query")) {
                        cz.jirutka.rsql.parser.ast.Node rootNode = rsqlParser.parse(value);
                        Predicate rsqlPredicate = RSQLToPredicateConverter(rootNode, root, cb);
                        predicates.add(rsqlPredicate);
                        logConditions.add("RSQL: " + value);
                        logParams.add(value);
                    } else if (value != null && (value.contains("*") || value.contains("%"))) {
                        String sqlWildcard = value.replace("*", "%");
                        predicates.add(cb.like(root.get(key), sqlWildcard));
                        logConditions.add(key + " LIKE ?");
                        logParams.add(sqlWildcard);
                    } else {
                        predicates.add(cb.equal(root.get(key), value));
                        logConditions.add(key + " = ?");
                        logParams.add(value);
                    }
                }
            }

            Predicate finalPredicate = cb.and(predicates.toArray(new Predicate[0]));
            log.info("Specification conditions: {}", logConditions);
            log.info("Specification parameters: {}", logParams);
            log.info("Constructed Specification Predicate: {}", finalPredicate);
            return finalPredicate;
        };
    }

    public Specification<ForensicInfo> constructQueryBuilder(
            String accountId, Map<String, String> terms) {
        return constructQueryBuilderGeneric(
                accountId,
                terms,
                (root, cb) -> converterUtil.filterAll(root, cb, terms != null && terms.containsKey("search") ? terms.get("search") : ""),
                rsqlParser, "forensicInfo"
        );
    }

    public Specification<InsightsInfo> constructQueryBuilderForInsights(
            String accountId, Map<String, String> terms) {
        return constructQueryBuilderGeneric(
                accountId,
                terms,
                (root, cb) -> converterUtil.filterAllInsights(root, cb, terms != null && terms.containsKey("search") ? terms.get("search") : ""),
                rsqlParser, "insightInfo"
        );
    }
}

