package com.relationdetector.postgres.routine;

import java.util.LinkedHashMap;
import java.util.Map;

import org.antlr.v4.runtime.ParserRuleContext;

import com.relationdetector.contracts.parse.SqlStatementRecord;

/**
 * CN: 把 outer grammar 已 typed 定位的 SQL context span 转成带绝对行号和 routine provenance 的 SqlStatementRecord；非法 span 明确失败，不搜索 SQL 文本。
 * EN: Converts a SQL context span located by the typed outer grammar into a SqlStatementRecord with absolute lines and routine provenance. Invalid spans fail explicitly without text searching.
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
