package com.relationdetector.postgres.routine;

import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.spi.AdaptorContext;
import com.relationdetector.contracts.spi.Collectors.StructuredSqlParser;

public interface PlPgSqlBodyParser {
    PlPgSqlParseOutcome parse(
            SqlStatementRecord body,
            AdaptorContext context,
            StructuredSqlParser embeddedSqlParser
    );
}
