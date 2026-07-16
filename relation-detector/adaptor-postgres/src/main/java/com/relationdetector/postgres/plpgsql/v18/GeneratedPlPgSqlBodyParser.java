package com.relationdetector.postgres.plpgsql.v18;

import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.spi.AdaptorContext;
import com.relationdetector.contracts.spi.Collectors.StructuredSqlParser;
import com.relationdetector.postgres.routine.GeneratedPlPgSqlBodyParserSupport;
import com.relationdetector.postgres.routine.PlPgSqlBodyParser;
import com.relationdetector.postgres.routine.PlPgSqlParseOutcome;

/**
 * CN: 使用 PostgreSQL 18 PL/pgSQL shell grammar 定位 static SQL，并仅回调 v18 full-grammar SQL parser。
 * EN: Uses the PostgreSQL 18 PL/pgSQL shell grammar to locate static SQL and calls only the v18 full-grammar parser.
 */
public final class GeneratedPlPgSqlBodyParser implements PlPgSqlBodyParser {
    @Override
    public PlPgSqlParseOutcome parse(SqlStatementRecord body, AdaptorContext context,
            StructuredSqlParser embeddedSqlParser) {
        return GeneratedPlPgSqlBodyParserSupport.parse(body, context, embeddedSqlParser,
                PlPgSqlLexer::new, PlPgSqlParser::new, PlPgSqlParser::script,
                root -> new PlPgSqlShellCollector(body.sql()).collect(root));
    }
}
