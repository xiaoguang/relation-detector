package com.relationdetector.core;

import com.relationdetector.core.metadata.MetadataEvidenceEnhancer;
import com.relationdetector.core.lineage.*;
import com.relationdetector.core.parser.*;
import com.relationdetector.core.relation.*;

import com.relationdetector.core.tokenevent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.model.ColumnRef;
import com.relationdetector.contracts.model.Endpoint;
import com.relationdetector.contracts.model.Evidence;
import com.relationdetector.contracts.metadata.MetadataColumnFact;
import com.relationdetector.contracts.metadata.MetadataIndexFact;
import com.relationdetector.contracts.metadata.MetadataSnapshot;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.model.TableId;
import com.relationdetector.contracts.Enums.EvidenceSourceType;
import com.relationdetector.contracts.Enums.EvidenceType;
import com.relationdetector.contracts.Enums.RelationSubType;
import com.relationdetector.contracts.Enums.RelationType;
import com.relationdetector.core.identity.NamespaceContext;

class MetadataEvidenceEnhancerTest {
    @Test
    void enrichesExistingColumnRelationWithIndexUniqueAndTypeFacts() {
        MetadataSnapshot metadata = metadataFacts();
        RelationshipCandidate candidate = joinCandidate("orders", "user_id", "users", "id");

        new MetadataEvidenceEnhancer().enhance(List.of(candidate), metadata);

        assertHasEvidence(candidate, EvidenceType.SOURCE_INDEX);
        assertHasEvidence(candidate, EvidenceType.TARGET_UNIQUE);
        assertHasEvidence(candidate, EvidenceType.COLUMN_TYPE_COMPATIBLE);
    }

    @Test
    void enrichesColumnCoOccurrenceWithEndpointIndexUniqueAndTypeFacts() {
        MetadataSnapshot metadata = metadataFacts();
        RelationshipCandidate candidate = coOccurrenceCandidate("orders", "user_id", "users", "id");

        new MetadataEvidenceEnhancer().enhance(List.of(candidate), metadata);

        assertHasEvidence(candidate, EvidenceType.SOURCE_INDEX);
        Evidence targetUnique = evidence(candidate, EvidenceType.TARGET_UNIQUE);
        assertEquals("shop.users.id", targetUnique.attributes().get("uniqueEndpoint"));
        assertEquals("target", targetUnique.attributes().get("endpointSide"));
        assertHasEvidence(candidate, EvidenceType.COLUMN_TYPE_COMPATIBLE);
    }

    @Test
    void doesNotCreateRelationshipsFromMetadataFactsAlone() {
        MetadataSnapshot metadata = metadataFacts();

        List<RelationshipCandidate> candidates = new java.util.ArrayList<>();
        new MetadataEvidenceEnhancer().enhance(candidates, metadata);

        assertEquals(0, candidates.size(), "metadata index/column facts can enrich candidates but must not invent FK-like relations");
    }

    @Test
    void doesNotAttachMetadataFromAnotherSchemaWithTheSameTableAndColumnNames() {
        MetadataSnapshot metadata = metadataFacts();
        RelationshipCandidate candidate = new RelationshipCandidate(
                Endpoint.column(ColumnRef.of(TableId.of("archive", "orders"), "user_id")),
                Endpoint.column(ColumnRef.of(TableId.of("archive", "users"), "id")),
                RelationType.FK_LIKE,
                RelationSubType.INFERRED_JOIN_FK);
        candidate.evidence().add(new Evidence(EvidenceType.SQL_LOG_JOIN, BigDecimal.valueOf(0.55),
                EvidenceSourceType.PLAIN_SQL, "unit-test.sql", "archive.orders.user_id = archive.users.id", Map.of()));

        new MetadataEvidenceEnhancer().enhance(List.of(candidate), metadata);

        assertEquals(List.of(EvidenceType.SQL_LOG_JOIN),
                candidate.evidence().stream().map(Evidence::type).toList());
    }

    @Test
    void honorsCaseSensitiveIdentifierRulesInsteadOfLowercasingKeys() {
        MetadataSnapshot metadata = new MetadataSnapshot();
        metadata.columnFacts().add(new MetadataColumnFact("shop", "orders", "customerid",
                "bigint", "bigint", false, null, "", "", 1));
        metadata.indexFacts().add(new MetadataIndexFact("shop", "orders", "PRIMARY",
                true, true, "BTREE", true, List.of("customerid"), List.of(), List.of(), List.of(1)));
        TableId table = new TableId(null, "Shop", "Orders", "Shop.Orders");
        ColumnRef column = new ColumnRef(table, "CustomerId", "CustomerId", null, false);
        RelationshipCandidate candidate = new RelationshipCandidate(
                Endpoint.column(column), Endpoint.column(column),
                RelationType.FK_LIKE, RelationSubType.INFERRED_JOIN_FK);
        candidate.evidence().add(new Evidence(EvidenceType.SQL_LOG_JOIN, BigDecimal.valueOf(0.55),
                EvidenceSourceType.PLAIN_SQL, "case-sensitive.sql", "typed equality", Map.of()));

        new MetadataEvidenceEnhancer().enhance(
                List.of(candidate), metadata, value -> value == null ? "" : value, NamespaceContext.empty());

        assertEquals(List.of(EvidenceType.SQL_LOG_JOIN),
                candidate.evidence().stream().map(Evidence::type).toList());
    }

