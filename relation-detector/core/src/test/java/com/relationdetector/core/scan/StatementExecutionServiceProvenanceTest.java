package com.relationdetector.core.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.core.naming.NamingEvidencePool;
import com.relationdetector.core.tokenevent.CommonTokenEventStructuredSqlParser;

class StatementExecutionServiceProvenanceTest {
    @Test
    void directStructuredParserPathUsesNormalizedQueryProvenance() {
        SqlStatementRecord statement = new SqlStatementRecord(
                "SELECT o.id FROM orders o JOIN customers c ON c.id = o.customer_id",
                StatementSourceType.PLAIN_SQL,
                "query.sql",
                4,
                4,
                Map.of("sourceObjectType", "SQL_WRITE"));

        StatementExecutionOutcome outcome = new StatementExecutionService().executeSql(
                new CommonTokenEventStructuredSqlParser(),
                statement,
                null,
                Set.of(),
                new ScanConfig());

        assertFalse(outcome.relationshipCandidates().isEmpty());
        assertEquals(Set.of("QUERY"), outcome.relationshipCandidates().stream()
                .flatMap(relationship -> relationship.evidence().stream())
                .map(evidence -> String.valueOf(evidence.attributes().get("sourceObjectType")))
                .collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void sqlExecutionDefersNamingRulesToScanLevelEnhancement() {
        SqlStatementRecord statement = new SqlStatementRecord(
                "SELECT o.id FROM orders o JOIN customers c ON c.id = o.customer_id",
                StatementSourceType.PLAIN_SQL,
                "query.sql",
                1,
                1,
                Map.of());
        ScanConfig config = new ScanConfig();

        StatementExecutionOutcome outcome = new StatementExecutionService().executeSql(
                new CommonTokenEventStructuredSqlParser(), statement, null, Set.of(), config);

        assertEquals(0, outcome.namingEvidence().size(),
                "Statement execution must not run SQL naming rules");
        NamingEvidencePool pool = new NamingEvidencePool();
        new EvidenceEnhancementService().enhance(
                outcome.relationshipCandidates(), pool, null, config);
        assertEquals(1, pool.merged().size(),
                "Scan-level enhancement remains the single SQL naming execution path");
    }
}
