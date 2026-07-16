package com.relationdetector.postgres.plpgsql.tokenevent;

import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.spi.AdaptorContext;
import com.relationdetector.contracts.spi.Collectors.StructuredSqlParser;
import com.relationdetector.postgres.routine.GeneratedPlPgSqlBodyParserSupport;
import com.relationdetector.postgres.routine.PlPgSqlBodyParser;
import com.relationdetector.postgres.routine.PlPgSqlParseOutcome;

/**
 * CN: 使用 compact token-event PL/pgSQL shell grammar 定位过程结构和 static SQL，并回调 PostgreSQL token-event SQL parser；
 * 不加载任何 versioned full-grammar parser。
 * EN: Uses the compact token-event PL/pgSQL shell grammar to locate procedural structure and static SQL, then calls
 * the PostgreSQL token-event SQL parser; it never loads a versioned full-grammar parser.
 */
public final class TokenEventPlPgSqlBodyParser implements PlPgSqlBodyParser {
    @Override
    public PlPgSqlParseOutcome parse(SqlStatementRecord body, AdaptorContext context,
            StructuredSqlParser embeddedSqlParser) {
        return GeneratedPlPgSqlBodyParserSupport.parse(body, context, embeddedSqlParser,
                PlPgSqlLexer::new, PlPgSqlParser::new, PlPgSqlParser::script,
                root -> new PlPgSqlShellCollector(body.sql()).collect(root));
    }
}
