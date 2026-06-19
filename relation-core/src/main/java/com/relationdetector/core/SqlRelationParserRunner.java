package com.relationdetector.core;

import java.util.List;

import com.relationdetector.api.AdaptorContext;
import com.relationdetector.api.Collectors.SqlRelationParser;
import com.relationdetector.api.DatabaseAdaptor;
import com.relationdetector.api.RelationshipCandidate;
import com.relationdetector.api.SqlStatementRecord;

/**
 * Applies common SQL parser pre-processing before invoking the adaptor parser.
 *
 * <p>Design mapping: SQL parser now uses the Token/Event primary pipeline for
 * MySQL/PostgreSQL. The runner no longer selects removed parser modes or
 * performs parser fallback; it only filters native-log noise and passes
 * dialect system-schema policy through statement attributes:
 *
 * <pre>{@code
 * ScanEngine.safeParseStatement(...)
 *   -> SqlRelationParserRunner.parse(...)
 *      -> adaptor.sqlRelationParser()
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
        return parser.parse(effectiveStatement, context);
    }

    private SqlStatementRecord withParserPolicyAttributes(ScanConfig config, SqlStatementRecord statement) {
        java.util.Map<String, Object> attributes = new java.util.LinkedHashMap<>(statement.attributes());
        attributes.put("logSystemSchemas", java.util.List.copyOf(SqlLogNoiseFilter.effectiveSystemSchemas(config)));
        return new SqlStatementRecord(statement.sql(), statement.sourceType(), statement.sourceName(),
                statement.startLine(), statement.endLine(), attributes);
    }
}
