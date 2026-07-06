package com.relationdetector.core.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.Enums.EvidenceSourceType;
import com.relationdetector.contracts.Enums.EvidenceType;
import com.relationdetector.contracts.Enums.RelationSubType;
import com.relationdetector.contracts.Enums.RelationType;
import com.relationdetector.contracts.metadata.MetadataColumnFact;
import com.relationdetector.contracts.metadata.MetadataIndexFact;
import com.relationdetector.contracts.metadata.MetadataSnapshot;
import com.relationdetector.contracts.model.ColumnRef;
import com.relationdetector.contracts.model.Endpoint;
import com.relationdetector.contracts.model.Evidence;
import com.relationdetector.contracts.model.NamingEvidenceCandidate;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.model.TableId;
import com.relationdetector.contracts.spi.DataProfileOptions;

class DataProfileCandidateGeneratorTest {
    private final DataProfileCandidateGenerator generator = new DataProfileCandidateGenerator();

    @Test
    void existingColumnPredicateCandidatesAreSelectedWithinBudget() {
        RelationshipCandidate candidate = relation("orders", "customer_id", "customers", "id");
        candidate.evidence().add(Evidence.of(EvidenceType.SQL_LOG_JOIN, 0.55d,
                EvidenceSourceType.NATIVE_LOG, "query.sql", "orders.customer_id = customers.id"));

        List<RelationshipCandidate> selected = generator.select(
                List.of(candidate),
                new MetadataSnapshot(),
                List.of(),
                DataProfileOptions.defaults().withMaxCandidatePairs(1));

        assertEquals(List.of(candidate), selected);
    }

    @Test
    void declaredForeignKeysAreSkippedUnlessVerificationEnabled() {
        RelationshipCandidate candidate = relation("orders", "customer_id", "customers", "id");
        candidate.evidence().add(Evidence.of(EvidenceType.METADATA_FOREIGN_KEY, 0.98d,
                EvidenceSourceType.METADATA, "metadata", "declared fk"));

        assertTrue(generator.select(List.of(candidate), new MetadataSnapshot(), List.of(),
                DataProfileOptions.defaults()).isEmpty());
        assertEquals(1, generator.select(List.of(candidate), new MetadataSnapshot(), List.of(),
                DataProfileOptions.defaults().withVerifyDeclaredForeignKeys(true)).size());
    }

    @Test
    void namingEvidenceDiscoveryRequiresExplicitOptInAndTargetUnique() {
        MetadataSnapshot metadata = metadataWithCustomerId();
        NamingEvidenceCandidate naming = new NamingEvidenceCandidate(
                endpoint("orders", "customer_id"),
                endpoint("customers", "id"),
                Evidence.of(EvidenceType.NAMING_MATCH, 0.20d, EvidenceSourceType.NAMING_HEURISTIC,
                        "metadata", "orders.customer_id matches customers.id"),
                "TABLE_ID",
                true);

        assertTrue(generator.select(List.of(), metadata, List.of(naming), DataProfileOptions.defaults()).isEmpty());

        List<RelationshipCandidate> selected = generator.select(List.of(), metadata, List.of(naming),
                DataProfileOptions.defaults().withDiscoverFromNamingEvidence(true));

        assertEquals(1, selected.size());
        assertEquals("orders.customer_id", selected.get(0).source().displayName());
        assertEquals("customers.id", selected.get(0).target().displayName());
    }

    @Test
    void pureSameNamedColumnsDoNotBecomeProfileCandidates() {
        MetadataSnapshot metadata = new MetadataSnapshot();
        metadata.columnFacts().add(column("orders", "status", "varchar"));
        metadata.columnFacts().add(column("customers", "status", "varchar"));

        List<RelationshipCandidate> selected = generator.select(
                List.of(),
                metadata,
                List.of(),
                DataProfileOptions.defaults().withDiscoverFromNamingEvidence(true));

        assertTrue(selected.isEmpty());
    }

    @Test
    void sourceColumnTargetBudgetLimitsProfileFanOut() {
        RelationshipCandidate first = relation("orders", "customer_id", "customers", "id");
        RelationshipCandidate second = relation("orders", "customer_id", "customer_archive", "id");
        first.evidence().add(joinEvidence());
        second.evidence().add(joinEvidence());

        List<RelationshipCandidate> selected = generator.select(
                List.of(first, second),
                new MetadataSnapshot(),
                List.of(),
                DataProfileOptions.defaults()
                        .withSkipUnindexedLargeTargets(false)
                        .withMaxTargetsPerSourceColumn(1));

        assertEquals(List.of(first), selected);
    }

    @Test
    void indexedTargetGateSkipsUnindexedTargetsWhenIndexMetadataExists() {
        RelationshipCandidate indexed = relation("orders", "customer_id", "customers", "id");
        RelationshipCandidate unindexed = relation("orders", "sales_rep_id", "employees", "id");
        indexed.evidence().add(joinEvidence());
        unindexed.evidence().add(joinEvidence());

        MetadataSnapshot metadata = new MetadataSnapshot();
        metadata.indexFacts().add(new MetadataIndexFact(null, "customers", "PRIMARY", true, true,
                "BTREE", true, List.of("id"), List.of(), List.of(), List.of(1)));

        List<RelationshipCandidate> selected = generator.select(
                List.of(indexed, unindexed),
                metadata,
                List.of(),
                DataProfileOptions.defaults());

        assertEquals(List.of(indexed), selected);
    }

    private MetadataSnapshot metadataWithCustomerId() {
        MetadataSnapshot metadata = new MetadataSnapshot();
        metadata.columnFacts().add(column("orders", "customer_id", "bigint"));
        metadata.columnFacts().add(column("customers", "id", "bigint"));
        metadata.indexFacts().add(new MetadataIndexFact(null, "customers", "PRIMARY", true, true,
                "BTREE", true, List.of("id"), List.of(), List.of(), List.of(1)));
        return metadata;
    }

    private MetadataColumnFact column(String table, String column, String type) {
        return new MetadataColumnFact(null, table, column, type, type, true, null, "", null, 1);
    }

    private Evidence joinEvidence() {
        return Evidence.of(EvidenceType.SQL_LOG_JOIN, 0.55d,
                EvidenceSourceType.NATIVE_LOG, "query.sql", "join predicate");
    }

    private RelationshipCandidate relation(String sourceTable, String sourceColumn, String targetTable, String targetColumn) {
        return new RelationshipCandidate(
                endpoint(sourceTable, sourceColumn),
                endpoint(targetTable, targetColumn),
                RelationType.CO_OCCURRENCE,
                RelationSubType.COLUMN_CO_OCCURRENCE);
    }

    private Endpoint endpoint(String table, String column) {
        return Endpoint.column(ColumnRef.of(TableId.of(null, table), column));
    }
}
