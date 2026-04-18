package org.example.migration.impl;

import cz.jirutka.rsql.parser.ast.*;
import org.apache.el.parser.Node;
import cz.jirutka.rsql.parser.ast.RSQLVisitor;

import java.util.stream.Collectors;

public class RSQLNodeToSqlVisitor implements RSQLVisitor<String, Void> {

    @Override
    public String visit(AndNode node, Void param) {
        return node.getChildren().stream()
                .map(child -> child.accept(this, param))
                .collect(Collectors.joining(" AND ", "(", ")"));
    }

    @Override
    public String visit(OrNode node, Void param) {
        return node.getChildren().stream()
                .map(child -> child.accept(this, param))
                .collect(Collectors.joining(" OR ", "(", ")"));
    }

    @Override
    public String visit(ComparisonNode node, Void param) {
        String selector = node.getSelector();
        String operator = node.getOperator().getSymbol();
        String argument = node.getArguments().get(0); // Only handles single argument

        switch (operator) {
            case "==":
                return selector + " = '" + argument + "'";
            case "!=":
                return selector + " <> '" + argument + "'";
            case "=gt=":
                return selector + " > '" + argument + "'";
            case "=lt=":
                return selector + " < '" + argument + "'";
            case "=ge=":
                return selector + " >= '" + argument + "'";
            case "=le=":
                return selector + " <= '" + argument + "'";
            default:
                throw new UnsupportedOperationException("Operator not supported: " + operator);
        }
    }
}
