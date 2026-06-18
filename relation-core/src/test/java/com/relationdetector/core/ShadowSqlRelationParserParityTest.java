package com.relationdetector.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.relationdetector.api.AdaptorContext;
import com.relationdetector.api.RelationshipCandidate;
import com.relationdetector.api.ScanScope;
import com.relationdetector.api.SqlStatementRecord;
import com.relationdetector.api.StructuredParseResult;
import com.relationdetector.api.StructuredSqlEvent;
import com.relationdetector.api.WarningMessage;
import com.relationdetector.api.Collectors.StructuredSqlParser;
import com.relationdetector.api.Enums.StatementSourceType;
import com.relationdetector.api.Enums.StructuredParseEventType;

/**
 * Locks down the safety rule for switching ANTLR toward primary.
 *
 * <p>Shadow mode may allow ANTLR to discover extra relationships, but it must
 * never silently miss relationships already produced by SimpleSqlRelationParser.
 * A missing Simple baseline relation is reported in diagnostics and, in primary
 * mode, is the signal used by SqlRelationParserRunner to fall back.
 */
class ShadowSqlRelationParserParityTest {
    @Test
    void comparisonEventReportsMissingSimpleBaselineRelations() {
        SqlStatementRecord statement = record("""
                SELECT *
                FROM orders o
                WHERE EXISTS (
                    SELECT 1
                    FROM users u
                    WHERE u.id = o.user_id
                )
                """);
        ShadowSqlRelationParser parser = new ShadowSqlRelationParser(
                new SimpleSqlRelationParser(),
                emptyStructuredParser(),
                new RelationExtractionVisitor());

        ShadowSqlRelationParser.Result result = parser.parseWithDiagnostics(statement, context());

        StructuredSqlEvent comparison = result.diagnostics().stream()
                .filter(event -> event.type() == StructuredParseEventType.PARSER_COMPARISON)
                .findFirst()
                .orElseThrow();
        assertEquals(1, comparison.attributes().get("missingSimpleCount"));
        assertTrue(String.valueOf(comparison.attributes().get("missingSimpleRelations"))
                        .contains("SQL_LOG_EXISTS"),
                () -> "Missing relation fingerprints should be operator-readable: " + comparison.attributes());
        assertEquals(1, result.missingSimpleRelations().size());
        assertEquals(1, result.primaryCount());
        assertEquals(1, result.shadowCount());
    }

    private StructuredSqlParser emptyStructuredParser() {
        return (statement, context) -> new StructuredParseResult("ANTLR", "MYSQL",
                statement.sourceName(), List.of(), List.of(), Map.of("testParser", "empty"));
    }

    private SqlStatementRecord record(String sql) {
        return new SqlStatementRecord(sql, StatementSourceType.PLAIN_SQL, "parity.sql", 1, 1, Map.of());
    }

    private AdaptorContext context() {
        List<WarningMessage> warnings = new java.util.ArrayList<>();
        return new AdaptorContext(new ScanScope(null, null, List.of(), List.of()), Map.of(), warnings::add);
    }
}