    @Test
    void resolvesBareCandidateAgainstExplicitScanSchemaWithoutChangingEndpoint() {
        MetadataSnapshot metadata = metadataFacts();
        RelationshipCandidate candidate = new RelationshipCandidate(
                Endpoint.column(ColumnRef.of(TableId.of(null, "orders"), "user_id")),
                Endpoint.column(ColumnRef.of(TableId.of(null, "users"), "id")),
                RelationType.FK_LIKE,
                RelationSubType.INFERRED_JOIN_FK);
        candidate.evidence().add(new Evidence(EvidenceType.SQL_LOG_JOIN, BigDecimal.valueOf(0.55),
                EvidenceSourceType.PLAIN_SQL, "unit-test.sql", "orders.user_id = users.id", Map.of()));

        new MetadataEvidenceEnhancer().enhance(
                List.of(candidate), metadata,
                value -> value == null ? "" : value.strip().toLowerCase(java.util.Locale.ROOT),
                new NamespaceContext("", "shop", List.of()));

        assertHasEvidence(candidate, EvidenceType.TARGET_UNIQUE);
        assertEquals("orders.user_id", candidate.source().displayName());
        assertEquals("users.id", candidate.target().displayName());
    }

    private MetadataSnapshot metadataFacts() {
        MetadataSnapshot snapshot = new MetadataSnapshot();
        snapshot.columnFacts().add(new MetadataColumnFact("shop", "orders", "user_id",
                "bigint", "bigint", false, null, "", "", 2));
        snapshot.columnFacts().add(new MetadataColumnFact("shop", "users", "id",
                "bigint", "bigint", false, null, "auto_increment", "", 1));
        snapshot.indexFacts().add(new MetadataIndexFact("shop", "orders", "idx_orders_user_id",
                false, false, "BTREE", true, List.of("user_id"), List.of(), List.of(), List.of(1)));
        snapshot.indexFacts().add(new MetadataIndexFact("shop", "users", "PRIMARY",
                true, true, "BTREE", true, List.of("id"), List.of(), List.of(), List.of(1)));
        return snapshot;
    }

    private RelationshipCandidate joinCandidate(String sourceTable, String sourceColumn, String targetTable, String targetColumn) {
        RelationshipCandidate candidate = new RelationshipCandidate(
                Endpoint.column(ColumnRef.of(TableId.of("shop", sourceTable), sourceColumn)),
                Endpoint.column(ColumnRef.of(TableId.of("shop", targetTable), targetColumn)),
                RelationType.FK_LIKE,
                RelationSubType.INFERRED_JOIN_FK);
        candidate.evidence().add(new Evidence(EvidenceType.SQL_LOG_JOIN, BigDecimal.valueOf(0.55),
                EvidenceSourceType.PLAIN_SQL, "unit-test.sql", "orders.user_id = users.id", Map.of()));
        return candidate;
    }

    private RelationshipCandidate coOccurrenceCandidate(String sourceTable, String sourceColumn, String targetTable, String targetColumn) {
        RelationshipCandidate candidate = new RelationshipCandidate(
                Endpoint.column(ColumnRef.of(TableId.of("shop", sourceTable), sourceColumn)),
                Endpoint.column(ColumnRef.of(TableId.of("shop", targetTable), targetColumn)),
                RelationType.CO_OCCURRENCE,
                RelationSubType.COLUMN_CO_OCCURRENCE);
        candidate.evidence().add(new Evidence(EvidenceType.SQL_LOG_JOIN, BigDecimal.valueOf(0.55),
                EvidenceSourceType.PLAIN_SQL, "unit-test.sql", "orders.user_id = users.id", Map.of()));
        return candidate;
    }

    private void assertHasEvidence(RelationshipCandidate candidate, EvidenceType type) {
        assertTrue(candidate.evidence().stream().anyMatch(evidence -> evidence.type() == type),
                () -> "Missing " + type + " in " + candidate.evidence());
    }

    private Evidence evidence(RelationshipCandidate candidate, EvidenceType type) {
        return candidate.evidence().stream()
                .filter(evidence -> evidence.type() == type)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing " + type + " in " + candidate.evidence()));
    }
}
