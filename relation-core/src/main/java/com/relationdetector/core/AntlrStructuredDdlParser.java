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
 * <p>The class currently validates DDL through the same tolerant ANTLR grammar
 * used for SQL text and returns diagnostic metadata. Relationship extraction
 * for DDL still comes from MySQL/Postgres DDL parsers and {@link SimpleDdlParser}
 * until a dedicated DDL visitor is promoted from shadow diagnostics.
 */
public class AntlrStructuredDdlParser implements StructuredDdlParser {
    private final SqlDialect dialect;

    public AntlrStructuredDdlParser(SqlDialect dialect) {
        this.dialect = dialect;
    }

    @Override
    public StructuredParseResult parseDdl(String ddl, String sourceName, AdaptorContext context) {
        var statement = new com.relationdetector.api.SqlStatementRecord(
                ddl,
                com.relationdetector.api.Enums.StatementSourceType.DDL_FILE,
                sourceName,
                1,
                1,
                Map.of());
        StructuredParseResult sqlResult = new AntlrStructuredSqlParser(dialect).parseSql(statement, context);
        Map<String, Object> attributes = new LinkedHashMap<>(sqlResult.attributes());
        attributes.put("ddlMode", true);
        return new StructuredParseResult(sqlResult.backend(), sqlResult.dialect(), sourceName,
                sqlResult.events(), sqlResult.warnings(), attributes);
    }
}
