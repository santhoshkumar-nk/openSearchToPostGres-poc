package org.example.migration.util;

import cz.jirutka.rsql.parser.ast.*;
import jakarta.persistence.criteria.*;

public class RSQLCriteriaVisitor implements RSQLVisitor<Predicate, RSQLCriteriaVisitor.Context> {
    public static class Context {
        public final Root<?> root;
        public final CriteriaBuilder cb;
        public Context(Root<?> root, CriteriaBuilder cb) {
            this.root = root;
            this.cb = cb;
        }
    }

    @Override
    public Predicate visit(AndNode node, Context context) {
        return context.cb.and(
                node.getChildren().stream()
                        .map(child -> child.accept(this, context))
                        .toArray(Predicate[]::new)
        );
    }

    @Override
    public Predicate visit(OrNode node, Context context) {
        return context.cb.or(
                node.getChildren().stream()
                        .map(child -> child.accept(this, context))
                        .toArray(Predicate[]::new)
        );
    }

    @Override
    public Predicate visit(ComparisonNode node, Context context) {
        String selector = node.getSelector();
        String argument = node.getArguments().get(0);
        Path<?> path = context.root.get(selector);

        switch (node.getOperator().getSymbol()) {
            case "==":
                return context.cb.equal(path, argument);
            case "!=":
                return context.cb.notEqual(path, argument);
            case "=gt=":
                return context.cb.greaterThan(path.as(String.class), argument);
            case "=lt=":
                return context.cb.lessThan(path.as(String.class), argument);
            case "=ge=":
                return context.cb.greaterThanOrEqualTo(path.as(String.class), argument);
            case "=le=":
                return context.cb.lessThanOrEqualTo(path.as(String.class), argument);
            default:
                throw new UnsupportedOperationException("Operator not supported: " + node.getOperator());
        }
    }
}

