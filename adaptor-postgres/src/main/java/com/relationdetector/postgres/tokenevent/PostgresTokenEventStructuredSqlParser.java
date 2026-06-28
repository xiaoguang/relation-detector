package com.relationdetector.postgres.tokenevent;

import com.relationdetector.core.tokenevent.PostgresTokenEventSqlEventBuilder;
import com.relationdetector.core.parse.SqlDialect;
import com.relationdetector.core.tokenevent.TokenEventStructuredSqlParser;
import com.relationdetector.core.parse.AntlrSqlParseSupport;
import com.relationdetector.core.parse.AntlrSqlParseSupport.ParsedSql;
import com.relationdetector.core.parse.AntlrSqlParseSupport.SyntaxErrorCounter;
import com.relationdetector.core.antlr.postgres.PostgresRelationSqlLexer;
import com.relationdetector.core.antlr.postgres.PostgresRelationSqlParser;

/**
 * PostgreSQL token-event SQL parser。
 *
 * <p>CN: PostgreSQL 保留自己的 tolerant lexer/parser 入口和 event builder，使
 * {@code ONLY}、{@code ROWS FROM}、{@code UNNEST ... WITH ORDINALITY}、
 * {@code MATERIALIZED} CTE 等 PostgreSQL-only rowset 规则不会泄漏到 MySQL。
 *
 * <p>EN: PostgreSQL token-event SQL parser. PostgreSQL keeps its own tolerant
 * lexer/parser entry point and event builder so PostgreSQL-only rowsets such as
 * ONLY, ROWS FROM, UNNEST WITH ORDINALITY, and MATERIALIZED CTEs stay out of
 * MySQL and the common token-event builder.
 */
public final class PostgresTokenEventStructuredSqlParser extends TokenEventStructuredSqlParser {
    public PostgresTokenEventStructuredSqlParser() {
        super(SqlDialect.POSTGRES, new PostgresTokenEventSqlEventBuilder());
    }

    @Override
    protected ParsedSql parseAntlr(String sql, SyntaxErrorCounter errors) {
        return AntlrSqlParseSupport.parseAntlr(
                sql,
                errors,
                "PostgresRelationSql",
                PostgresRelationSqlLexer.class.getSimpleName(),
                PostgresRelationSqlParser.class.getSimpleName(),
                PostgresRelationSqlLexer::new,
                PostgresRelationSqlParser::new,
                PostgresRelationSqlParser::script);
    }
}
