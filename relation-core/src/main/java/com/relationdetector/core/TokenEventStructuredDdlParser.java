package com.relationdetector.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.relationdetector.api.AdaptorContext;
import com.relationdetector.api.Collectors.StructuredDdlParser;
import com.relationdetector.api.StructuredParseResult;

/**
 * Production DDL parser for the Token/Event pipeline.
 *
 * <p>DDL does not reuse the SQL statement parser. It has its own event visitor
 * because schema definition text has different structure and semantics from SQL
 * query/DML text.
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
