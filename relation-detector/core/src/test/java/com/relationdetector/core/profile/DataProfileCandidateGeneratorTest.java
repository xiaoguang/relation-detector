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
import com.relationdetector.contracts.spi.IdentifierRules;
import com.relationdetector.core.identity.NamespaceContext;

class DataProfileCandidateGeneratorTest {
    private final DataProfileCandidateGenerator generator = new DataProfileCandidateGenerator();

    @Test
    void existingColumnPredicateCandidatesAreSelectedWithinBudget() {
        RelationshipCandidate candidate = relation("orders", "customer_id", "customers", "id");
        candidate.evidence().add(Evidence.of(EvidenceType.SQL_LOG_JOIN, 0.55d,
                EvidenceSourceType.NATIVE_LOG, "query.sql", "orders.customer_id = customers.id"));

        List<RelationshipCandidate> selected = generator.select(
                List.of(candidate),
                metadataFor(candidate, "bigint", "bigint"),
                List.of(),
                DataProfileOptions.defaults().withMaxCandidatePairs(1));

        assertEquals(List.of(candidate), selected);
    }

    @Test
    void declaredForeignKeysAreSkippedUnlessVerificationEnabled() {
        RelationshipCandidate candidate = relation("orders", "customer_id", "customers", "id");
        candidate.evidence().add(Evidence.of(EvidenceType.METADATA_FOREIGN_KEY, 0.98d,
                EvidenceSourceType.METADATA, "metadata", "declared fk"));
        MetadataSnapshot metadata = metadataFor(candidate, "bigint", "bigint");

        assertTrue(generator.select(List.of(candidate), metadata, List.of(),
                DataProfileOptions.defaults()).isEmpty());
        assertEquals(1, generator.select(List.of(candidate), metadata, List.of(),
                DataProfileOptions.defaults().withVerifyDeclaredForeignKeys(true)).size());
    }

    @Test
    void existingCandidatesRequireInventoryAndCompatibleTypes() {
        RelationshipCandidate candidate = relation("orders", "customer_id", "customers", "id");
        candidate.evidence().add(joinEvidence());

        assertTrue(generator.select(List.of(candidate), new MetadataSnapshot(), List.of(),
                DataProfileOptions.defaults()).isEmpty());
        assertTrue(generator.select(List.of(candidate),
                metadataFor(candidate, "bigint", "varchar"), List.of(),
                DataProfileOptions.defaults()).isEmpty());
        assertEquals(List.of(candidate), generator.select(List.of(candidate),
                metadataFor(candidate, "bigint", "bigint"), List.of(),
                DataProfileOptions.defaults()));
    }

    @Test
    void declaredForeignKeyBypassesUnindexedTargetGate() {
        RelationshipCandidate candidate = relation("orders", "customer_id", "customers", "id");
        candidate.evidence().add(Evidence.of(EvidenceType.METADATA_FOREIGN_KEY, 0.98d,
                EvidenceSourceType.METADATA, "metadata", "declared fk"));
        MetadataSnapshot metadata = metadataFor(candidate, "bigint", "bigint");
        metadata.indexFacts().add(new MetadataIndexFact(null, null, "other_table", "idx_other",
                false, false, "BTREE", true, List.of("id"), List.of(), List.of(), List.of(1)));

        assertEquals(List.of(candidate), generator.select(List.of(candidate), metadata, List.of(),
                DataProfileOptions.defaults().withVerifyDeclaredForeignKeys(true)));
    }

