package com.relationdetector.core;

import com.relationdetector.core.ddl.*;
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

import com.relationdetector.api.ColumnRef;
import com.relationdetector.api.Endpoint;
import com.relationdetector.api.Evidence;
import com.relationdetector.api.MetadataColumnFact;
import com.relationdetector.api.MetadataIndexFact;
import com.relationdetector.api.MetadataSnapshot;
import com.relationdetector.api.RelationshipCandidate;
import com.relationdetector.api.TableId;
import com.relationdetector.api.Enums.EvidenceSourceType;
import com.relationdetector.api.Enums.EvidenceType;
import com.relationdetector.api.Enums.RelationSubType;
import com.relationdetector.api.Enums.RelationType;

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
    void doesNotCreateRelationshipsFromMetadataFactsAlone() {
        MetadataSnapshot metadata = metadataFacts();

        List<RelationshipCandidate> candidates = new java.util.ArrayList<>();
        new MetadataEvidenceEnhancer().enhance(candidates, metadata);

        assertEquals(0, candidates.size(), "metadata index/column facts can enrich candidates but must not invent FK-like relations");
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

    private void assertHasEvidence(RelationshipCandidate candidate, EvidenceType type) {
        assertTrue(candidate.evidence().stream().anyMatch(evidence -> evidence.type() == type),
                () -> "Missing " + type + " in " + candidate.evidence());
    }
}
