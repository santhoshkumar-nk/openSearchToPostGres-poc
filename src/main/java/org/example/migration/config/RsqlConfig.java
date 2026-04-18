package org.example.migration.config;

import cz.jirutka.rsql.parser.RSQLParser;
import cz.jirutka.rsql.parser.ast.ComparisonOperator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


import java.util.Set;

@Configuration
public class RsqlConfig {

   /* @Bean
    public RSQLParser rsqlParser() {
        return new RSQLParser(SpringJpaRsqlVisitor.supportedOperators());*/


@Bean
public RSQLParser rsqlParser() {
    return new RSQLParser();
}

   /* @Bean
    public RSQLParser opensearchRsqlParser() {
        Set<ComparisonOperator> operators = ComparisonOperatorProxy.defaultOperators();
        return new RSQLParser(operators);
    }*/
}

