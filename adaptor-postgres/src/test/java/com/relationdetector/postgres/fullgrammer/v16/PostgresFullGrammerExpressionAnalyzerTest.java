package com.relationdetector.postgres.fullgrammer.v16;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import com.relationdetector.contracts.Enums.EvidenceType;
import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.core.relation.TokenEventRelationExtractor;

class PostgresFullGrammerExpressionAnalyzerTest {
    @Test
    void analyzerReadsCaseExpressionThroughFullGrammerEvents() {
        var result = new PostgresFullGrammerDialectModule()
                .sqlParser()
                .parseSql(statement("""
                UPDATE users u
                SET risk_band = CASE WHEN u.risk_score > 80 THEN 'HIGH' ELSE u.risk_band END
                ;
                """), null);

        Map<String, Object> assignment = result.events().stream()
                .filter(event -> event.type() == StructuredParseEventType.UPDATE_ASSIGNMENT)
                .findFirst()
                .orElseThrow()
                .attributes();

        assertEquals("CASE_WHEN", assignment.get("transformType"));
        assertEquals("CONTROL", assignment.get("flowKind"));
        assertTrue(((List<?>) assignment.get("sourceAliases")).contains("u"));
        assertTrue(((List<?>) assignment.get("sourceColumns")).contains("risk_score"));
        assertTrue(((List<?>) assignment.get("sourceColumns")).contains("risk_band"));
    }

    @Test
    void nestedInSubqueryDoesNotBindInnerUnqualifiedColumnToMiddleScope() {
        SqlStatementRecord statement = statement("""
                SELECT a.account_id,
                       (SELECT count(*) FROM pg10_transactions
                        WHERE account_id IN (
                            SELECT account_id FROM pg10_accounts
                            WHERE branch_code = a.branch_code
                              AND risk_score <= a.risk_score
                        )
                       ) AS peer_branch_weekly_txns
                FROM pg10_accounts a;
                """);

        var structured = new PostgresFullGrammerDialectModule().sqlParser().parseSql(statement, null);
        List<RelationshipCandidate> relationships = new TokenEventRelationExtractor().extract(statement, structured);

        assertTrue(relationships.stream().anyMatch(candidate ->
                fingerprint(candidate).equals("FK_LIKE:pg10_transactions.account_id->pg10_accounts.account_id:SQL_LOG_SUBQUERY_IN")));
        assertFalse(relationships.stream().anyMatch(candidate ->
                fingerprint(candidate).equals("CO_OCCURRENCE:pg10_transactions.branch_code->pg10_accounts.branch_code:SQL_LOG_COLUMN_CO_OCCURRENCE")));
    }

    @Test
    void analyzerMarksExpressionProjectionAsNonRelationColumnExpression() {
        var result = new PostgresFullGrammerDialectModule()
                .sqlParser()
                .parseSql(statement("""
                SELECT u.account_status
                FROM application_users u
                WHERE u.account_status IN (
                    SELECT 'STATUS_' || status_label
                    FROM structural_statuses
                );
                """), null);

        assertFalse(result.events().stream().anyMatch(event ->
                event.type() == StructuredParseEventType.IN_SUBQUERY_PREDICATE
                        && "structural_statuses".equals(event.attributes().get("innerTable"))));

    }

    @Test
    void analyzerClassifiesPostgresConcatOperatorAsConcatFormat() {
        var result = new PostgresFullGrammerDialectModule()
                .sqlParser()
                .parseSql(statement("""
                UPDATE order_ledgers l
                SET remarks = 'User risk level: ' || u.risk_level || ' | Order Rank: ' || fo.rnk
                FROM fraud_orders fo, users u
                WHERE l.order_id = fo.order_id
                  AND fo.user_id = u.id;
                """), null);

        Map<String, Object> assignment = result.events().stream()
                .filter(event -> event.type() == StructuredParseEventType.UPDATE_ASSIGNMENT)
                .filter(event -> "remarks".equals(event.attributes().get("targetColumn")))
                .findFirst()
                .orElseThrow()
                .attributes();

        assertEquals("CONCAT_FORMAT", assignment.get("transformType"));
        assertEquals(List.of("u", "fo"), assignment.get("sourceAliases"));
        assertEquals(List.of("risk_level", "rnk"), assignment.get("sourceColumns"));
    }

    private static SqlStatementRecord statement(String sql) {
        return new SqlStatementRecord(sql, StatementSourceType.PLAIN_SQL, "fixture.sql", 1, 1, Map.of());
    }

    private static String fingerprint(RelationshipCandidate candidate) {
        EvidenceType evidenceType = candidate.evidence().isEmpty() ? null : candidate.evidence().get(0).type();
        return candidate.relationType() + ":"
                + candidate.source().displayName() + "->" + candidate.target().displayName()
                + ":" + evidenceType;
    }

}
