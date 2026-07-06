package com.relationdetector.oracle.fullgrammer.common;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.antlr.v4.runtime.ParserRuleContext;

import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredSqlEvent;

/**
 * Shared state and helpers for Oracle SQL typed visitors.
 *
 * <p>CN: 这里只保存 visitor 状态、source line 和 identifier 清洗等公共辅助；
 * SQL 结构判断继续由各版本 generated context override 完成。
 *
 * <p>EN: Keeps shared visitor state, source line handling, and identifier
 * cleanup. SQL structure decisions remain in version generated-context
 * visitors.
 */
public final class OracleSqlEventVisitorCore {
    private final SqlStatementRecord statement;
    private final List<StructuredSqlEvent> events = new ArrayList<>();

    public OracleSqlEventVisitorCore(SqlStatementRecord statement) {
        this.statement = statement;
    }

    public List<StructuredSqlEvent> events() {
        return List.copyOf(events);
    }

    public Map<String, Object> attrs() {
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("tokenEventNative", true);
        return attrs;
    }

    public void add(StructuredParseEventType type, ParserRuleContext ctx, Map<String, Object> attrs) {
        events.add(new StructuredSqlEvent(type, statement.sourceName(), line(ctx), attrs));
    }

    public long line(ParserRuleContext ctx) {
        if (ctx == null || ctx.getStart() == null) {
            return statement.startLine();
        }
        return statement.startLine() + Math.max(0, ctx.getStart().getLine() - 1);
    }

    public String clean(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.length() >= 2
                && ((trimmed.startsWith("\"") && trimmed.endsWith("\""))
                || (trimmed.startsWith("`") && trimmed.endsWith("`")))) {
            return trimmed.substring(1, trimmed.length() - 1).replace(trimmed.substring(0, 1) + trimmed.substring(0, 1),
                    trimmed.substring(0, 1));
        }
        return trimmed;
    }

    public String normalize(String value) {
        return clean(value).toLowerCase(Locale.ROOT);
    }

    public String baseName(String qualified) {
        if (qualified == null) {
            return "";
        }
        int dot = qualified.lastIndexOf('.');
        return dot < 0 ? qualified : qualified.substring(dot + 1);
    }

    public static Map<String, Object> fullGrammerAttributes() {
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("fullGrammerNative", true);
        return attrs;
    }
}
