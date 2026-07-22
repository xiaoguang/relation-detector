package com.relationdetector.core.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.Enums.WarningType;
import com.relationdetector.contracts.model.TableId;
import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.contracts.parse.RowsetEvent;
import com.relationdetector.contracts.parse.SourceProvenance;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredParseResult;
import com.relationdetector.contracts.spi.AdaptorContext;
import com.relationdetector.contracts.spi.ScanScope;
import com.relationdetector.core.common.CommonDatabaseAdaptor;
import com.relationdetector.core.naming.NamingEvidencePool;
import com.relationdetector.core.tokenevent.CommonTokenEventStructuredSqlParser;

class StatementExecutionServiceProvenanceTest {
    @Test
    void directStructuredParserAcceptsAbsoluteSourceOwnedByInputStatement() {
        String source = Path.of("target", "external-input", "query.sql")
                .toAbsolutePath().normalize().toString();
        SqlStatementRecord statement = new SqlStatementRecord(
                "SELECT o.id FROM orders o JOIN customers c ON c.id = o.customer_id",
                StatementSourceType.PLAIN_SQL,
                source,
                1,
                1,
                Map.of("sourceFile", source, "sourceFileLineCount", 1L));

        StatementExecutionOutcome outcome = new StatementExecutionService().executeSql(
                new CommonTokenEventStructuredSqlParser(),
                statement,
                null,
                Set.of());

        assertFalse(outcome.relationshipCandidates().isEmpty());
    }

    @Test
    void directStructuredParserPathRejectsInvalidResultAtomically() {
        SqlStatementRecord statement = new SqlStatementRecord(
                "SELECT * FROM orders", StatementSourceType.PLAIN_SQL,
                "query.sql", 1, 1, Map.of());
        List<WarningMessage> warnings = new ArrayList<>();
        AdaptorContext context = new AdaptorContext(null, Map.of(), warnings::add);

        assertThrows(AdaptorContractException.class, () -> new StatementExecutionService().executeSql(
                (record, parserContext) -> {
                    parserContext.warn(WarningMessage.warn(
                            WarningType.PARSE_WARNING, "PLUGIN_WARNING", "must remain detached",
                            record.sourceName(), record.startLine()));
                    return new StructuredParseResult(
                            "plugin", "common", record.sourceName(),
                            List.of(new RowsetEvent(
                                    StructuredParseEventType.ROWSET_REFERENCE,
                                    SourceProvenance.source(record.sourceName(), record.startLine()),
                                    "FROM", "", "", "", "", "", "")),
                            List.of(), Map.of());
                },
                statement,
                context,
                Set.of()));
        assertTrue(warnings.isEmpty());
    }

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
                Set.of());

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
                new CommonTokenEventStructuredSqlParser(), statement, null, Set.of());

        assertEquals(0, outcome.namingEvidence().size(),
                "Statement execution must not run SQL naming rules");
        NamingEvidencePool pool = new NamingEvidencePool();
        new EvidenceEnhancementService().enhance(
                outcome.relationshipCandidates(), pool, null, config);
        assertEquals(1, pool.merged().size(),
                "Scan-level enhancement remains the single SQL naming execution path");
    }

    @Test
    void liveObjectNamespaceOverridesTheGlobalScanScopeForRelationshipsAndLineage() {
        SqlStatementRecord statement = new SqlStatementRecord(
                """
                        INSERT INTO sales_fact (order_id)
                        SELECT o.id
                        FROM orders o
                        JOIN customers c ON c.id = o.customer_id
                        """,
                StatementSourceType.PROCEDURE,
                "catalog_a.schema_a.rebuild_sales_fact",
                1,
                4,
                Map.of(
                        "objectCatalog", "catalog_a",
                        "objectSchema", "schema_a",
                        "objectName", "rebuild_sales_fact"));
        AdaptorContext context = new AdaptorContext(
                new ScanScope("catalog_b", "schema_b", List.of(), List.of()), Map.of());
        ScanConfig config = new ScanConfig();
        config.databaseType = DatabaseType.COMMON;
        config.parserMode = "token-event";
        Set<TableId> knownPhysical = Set.of(
                new TableId("catalog_a", "schema_a", "orders", "schema_a.orders"),
                new TableId("catalog_a", "schema_a", "customers", "schema_a.customers"),
                new TableId("catalog_a", "schema_a", "sales_fact", "schema_a.sales_fact"));

        StatementExecutionOutcome outcome = new StatementExecutionService().executeSql(
                new CommonDatabaseAdaptor(), config, statement, context, knownPhysical);

        assertFalse(outcome.relationshipCandidates().isEmpty());
        assertFalse(outcome.lineageCandidates().isEmpty());
        assertTrue(outcome.relationshipCandidates().stream()
                .flatMap(candidate -> java.util.stream.Stream.of(candidate.source(), candidate.target()))
                .allMatch(endpoint -> "catalog_a".equals(endpoint.table().catalog())
                        && "schema_a".equals(endpoint.table().schema())));
        assertTrue(outcome.lineageCandidates().stream()
                .flatMap(candidate -> java.util.stream.Stream.concat(
                        candidate.sources().stream(), java.util.stream.Stream.of(candidate.target())))
                .allMatch(endpoint -> "catalog_a".equals(endpoint.table().catalog())
                        && "schema_a".equals(endpoint.table().schema())));
    }
}
