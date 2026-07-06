package com.relationdetector.postgres.fullgrammer.common;

import java.util.List;

import org.antlr.v4.runtime.ParserRuleContext;

import com.relationdetector.contracts.parse.StructuredSqlEvent;

/**
 * PostgreSQL version-specific DDL grammar binding.
 *
 * <p>CN: 由版本包提供 root parse 和 DDL typed collector。公共 DDL parser 只负责
 * warning、attributes 和错误处理。
 *
 * <p>EN: Version packages provide root parsing and the DDL typed collector. The
 * shared DDL parser handles warnings, attributes, and failure handling.
 */
public interface PostgresFullGrammerDdlBinding {
    int majorVersion();

    String lexerName();

    String parserName();

    String collectorName();

    AbstractPostgresFullGrammerStructuredDdlParser.FullGrammerDdlParse parseDdl(String ddl);

    List<StructuredSqlEvent> collectEvents(String sourceName, ParserRuleContext root);
}
