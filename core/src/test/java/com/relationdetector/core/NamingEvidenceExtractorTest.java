package com.relationdetector.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.Enums.EvidenceSourceType;
import com.relationdetector.contracts.Enums.EvidenceType;
import com.relationdetector.contracts.Enums.RelationSubType;
import com.relationdetector.contracts.Enums.RelationType;
import com.relationdetector.contracts.metadata.MetadataSnapshot;
import com.relationdetector.contracts.model.ColumnRef;
import com.relationdetector.contracts.model.Endpoint;
import com.relationdetector.contracts.model.Evidence;
import com.relationdetector.contracts.model.NamingEvidenceCandidate;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.model.TableId;
import com.relationdetector.contracts.parse.StructuredParseResult;
import com.relationdetector.contracts.scoring.DefaultEvidenceScores;
import com.relationdetector.core.parse.SqlDialect;
import com.relationdetector.core.relation.NamingEvidenceExtractor;
import com.relationdetector.core.relation.NamingMatchEvidenceEnhancer;
import com.relationdetector.core.tokenevent.TokenEventStructuredDdlParser;

class NamingEvidenceExtractorTest {
    private final NamingEvidenceExtractor extractor = new NamingEvidenceExtractor();

    @Test
    void extractsMetadataNameOnlyEvidenceWithoutCreatingRelationship() {
        MetadataSnapshot metadata = new MetadataSnapshot();
        metadata.columns().add(column("orders", "customer_id"));
        metadata.columns().add(column("customers", "id"));

        List<NamingEvidenceCandidate> evidence = extractor.extractFromMetadata(metadata);

        assertEquals(1, evidence.size());
        NamingEvidenceCandidate match = evidence.get(0);
        assertEndpoint("orders", "customer_id", match.source());
        assertEndpoint("customers", "id", match.target());
        assertEquals("TABLE_ID", match.rule());
        assertTrue(match.directionHint());
        assertEquals(EvidenceType.NAMING_MATCH, match.evidence().type());
    }

    @Test
    void extractsDdlColumnInventoryEvidenceWithoutCreatingRelationship() {
        String ddl = """
                CREATE TABLE customers (
                  id INTEGER PRIMARY KEY
                );
                CREATE TABLE orders (
                  id INTEGER PRIMARY KEY,
                  customer_id INTEGER
                );
                """;
        StructuredParseResult result = new TokenEventStructuredDdlParser(SqlDialect.GENERIC)
                .parseDdl(ddl, "schema.sql", null);

        List<NamingEvidenceCandidate> evidence = extractor.extractFromDdlEvents(result.events());

        assertEquals(1, evidence.size());
        assertEndpoint("orders", "customer_id", evidence.get(0).source());
        assertEndpoint("customers", "id", evidence.get(0).target());
        assertEquals("TABLE_ID", evidence.get(0).rule());
    }

    @Test
    void extractsSqlPredicateNamingEvidenceAndEnhancerCanReusePool() {
        RelationshipCandidate candidate = sqlPredicate("orders", "customer_id", "customers", "id");

        List<NamingEvidenceCandidate> evidence = extractor.extractFromRelationshipCandidates(List.of(candidate));

        assertEquals(1, evidence.size());
        assertEquals("TABLE_ID", evidence.get(0).rule());
        new NamingMatchEvidenceEnhancer().enhance(List.of(candidate), evidence);
        assertTrue(candidate.evidence().stream().anyMatch(item -> item.type() == EvidenceType.NAMING_MATCH));
    }

    @Test
    void doesNotExtractAmbiguousTwoForeignKeyStyleColumns() {
        RelationshipCandidate candidate = sqlPredicate("orders", "customer_id", "payments", "order_id");

        List<NamingEvidenceCandidate> evidence = extractor.extractFromRelationshipCandidates(List.of(candidate));

        assertTrue(evidence.isEmpty());
    }

    @Test
    void extractsSelfRoleIdEvidence() {
        RelationshipCandidate candidate = sqlPredicate("employees", "manager_id", "employees", "id");
        candidate.evidence().add(new Evidence(EvidenceType.SQL_LOG_COLUMN_CO_OCCURRENCE,
                java.math.BigDecimal.valueOf(DefaultEvidenceScores.SQL_LOG_COLUMN_CO_OCCURRENCE),
                EvidenceSourceType.PLAIN_SQL,
                "query.sql",
                "self join",
                java.util.Map.of("selfJoinRole", true)));

        List<NamingEvidenceCandidate> evidence = extractor.extractFromRelationshipCandidates(List.of(candidate));

        assertEquals(1, evidence.size());
        assertEquals("SELF_ROLE_ID", evidence.get(0).rule());
        assertEndpoint("employees", "manager_id", evidence.get(0).source());
        assertEndpoint("employees", "id", evidence.get(0).target());
    }

    @Test
    void doesNotExtractPlainIdToIdEvidence() {
        RelationshipCandidate candidate = sqlPredicate("a", "id", "b", "id");

        List<NamingEvidenceCandidate> evidence = extractor.extractFromRelationshipCandidates(List.of(candidate));

        assertTrue(evidence.isEmpty());
    }

    private RelationshipCandidate sqlPredicate(String leftTable, String leftColumn, String rightTable, String rightColumn) {
        RelationshipCandidate candidate = new RelationshipCandidate(
                Endpoint.column(column(leftTable, leftColumn)),
                Endpoint.column(column(rightTable, rightColumn)),
                RelationType.CO_OCCURRENCE,
                RelationSubType.COLUMN_CO_OCCURRENCE);
        candidate.evidence().add(Evidence.of(EvidenceType.SQL_LOG_JOIN,
                DefaultEvidenceScores.SQL_LOG_JOIN,
                EvidenceSourceType.PLAIN_SQL,
                "query.sql",
                leftTable + "." + leftColumn + " = " + rightTable + "." + rightColumn));
        return candidate;
    }

    private ColumnRef column(String tableName, String columnName) {
        return ColumnRef.of(TableId.of(null, tableName), columnName);
    }

    private void assertEndpoint(String table, String column, Endpoint endpoint) {
        assertEquals(table, endpoint.table().tableName());
        assertEquals(column, endpoint.column().columnName());
    }
}
