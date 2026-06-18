package com.relationdetector.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.relationdetector.api.AdaptorContext;
import com.relationdetector.api.Collectors.StructuredDdlParser;
import com.relationdetector.api.StructuredParseResult;

/**
 * ANTLR-backed structural DDL parser placeholder for dialect adaptors.
 *
 * <p>The DDL path intentionally does not feed an entire schema dump through the
 * SQL statement parser. Real {@code SHOW CREATE TABLE} fixtures can contain
 * thousands of lines, table options, comments, generated columns, and index
 * clauses that are not query expressions. The DDL visitor is the semantic
 * boundary for this path: it extracts schema-definition events directly and the
 * {@link DdlRelationExtractionVisitor} turns those events into relationship
 * candidates. SQL text still uses {@link AntlrStructuredSqlParser}; DDL text has
 * this separate entry so the two primary-switch pipelines can be verified and
 * optimized independently.
 */
public class AntlrStructuredDdlParser implements StructuredDdlParser {
    private final SqlDialect dialect;
    private final DdlStructuredEventVisitor eventVisitor = new DdlStructuredEventVisitor();

    public AntlrStructuredDdlParser(SqlDialect dialect) {
        this.dialect = dialect;
    }

    @Override
    public StructuredParseResult parseDdl(String ddl, String sourceName, AdaptorContext context) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("ddlMode", true);
        attributes.put("ddlEventVisitor", eventVisitor.getClass().getSimpleName());
        return new StructuredParseResult("ANTLR", dialect.name(), sourceName,
                eventVisitor.extractEvents(ddl, sourceName), List.of(), attributes);
    }
}
