package com.relationdetector.postgres.routine;

import java.util.LinkedHashMap;
import java.util.Map;

import org.antlr.v4.runtime.ParserRuleContext;

import com.relationdetector.contracts.parse.SqlStatementRecord;

/**
 *
 * Creates an embedded SQL statement from a typed outer-grammar context span.
 */
public final class PostgresRoutineStatementFactory {
    private PostgresRoutineStatementFactory() {
    }

    public static SqlStatementRecord fromContext(SqlStatementRecord outer, ParserRuleContext context) {
        int start = context.getStart().getStartIndex();
        int stop = context.getStop().getStopIndex() + 1;
        if (start < 0 || stop <= start || stop > outer.sql().length()) {
            throw new IllegalArgumentException("Invalid PostgreSQL routine statement context span");
        }
        long startLine = outer.startLine() + context.getStart().getLine() - 1L;
        long endLine = outer.startLine() + context.getStop().getLine() - 1L;
        Map<String, Object> attributes = new LinkedHashMap<>(outer.attributes());
        attributes.put("sourceLine", startLine);
        return new SqlStatementRecord(outer.sql().substring(start, stop).strip(), outer.sourceType(),
                outer.sourceName(), startLine, endLine, attributes);
    }
}
