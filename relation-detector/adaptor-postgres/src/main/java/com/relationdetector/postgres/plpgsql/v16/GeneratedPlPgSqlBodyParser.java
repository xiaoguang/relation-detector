package com.relationdetector.postgres.plpgsql.v16;

import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.spi.AdaptorContext;
import com.relationdetector.contracts.spi.Collectors.StructuredSqlParser;
import com.relationdetector.postgres.routine.GeneratedPlPgSqlBodyParserSupport;
import com.relationdetector.postgres.routine.PlPgSqlBodyParser;
import com.relationdetector.postgres.routine.PlPgSqlParseOutcome;

public final class GeneratedPlPgSqlBodyParser implements PlPgSqlBodyParser {
    @Override
    public PlPgSqlParseOutcome parse(SqlStatementRecord body, AdaptorContext context,
            StructuredSqlParser embeddedSqlParser) {
        return GeneratedPlPgSqlBodyParserSupport.parse(body, context, embeddedSqlParser,
                PlPgSqlLexer::new, PlPgSqlParser::new, PlPgSqlParser::script,
                root -> new PlPgSqlStaticStatementCollector(body.sql()).collect(root));
    }
}