    @Test
    void candidatePriorityIsStableBeforeBudgetsAreApplied() {
        RelationshipCandidate declared = relation("orders", "sales_rep_id", "employees", "id");
        declared.evidence().add(Evidence.of(EvidenceType.METADATA_FOREIGN_KEY, 0.98d,
                EvidenceSourceType.METADATA, "metadata", "declared fk"));
        RelationshipCandidate predicate = relation("orders", "customer_id", "customers", "id");
        predicate.evidence().add(joinEvidence());
        MetadataSnapshot metadata = metadataFor(declared, "bigint", "bigint");
        addColumns(metadata, predicate, "bigint", "bigint");
        metadata.indexFacts().add(new MetadataIndexFact(null, null, "customers", "PRIMARY",
                true, true, "BTREE", true, List.of("id"), List.of(), List.of(), List.of(1)));
        DataProfileOptions options = DataProfileOptions.defaults()
                .withVerifyDeclaredForeignKeys(true)
                .withMaxCandidatePairs(1);

        assertEquals(List.of(predicate), generator.select(
                List.of(declared, predicate), metadata, List.of(), options));
        assertEquals(List.of(predicate), generator.select(
                List.of(predicate, declared), metadata, List.of(), options));
    }

    @Test
    void namingEvidenceDiscoveryRequiresExplicitOptInTargetUniqueAndSourceIndex() {
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
                metadataFor(List.of(first, second), "bigint"),
                List.of(),
                DataProfileOptions.defaults()
                        .withSkipUnindexedLargeTargets(false)
                        .withMaxTargetsPerSourceColumn(1));

