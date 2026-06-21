package com.relationdetector.core.tokenevent;

import com.relationdetector.core.parse.SqlDialect;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.relationdetector.contracts.spi.AdaptorContext;
import com.relationdetector.contracts.spi.Collectors.StructuredDdlParser;
import com.relationdetector.contracts.parse.StructuredParseResult;
import com.relationdetector.core.ddl.DdlStructuredEventVisitor;
import com.relationdetector.core.ddl.MySqlDdlStructuredEventVisitor;
import com.relationdetector.core.ddl.PostgresDdlStructuredEventVisitor;

/**
 * token-event DDL parser 基类。
 *
 * <p>CN: DDL 不复用 SQL parser，因为 schema definition 与 query/DML 的结构和语义不同。
 * 本类选择方言 DDL event visitor，并把 DDL text 转成统一 StructuredParseResult。
 *
 * <p>EN: Base DDL parser for the token-event pipeline. DDL does not reuse the
 * SQL parser because schema definition text has different structure and
 * semantics from query/DML text. This class selects the dialect DDL event
 * visitor and returns a StructuredParseResult.
 */
public class TokenEventStructuredDdlParser implements StructuredDdlParser {
    private final SqlDialect dialect;
    private final DdlStructuredEventVisitor eventVisitor;

    public TokenEventStructuredDdlParser(SqlDialect dialect) {
        this(dialect, eventVisitorFor(dialect));
    }

    protected TokenEventStructuredDdlParser(SqlDialect dialect, DdlStructuredEventVisitor eventVisitor) {
        this.dialect = dialect;
        this.eventVisitor = eventVisitor;
    }

    /**
     * 解析 DDL 文本并返回 DDL 结构事件。
     *
     * <p>EN: Parses DDL text and returns DDL structured events.
     */
    @Override
    public StructuredParseResult parseDdl(String ddl, String sourceName, AdaptorContext context) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("ddlMode", true);
        attributes.put("ddlEventVisitor", eventVisitor.getClass().getSimpleName());
        attributes.put("backend", "ANTLR_TOKEN_EVENT_DDL");
        attributes.put("tokenEvent", true);
        attributes.put("tokenEventPrimary", true);
        return new StructuredParseResult("ANTLR_TOKEN_EVENT_DDL", dialect.name(), sourceName,
                eventVisitor.extractEvents(ddl, sourceName), List.of(), attributes);
    }

    private static DdlStructuredEventVisitor eventVisitorFor(SqlDialect dialect) {
        return switch (dialect) {
            case MYSQL -> new MySqlDdlStructuredEventVisitor();
            case POSTGRES -> new PostgresDdlStructuredEventVisitor();
            default -> new DdlStructuredEventVisitor();
        };
    }
}
