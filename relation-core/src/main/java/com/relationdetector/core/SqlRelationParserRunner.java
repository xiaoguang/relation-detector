package com.relationdetector.core;

import java.util.List;

import com.relationdetector.api.AdaptorContext;
import com.relationdetector.api.Collectors.SqlRelationParser;
import com.relationdetector.api.DatabaseAdaptor;
import com.relationdetector.api.RelationshipCandidate;
import com.relationdetector.api.SqlStatementRecord;

/**
 * Applies runtime SQL parser mode selection around the adaptor parser.
 *
 * <p>Design mapping: SQL Parser Primary 切换设计计划. Adaptors continue to expose
 * their best parser stack through SPI, while this runner controls how a scan
 * uses that stack:
 *
 * <pre>{@code
 * ScanEngine.safeParseStatement(...)
 *   -> SqlRelationParserRunner.parse(...)
 *      -> simple / antlr-shadow / antlr-primary
 * }</pre>
 */
public final class SqlRelationParserRunner {
    public List<RelationshipCandidate> parse(
            DatabaseAdaptor adaptor,
            ScanConfig config,
            SqlStatementRecord statement,
            AdaptorContext context
    ) {
        if (SqlLogNoiseFilter.shouldSkip(config, statement)) {
            return List.of();
        }
        SqlStatementRecord effectiveStatement = withParserPolicyAttributes(config, statement);
        SqlRelationParser parser = adaptor.sqlRelationParser();
        if (parser instanceof ShadowSqlRelationParser shadow) {
            return parseShadowCapable(shadow, config, effectiveStatement, context);
        }
        return parser.parse(effectiveStatement, context);
    }

    private SqlStatementRecord withParserPolicyAttributes(ScanConfig config, SqlStatementRecord statement) {
        java.util.Map<String, Object> attributes = new java.util.LinkedHashMap<>(statement.attributes());
        attributes.put("logSystemSchemas", java.util.List.copyOf(SqlLogNoiseFilter.effectiveSystemSchemas(config)));
        return new SqlStatementRecord(statement.sql(), statement.sourceType(), statement.sourceName(),
                statement.startLine(), statement.endLine(), attributes);
    }

    private List<RelationshipCandidate> parseShadowCapable(
            ShadowSqlRelationParser parser,
            ScanConfig config,
            SqlStatementRecord statement,
            AdaptorContext context
    ) {
        return switch (config.sqlParserMode) {
            case SIMPLE -> parser.parsePrimary(statement, context);
            case ANTLR_SHADOW -> parser.parseWithDiagnostics(statement, context).relationships();
            case ANTLR_PRIMARY -> parseAntlrPrimary(parser, config, statement, context);
        };
    }

    /**
     * Runs ANTLR as candidate primary while preserving the no-loss guarantee.
     *
     * <p>If ANTLR misses any Simple baseline relation and fallback is enabled,
     * the scan keeps Simple output and emits a warning carrying the raw SQL and
     * missing fingerprints. This is the runtime counterpart of the golden
     * comparison tests.
     */
    private List<RelationshipCandidate> parseAntlrPrimary(
            ShadowSqlRelationParser parser,
            ScanConfig config,
            SqlStatementRecord statement,
            AdaptorContext context
    ) {
        ShadowSqlRelationParser.Result result = parser.parseWithDiagnostics(statement, context);
        if (!result.missingSimpleRelations().isEmpty() && config.sqlParserFallbackOnFailure) {
            if (context != null) {
                context.warn(DiagnosticWarnings.antlrPrimaryFallback(
                        statement,
                        "ANTLR relation extraction missed Simple baseline relationships",
                        result.missingSimpleRelations()));
            }
            return result.relationships();
        }
        return result.shadowRelationships();
    }
}