        assertEquals(List.of(second), selected,
                "Equal-priority fan-out must use canonical target order before applying the source budget");
    }

    @Test
    void sourceBudgetUsesCanonicalCatalogIdentity() {
        TableId bareOrders = TableId.of(null, "orders");
        TableId qualifiedOrders = new TableId("shop", null, "orders", "orders");
        RelationshipCandidate first = new RelationshipCandidate(
                Endpoint.column(ColumnRef.of(bareOrders, "customer_id")),
                endpoint("customers", "id"),
                RelationType.CO_OCCURRENCE,
                RelationSubType.COLUMN_CO_OCCURRENCE);
        RelationshipCandidate second = new RelationshipCandidate(
                Endpoint.column(ColumnRef.of(qualifiedOrders, "customer_id")),
                endpoint("customer_archive", "id"),
                RelationType.CO_OCCURRENCE,
                RelationSubType.COLUMN_CO_OCCURRENCE);
        first.evidence().add(joinEvidence());
        second.evidence().add(joinEvidence());
        MetadataSnapshot metadata = new MetadataSnapshot();
        metadata.columnFacts().add(new MetadataColumnFact("shop", null, "orders", "customer_id",
                "bigint", "bigint", true, null, "", null, 1));
        metadata.columnFacts().add(new MetadataColumnFact("shop", null, "customers", "id",
                "bigint", "bigint", false, null, "", null, 1));
        metadata.columnFacts().add(new MetadataColumnFact("shop", null, "customer_archive", "id",
                "bigint", "bigint", false, null, "", null, 1));

        List<RelationshipCandidate> selected = generator.select(
                List.of(first, second),
                metadata,
                List.of(),
                DataProfileOptions.defaults()
                        .withSkipUnindexedLargeTargets(false)
                        .withMaxTargetsPerSourceColumn(1),
                mysqlRules(),
                new NamespaceContext("shop", null, List.of()));

        assertEquals(List.of(second), selected,
                "Bare and explicitly catalog-qualified sources must share one profile budget "
                        + "after canonical target ordering");
    }

    @Test
    void indexedTargetGateSkipsUnindexedTargetsWhenIndexMetadataExists() {
        RelationshipCandidate indexed = relation("orders", "customer_id", "customers", "id");
        RelationshipCandidate unindexed = relation("orders", "sales_rep_id", "employees", "id");
        indexed.evidence().add(joinEvidence());
        unindexed.evidence().add(joinEvidence());

        MetadataSnapshot metadata = new MetadataSnapshot();
        addColumns(metadata, indexed, "bigint", "bigint");
        addColumns(metadata, unindexed, "bigint", "bigint");
        metadata.indexFacts().add(new MetadataIndexFact(null, null, "customers", "PRIMARY", true, true,
                "BTREE", true, List.of("id"), List.of(), List.of(), List.of(1)));

        List<RelationshipCandidate> selected = generator.select(
                List.of(indexed, unindexed),
                metadata,
                List.of(),
                DataProfileOptions.defaults());

        assertEquals(List.of(indexed), selected);
    }

    @Test
    void namingDiscoveryDoesNotUseUniqueIndexFromAnotherSchema() {
        MetadataSnapshot metadata = new MetadataSnapshot();
        metadata.columnFacts().add(new MetadataColumnFact(null, "shop", "orders", "customer_id",
                "bigint", "bigint", true, null, "", null, 1));
        metadata.columnFacts().add(new MetadataColumnFact(null, "shop", "customers", "id",
                "bigint", "bigint", false, null, "", null, 1));
        metadata.indexFacts().add(new MetadataIndexFact(null, "archive", "customers", "PRIMARY", true, true,
                "BTREE", true, List.of("id"), List.of(), List.of(), List.of(1)));
        NamingEvidenceCandidate naming = new NamingEvidenceCandidate(
                Endpoint.column(ColumnRef.of(TableId.of("shop", "orders"), "customer_id")),
                Endpoint.column(ColumnRef.of(TableId.of("shop", "customers"), "id")),
                Evidence.of(EvidenceType.NAMING_MATCH, 0.20d, EvidenceSourceType.NAMING_HEURISTIC,
                        "metadata", "shop.orders.customer_id matches shop.customers.id"),
                "TABLE_ID",
                true);

        List<RelationshipCandidate> selected = generator.select(List.of(), metadata, List.of(naming),
                DataProfileOptions.defaults().withDiscoverFromNamingEvidence(true));

        assertTrue(selected.isEmpty());
    }

    @Test
    void namingDiscoveryDoesNotUseUniqueIndexFromAnotherCatalog() {
        MetadataSnapshot metadata = new MetadataSnapshot();
        metadata.columnFacts().add(new MetadataColumnFact("live", "shop", "orders", "customer_id",
                "bigint", "bigint", true, null, "", null, 1));
        metadata.columnFacts().add(new MetadataColumnFact("live", "shop", "customers", "id",
                "bigint", "bigint", false, null, "", null, 1));
        metadata.indexFacts().add(new MetadataIndexFact("archive", "shop", "customers", "PRIMARY",
                true, true, "BTREE", true, List.of("id"), List.of(), List.of(), List.of(1)));
        TableId orders = new TableId("live", "shop", "orders", "shop.orders");
        TableId customers = new TableId("live", "shop", "customers", "shop.customers");
        NamingEvidenceCandidate naming = new NamingEvidenceCandidate(
                Endpoint.column(ColumnRef.of(orders, "customer_id")),
                Endpoint.column(ColumnRef.of(customers, "id")),
                Evidence.of(EvidenceType.NAMING_MATCH, 0.20d, EvidenceSourceType.NAMING_HEURISTIC,
                        "metadata", "live.shop.orders.customer_id matches live.shop.customers.id"),
                "TABLE_ID",
                true);

        List<RelationshipCandidate> selected = generator.select(List.of(), metadata, List.of(naming),
                DataProfileOptions.defaults().withDiscoverFromNamingEvidence(true));

        assertTrue(selected.isEmpty());
    }

    @Test
    void compositeUniqueDoesNotEnableNamingOnlyDiscovery() {
        MetadataSnapshot metadata = metadataWithCustomerId();
        metadata.indexFacts().clear();
        metadata.indexFacts().add(new MetadataIndexFact(null, null, "customers", "uq_customers_tenant_id",
                true, false, "BTREE", true, List.of("tenant_id", "id"), List.of(), List.of(), List.of(1, 2)));
        NamingEvidenceCandidate naming = new NamingEvidenceCandidate(
                endpoint("orders", "customer_id"), endpoint("customers", "id"),
                Evidence.of(EvidenceType.NAMING_MATCH, 0.20d, EvidenceSourceType.NAMING_HEURISTIC,
                        "metadata", "orders.customer_id matches customers.id"), "TABLE_ID", true);

        assertTrue(generator.select(List.of(), metadata, List.of(naming),
                DataProfileOptions.defaults().withDiscoverFromNamingEvidence(true)).isEmpty());
    }

    @Test
    void compositeOrdinaryIndexAllowsLookupOnlyOnLeadingColumn() {
        MetadataSnapshot metadata = new MetadataSnapshot();
        metadata.columnFacts().add(column("orders", "customer_id", "bigint"));
        metadata.columnFacts().add(column("orders", "tenant_id", "bigint"));
        metadata.columnFacts().add(column("customers", "id", "bigint"));
        metadata.columnFacts().add(column("customers", "tenant_id", "bigint"));
        metadata.indexFacts().add(new MetadataIndexFact(null, null, "customers", "idx_customers_tenant_id",
                false, false, "BTREE", true, List.of("tenant_id", "id"), List.of(), List.of(), List.of(1, 2)));
        RelationshipCandidate id = relation("orders", "customer_id", "customers", "id");
        RelationshipCandidate tenant = relation("orders", "tenant_id", "customers", "tenant_id");
        id.evidence().add(joinEvidence());
        tenant.evidence().add(joinEvidence());

        List<RelationshipCandidate> selected = generator.select(List.of(id, tenant), metadata, List.of(),
                DataProfileOptions.defaults());

        assertEquals(List.of(tenant), selected);
    }

    private MetadataSnapshot metadataWithCustomerId() {
        MetadataSnapshot metadata = new MetadataSnapshot();
        metadata.columnFacts().add(column("orders", "customer_id", "bigint"));
        metadata.columnFacts().add(column("customers", "id", "bigint"));
        metadata.indexFacts().add(new MetadataIndexFact(null, null, "orders", "idx_orders_customer_id",
                false, false, "BTREE", true, List.of("customer_id"), List.of(), List.of(), List.of(1)));
        metadata.indexFacts().add(new MetadataIndexFact(null, null, "customers", "PRIMARY", true, true,
                "BTREE", true, List.of("id"), List.of(), List.of(), List.of(1)));
        return metadata;
    }

    private MetadataSnapshot metadataFor(
            RelationshipCandidate candidate,
            String sourceType,
            String targetType
    ) {
        MetadataSnapshot metadata = new MetadataSnapshot();
        addColumns(metadata, candidate, sourceType, targetType);
        return metadata;
    }

    private MetadataSnapshot metadataFor(List<RelationshipCandidate> candidates, String type) {
        MetadataSnapshot metadata = new MetadataSnapshot();
        for (RelationshipCandidate candidate : candidates) {
            addColumns(metadata, candidate, type, type);
        }
        return metadata;
    }

    private void addColumns(
            MetadataSnapshot metadata,
            RelationshipCandidate candidate,
            String sourceType,
            String targetType
    ) {
        metadata.columnFacts().add(column(
                candidate.source().table().tableName(),
                candidate.source().column().columnName(),
                sourceType));
        metadata.columnFacts().add(column(
                candidate.target().table().tableName(),
                candidate.target().column().columnName(),
                targetType));
    }

    private MetadataColumnFact column(String table, String column, String type) {
        return new MetadataColumnFact(null, null, table, column, type, type, true, null, "", null, 1);
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

    private IdentifierRules mysqlRules() {
        return new IdentifierRules() {
            @Override
            public String normalize(String identifier) {
                return identifier == null ? "" : identifier.toLowerCase(java.util.Locale.ROOT);
            }

            @Override
            public QualifiedNameSemantics qualifiedNameSemantics() {
                return QualifiedNameSemantics.CATALOG_TABLE;
            }
        };
    }
}
