package com.relationdetector.postgres.fullgrammer.common;

import java.util.List;

import org.antlr.v4.runtime.ParserRuleContext;

import com.relationdetector.contracts.parse.StructuredSqlEvent;

/**
 * Shared PostgreSQL full-grammer DDL parser implementation.
 *
 * <p>CN: 使用版本 binding 注入 generated parser 和 DDL typed collector，公共类统一
 * warning、attributes 和 parser lifecycle。
 *
 * <p>EN: Shared PostgreSQL full-grammer DDL parser. Version bindings inject the
 * generated parser and DDL typed collector while this class centralizes
 * warnings, attributes, and parser lifecycle.
 */
public final class PostgresFullGrammerStructuredDdlParser extends AbstractPostgresFullGrammerStructuredDdlParser {
    private final PostgresFullGrammerDdlBinding binding;

    public PostgresFullGrammerStructuredDdlParser(PostgresFullGrammerDdlBinding binding) {
        this.binding = binding;
    }

    @Override
    protected int majorVersion() {
        return binding.majorVersion();
    }

    @Override
    protected String lexerName() {
        return binding.lexerName();
    }

    @Override
    protected String parserName() {
        return binding.parserName();
    }

    @Override
    protected String collectorName() {
        return binding.collectorName();
    }

    @Override
    protected FullGrammerDdlParse parseFullGrammer(String ddl) {
        return binding.parseDdl(ddl);
    }

    @Override
    protected List<StructuredSqlEvent> collectEvents(String sourceName, ParserRuleContext root) {
        return binding.collectEvents(sourceName, root);
    }
}
