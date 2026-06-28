package com.relationdetector.mysql.tokenevent;

import com.relationdetector.core.tokenevent.MySqlTokenEventSqlEventBuilder;
import com.relationdetector.core.parse.SqlDialect;
import com.relationdetector.core.tokenevent.TokenEventStructuredSqlParser;
import com.relationdetector.core.parse.AntlrSqlParseSupport;
import com.relationdetector.core.parse.AntlrSqlParseSupport.ParsedSql;
import com.relationdetector.core.parse.AntlrSqlParseSupport.SyntaxErrorCounter;
import com.relationdetector.core.antlr.mysql.MySqlRelationSqlLexer;
import com.relationdetector.core.antlr.mysql.MySqlRelationSqlParser;

/**
 * MySQL token-event SQL parser。
 *
 * <p>CN: MySQL 保留自己的 tolerant lexer/parser 入口和 event builder，使
 * {@code STRAIGHT_JOIN}、index hints、{@code PARTITION}、{@code JSON_TABLE} 等
 * MySQL-only 语法不会泄漏到 PostgreSQL 或公共 token-event builder。
 *
 * <p>EN: MySQL token-event SQL parser. MySQL keeps its own tolerant
 * lexer/parser entry point and event builder so MySQL-only syntax such as
 * STRAIGHT_JOIN, index hints, PARTITION, and JSON_TABLE does not leak into
 * PostgreSQL or the common token-event builder.
 */
public final class MySqlTokenEventStructuredSqlParser extends TokenEventStructuredSqlParser {
    public MySqlTokenEventStructuredSqlParser() {
        super(SqlDialect.MYSQL, new MySqlTokenEventSqlEventBuilder());
    }

    @Override
    protected ParsedSql parseAntlr(String sql, SyntaxErrorCounter errors) {
        return AntlrSqlParseSupport.parseAntlr(
                sql,
                errors,
                "MySqlRelationSql",
                MySqlRelationSqlLexer.class.getSimpleName(),
                MySqlRelationSqlParser.class.getSimpleName(),
                MySqlRelationSqlLexer::new,
                MySqlRelationSqlParser::new,
                MySqlRelationSqlParser::script);
    }
}
