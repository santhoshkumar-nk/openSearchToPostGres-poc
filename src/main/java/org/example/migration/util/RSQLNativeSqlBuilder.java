package org.example.migration.util;

import cz.jirutka.rsql.parser.RSQLParser;
import cz.jirutka.rsql.parser.ast.AndNode;
import cz.jirutka.rsql.parser.ast.ComparisonNode;
import cz.jirutka.rsql.parser.ast.Node;
import cz.jirutka.rsql.parser.ast.OrNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts an RSQL "query" term into a native SQL AND fragment with LOWER()
 * for case-insensitive filtering, suitable for appending to a WHERE clause.
 *
 * Examples:
 *   type=in=(ioc,informational)
 *     → AND LOWER(type) IN (LOWER(:fv_0), LOWER(:fv_1))
 *
 *   type=in=(ioc,informational);category=in=(messaging,crash)
 *     → AND (LOWER(type) IN (LOWER(:fv_0), LOWER(:fv_1)) AND LOWER(category) IN (LOWER(:fv_2), LOWER(:fv_3)))
 */
@Component
public class RSQLNativeSqlBuilder {

    /**
     * Holds the generated SQL fragment and its bound parameter map.
     * fragment : e.g. "AND LOWER(type) IN (LOWER(:fv_0), LOWER(:fv_1))"
     * params   : e.g. { "fv_0" -> "ioc", "fv_1" -> "informational" }
     */
    public record RSQLFilterResult(String fragment, Map<String, String> params) {
        public static RSQLFilterResult empty() {
            return new RSQLFilterResult("", new LinkedHashMap<>());
        }
    }

    /**
     * Parses the "query" value from the terms map and builds a native SQL AND fragment.
     * Returns an empty result when no "query" key is present or its value is blank.
     */
    public RSQLFilterResult buildFilterFragment(Map<String, String> terms) {
        if (terms == null || !terms.containsKey("query")) {
            return RSQLFilterResult.empty();
        }
        String rsql = terms.get("query");
        if (rsql == null || rsql.isBlank()) {
            return RSQLFilterResult.empty();
        }
        Node rootNode = new RSQLParser().parse(rsql);
        Map<String, String> params = new LinkedHashMap<>();
        String fragment = buildNodeFragment(rootNode, params);
        if (fragment.isBlank()) {
            return RSQLFilterResult.empty();
        }
        return new RSQLFilterResult("AND " + fragment, params);
    }

    /**
     * Recursively walks an RSQL AST node and produces a SQL fragment (without leading AND).
     *
     * Supported operators:
     *   ==    → LOWER(col) = LOWER(:fv_N)
     *   !=    → LOWER(col) != LOWER(:fv_N)
     *   =in=  → LOWER(col) IN (LOWER(:fv_N), ...)
     *   =out= → LOWER(col) NOT IN (LOWER(:fv_N), ...)
     *   ;     → (fragment AND fragment)
     *   ,     → (fragment OR fragment)
     */
    private String buildNodeFragment(Node node, Map<String, String> params) {
        if (node instanceof AndNode andNode) {
            List<String> parts = new ArrayList<>();
            for (Node child : andNode.getChildren()) {
                String part = buildNodeFragment(child, params);
                if (!part.isBlank()) parts.add(part);
            }
            return parts.isEmpty() ? "" : "(" + String.join(" AND ", parts) + ")";

        } else if (node instanceof OrNode orNode) {
            List<String> parts = new ArrayList<>();
            for (Node child : orNode.getChildren()) {
                String part = buildNodeFragment(child, params);
                if (!part.isBlank()) parts.add(part);
            }
            return parts.isEmpty() ? "" : "(" + String.join(" OR ", parts) + ")";

        } else if (node instanceof ComparisonNode cn) {
            String column = cn.getSelector();
            List<String> values = cn.getArguments();
            // params.size() guarantees unique param names across all nodes in the tree
            int baseIndex = params.size();

            return switch (cn.getOperator().getSymbol()) {
                case "==" -> {
                    String paramName = "fv_" + baseIndex;
                    params.put(paramName, values.get(0));
                    yield "LOWER(" + column + ") = LOWER(:" + paramName + ")";
                }
                case "!=" -> {
                    String paramName = "fv_" + baseIndex;
                    params.put(paramName, values.get(0));
                    yield "LOWER(" + column + ") != LOWER(:" + paramName + ")";
                }
                case "=in=" -> {
                    StringBuilder inClause = new StringBuilder("LOWER(").append(column).append(") IN (");
                    for (int i = 0; i < values.size(); i++) {
                        String paramName = "fv_" + (baseIndex + i);
                        if (i > 0) inClause.append(", ");
                        inClause.append("LOWER(:").append(paramName).append(")");
                        params.put(paramName, values.get(i));
                    }
                    inClause.append(")");
                    yield inClause.toString();
                }
                case "=out=" -> {
                    StringBuilder notInClause = new StringBuilder("LOWER(").append(column).append(") NOT IN (");
                    for (int i = 0; i < values.size(); i++) {
                        String paramName = "fv_" + (baseIndex + i);
                        if (i > 0) notInClause.append(", ");
                        notInClause.append("LOWER(:").append(paramName).append(")");
                        params.put(paramName, values.get(i));
                    }
                    notInClause.append(")");
                    yield notInClause.toString();
                }
                default -> throw new UnsupportedOperationException("Operator not supported: " + cn.getOperator());
            };
        }
        return "";
    }
}

