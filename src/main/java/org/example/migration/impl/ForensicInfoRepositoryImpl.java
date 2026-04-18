package org.example.migration.impl;

import cz.jirutka.rsql.parser.RSQLParser;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.migration.repository.CustomizedForensicsRepository;
import org.example.migration.util.RSQLCriteriaVisitor;
import org.example.migration.postgres.ForensicInfo;
import org.example.model.forensics.ForensicInfoJson;
import org.example.util.ForensicsMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class ForensicInfoRepositoryImpl implements CustomizedForensicsRepository {

    private final ForensicsMapper forensicsMapper;
    private final RSQLParser rsqlParser;

    @PersistenceContext
    private EntityManager entityManager;

    // Helper class to hold query and params
    private static class QueryBuilderResult {
        String whereClause;
        List<Object> params;
        QueryBuilderResult(String whereClause, List<Object> params) {
            this.whereClause = whereClause;
            this.params = params;
        }
    }

    private QueryBuilderResult constructQueryBuilderWhere(
            String accountId, Date after, Date before,
            Map<String, String> terms) {

        StringBuilder whereClause = new StringBuilder("account_id = ?");
        List<Object> params = new ArrayList<>();
        params.add(accountId);

        if (!CollectionUtils.isEmpty(terms)) {
            for (Map.Entry<String, String> entry : terms.entrySet()) {
                String k = entry.getKey();
                String v = entry.getValue();
                if (k.endsWith(".keyword")) {
                    whereClause.append(" AND ").append(k.replace(".keyword", "")).append(" = ?");
                    params.add(v);
                } else if (k.equals("search")) {
                    whereClause.append(" AND to_tsvector('english', coalesce(insights, '')) @@ plainto_tsquery(?)");
                    params.add(v);
                } else if (k.equals("query")) {
                    cz.jirutka.rsql.parser.ast.Node rootNode = rsqlParser.parse(v);
                    String rsqlWhere = rootNode.accept(new RSQLNodeToSqlVisitor(), null);
                    whereClause.append(" AND ").append(rsqlWhere);
                } else {
                    whereClause.append(" AND ").append(k).append(" ILIKE ?");
                    params.add("%" + v + "%");
                }
            }
        }
        return new QueryBuilderResult(whereClause.toString(), params);
    }

    @Override
    public String aggregateForensics(String accountId, Date after, Date before, Map<String, String> terms, Module module, String timezoneId) {
        return "";
    }


    public Page<ForensicInfoJson> query1(String accountId, Date after, Date before, Pageable pageable, Map<String, String> terms) {
        QueryBuilderResult queryBuilderResult = constructQueryBuilderWhere(accountId, after, before, terms);
        String baseQuery = "SELECT * FROM forensic_info WHERE " + queryBuilderResult.whereClause;
        String countQueryStr = "SELECT COUNT(*) FROM forensic_info WHERE " + queryBuilderResult.whereClause;

        // Create count query for total elements
        Query countQuery = entityManager.createNativeQuery(countQueryStr);
        for (int i = 0; i < queryBuilderResult.params.size(); i++) {
            countQuery.setParameter(i + 1, queryBuilderResult.params.get(i));
        }
        Number totalElements = ((Number) countQuery.getSingleResult());

        // Create main query for paginated results
        Query nativeQuery = entityManager.createNativeQuery(baseQuery, ForensicInfo.class);
        for (int i = 0; i < queryBuilderResult.params.size(); i++) {
            nativeQuery.setParameter(i + 1, queryBuilderResult.params.get(i));
        }
        nativeQuery.setFirstResult((int) pageable.getOffset());
        nativeQuery.setMaxResults(pageable.getPageSize());
        @SuppressWarnings("unchecked")
        List<ForensicInfo> resultList = nativeQuery.getResultList();
        List<ForensicInfoJson> jsonList = resultList.stream().map(forensicsMapper::toForensicInfoJson).collect(Collectors.toList());
        return new org.springframework.data.domain.PageImpl<>(jsonList, pageable, totalElements.longValue());
    }


    /*@Override
    public Page<ForensicInfoJson> query(String accountId, Date after, Date before, Pageable pageable, Map<String, String> terms) {
        Specification<ForensicInfo> spec = constructQueryBuilder(accountId, terms);
        Page<ForensicInfo> page = repository.findAll(spec, pageable);
        return page.map(forensicsMapper::toForensicInfoJson);
    }*/

    public static Predicate RSQLToPredicateConverter(cz.jirutka.rsql.parser.ast.Node rootNode, Root<?> root, CriteriaBuilder cb) {
        return rootNode.accept(
                new RSQLCriteriaVisitor(),
                new RSQLCriteriaVisitor.Context(root, cb)
        );
    }

}
