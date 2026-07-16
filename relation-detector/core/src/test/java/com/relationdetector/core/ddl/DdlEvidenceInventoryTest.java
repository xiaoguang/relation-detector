package com.relationdetector.core.ddl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.Enums.EvidenceSourceType;
import com.relationdetector.contracts.Enums.EvidenceType;
import com.relationdetector.contracts.Enums.RelationSubType;
import com.relationdetector.contracts.Enums.RelationType;
import com.relationdetector.contracts.model.ColumnRef;
import com.relationdetector.contracts.model.Endpoint;
import com.relationdetector.contracts.model.Evidence;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.model.TableId;
import com.relationdetector.contracts.parse.DdlEvent;
import com.relationdetector.contracts.parse.SourceProvenance;
import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.core.identity.CanonicalEndpointKey;
import com.relationdetector.core.relation.DdlRelationExtractionVisitor;
import com.relationdetector.core.relation.RelationshipMerger;

class DdlEvidenceInventoryTest {
    @Test
    void crossFileIndexAndUniqueEnhanceExistingRelationship() {
        RelationshipCandidate relationship = relationship("shop", "orders", "customer_id",
                "shop", "customers", "id");
        DdlEvidenceInventory inventory = new DdlEvidenceInventory();
        inventory.addSourceIndex(key("shop", "orders", "customer_id"),
                observation("indexes.sql", "SOURCE_INDEX", "INDEX"));
        inventory.addTargetUnique(key("shop", "customers", "id"),
                observation("tables.sql", "TARGET_UNIQUE", "PRIMARY_KEY"));

        inventory.enhance(List.of(relationship));

        assertTrue(hasEvidence(relationship, EvidenceType.SOURCE_INDEX, "indexes.sql"));
        assertTrue(hasEvidence(relationship, EvidenceType.TARGET_UNIQUE, "tables.sql"));
    }

    @Test
    void sameDdlObservationEnhancesOneFactOnlyOnceAcrossCandidatesAndRuns() {
        RelationshipCandidate first = relationship("shop", "orders", "customer_id",
                "shop", "customers", "id");
        RelationshipCandidate second = relationship("shop", "orders", "customer_id",
                "shop", "customers", "id");
        DdlEvidenceInventory inventory = new DdlEvidenceInventory();
        inventory.addSourceIndex(key("shop", "orders", "customer_id"),
                observation("indexes.sql", "SOURCE_INDEX", "INDEX"));
        inventory.addSourceIndex(key("shop", "orders", "customer_id"),
                new DdlEvidenceInventory.Observation(
                        "SOURCE_INDEX", "INDEX", EvidenceSourceType.DDL_FILE, "indexes.sql", 1,
                        Map.of("sourceFile", "indexes.sql", "sourceStatementId", "indexes.sql:1-1",
                                "displayName", "idx_orders_customer")));

        inventory.enhance(List.of(first, second));
        inventory.enhance(List.of(first, second));

        assertEquals(1, List.of(first, second).stream()
                .flatMap(candidate -> candidate.evidence().stream())
                .filter(evidence -> evidence.type() == EvidenceType.SOURCE_INDEX)
                .count());
    }

    @Test
    void ddlFactIdentityKeepsDifferentRelationTypesIndependent() {
        RelationshipCandidate fkLike = relationship("shop", "orders", "customer_id",
                "shop", "customers", "id");
        RelationshipCandidate coOccurrence = new RelationshipCandidate(
                fkLike.source(), fkLike.target(), RelationType.CO_OCCURRENCE,
                RelationSubType.COLUMN_CO_OCCURRENCE);
        DdlEvidenceInventory inventory = new DdlEvidenceInventory();
        inventory.addSourceIndex(key("shop", "orders", "customer_id"),
                observation("indexes.sql", "SOURCE_INDEX", "INDEX"));

        inventory.enhance(List.of(fkLike, coOccurrence));

        assertEquals(2, List.of(fkLike, coOccurrence).stream()
                .flatMap(candidate -> candidate.evidence().stream())
                .filter(evidence -> evidence.type() == EvidenceType.SOURCE_INDEX)
                .count());
    }

    @Test
    void distinctDdlProvenanceSurvivesWhileExactProvenanceRepeatsFold() {
        RelationshipCandidate relationship = relationship("shop", "orders", "customer_id",
                "shop", "customers", "id");
        DdlEvidenceInventory inventory = new DdlEvidenceInventory();
        DdlEvidenceInventory.Observation first = provenanceObservation(
                "tables.sql:1-1", "block-1", "TABLE", "orders");
        DdlEvidenceInventory.Observation second = provenanceObservation(
                "tables.sql:2-2", "block-2", "INDEX", "idx_orders_customer");
        inventory.addSourceIndex(key("shop", "orders", "customer_id"), first);
        inventory.addSourceIndex(key("shop", "orders", "customer_id"), second);
        inventory.addSourceIndex(key("shop", "orders", "customer_id"), first);

        inventory.enhance(List.of(relationship));
        inventory.enhance(List.of(relationship));

        List<Evidence> sourceIndexes = relationship.evidence().stream()
                .filter(evidence -> evidence.type() == EvidenceType.SOURCE_INDEX)
                .toList();
        assertEquals(2, sourceIndexes.size());
        assertEquals(List.of("tables.sql:1-1", "tables.sql:2-2"), sourceIndexes.stream()
                .map(evidence -> String.valueOf(evidence.attributes().get("sourceStatementId")))
                .sorted()
                .toList());
    }

    @Test
    void schemaQualifiedInventoryNeverEnhancesBareOrDifferentSchemaEndpoint() {
        RelationshipCandidate relationship = relationship(null, "orders", "customer_id",
                "archive", "customers", "id");
        DdlEvidenceInventory inventory = new DdlEvidenceInventory();
        inventory.addSourceIndex(key("shop", "orders", "customer_id"),
                observation("indexes.sql", "SOURCE_INDEX", "INDEX"));
        inventory.addTargetUnique(key("shop", "customers", "id"),
                observation("tables.sql", "TARGET_UNIQUE", "PRIMARY_KEY"));

        inventory.enhance(List.of(relationship));

        assertFalse(relationship.evidence().stream().anyMatch(evidence ->
                evidence.type() == EvidenceType.SOURCE_INDEX || evidence.type() == EvidenceType.TARGET_UNIQUE));
    }

    @Test
    void standaloneIndexNeverCreatesRelationship() {
        DdlEvidenceInventory inventory = new DdlEvidenceInventory();
        inventory.addSourceIndex(key("shop", "orders", "customer_id"),
                observation("indexes.sql", "SOURCE_INDEX", "INDEX"));
        List<RelationshipCandidate> relationships = new java.util.ArrayList<>();

        inventory.enhance(relationships);

        assertTrue(relationships.isEmpty(), "An index is auxiliary evidence, not a relationship fact");
    }

    @Test
    void endpointFactsEnhanceAndOrientReverseLexicalCandidate() {
        RelationshipCandidate relationship = new RelationshipCandidate(
                Endpoint.column(ColumnRef.of(TableId.of("shop", "customers"), "id")),
                Endpoint.column(ColumnRef.of(TableId.of("shop", "orders"), "customer_id")),
                RelationType.CO_OCCURRENCE,
                RelationSubType.COLUMN_CO_OCCURRENCE);
        relationship.evidence().add(com.relationdetector.contracts.model.Evidence.of(
                EvidenceType.SQL_LOG_JOIN, 0.55d, EvidenceSourceType.PLAIN_SQL,
                "query.sql", "customers.id = orders.customer_id"));
        DdlEvidenceInventory inventory = new DdlEvidenceInventory();
        inventory.addSourceIndex(key("shop", "orders", "customer_id"),
                observation("indexes.sql", "SOURCE_INDEX", "INDEX"));
        inventory.addTargetUnique(key("shop", "customers", "id"),
                observation("tables.sql", "TARGET_UNIQUE", "PRIMARY_KEY"));

        inventory.enhance(List.of(relationship));
        RelationshipCandidate merged = new RelationshipMerger().merge(List.of(relationship), 0.0d).get(0);

        assertEquals("shop.orders.customer_id", merged.source().displayName());
        assertEquals("shop.customers.id", merged.target().displayName());
        assertTrue(hasEvidenceAttribute(merged, EvidenceType.SOURCE_INDEX,
                "indexEndpoint", "shop.orders.customer_id"));
        assertTrue(hasEvidenceAttribute(merged, EvidenceType.TARGET_UNIQUE,
                "uniqueEndpoint", "shop.customers.id"));
        assertTrue(hasEvidenceAttribute(merged, EvidenceType.SOURCE_INDEX,
                "endpointSide", "source"));
        assertTrue(hasEvidenceAttribute(merged, EvidenceType.TARGET_UNIQUE,
                "endpointSide", "target"));
    }

    @Test
    void compositeUniqueMemberDoesNotEnhanceSingleColumnRelationship() {
        DdlEvent firstMember = new DdlEvent(
                StructuredParseEventType.DDL_INDEX,
                SourceProvenance.source("tables.sql", 7),
                "", "", "", "",
                "shop.customers", "tenant_id", "TARGET_UNIQUE", "UNIQUE_CONSTRAINT", 1, 2);
        DdlEvent secondMember = new DdlEvent(
                StructuredParseEventType.DDL_INDEX,
                SourceProvenance.source("tables.sql", 7),
                "", "", "", "",
                "shop.customers", "external_id", "TARGET_UNIQUE", "UNIQUE_CONSTRAINT", 2, 2);
        DdlEvidenceInventory inventory = new DdlRelationExtractionVisitor().inventory(
                List.of(firstMember, secondMember), EvidenceSourceType.DDL_FILE, "tables.sql");
        RelationshipCandidate relationship = relationship(
                "shop", "orders", "customer_external_id",
                "shop", "customers", "external_id");

        inventory.enhance(List.of(relationship));

        assertFalse(relationship.evidence().stream().anyMatch(evidence ->
                        evidence.type() == EvidenceType.TARGET_UNIQUE),
                "A member of UNIQUE(tenant_id, external_id) is not independently unique");
    }

    private DdlEvidenceInventory.Observation observation(String source, String role, String kind) {
        return new DdlEvidenceInventory.Observation(
                role, kind, EvidenceSourceType.DDL_FILE, source, 1,
                Map.of("sourceFile", source, "sourceStatementId", source + ":1-1"));
    }

    private DdlEvidenceInventory.Observation provenanceObservation(
            String statementId,
            String blockId,
            String objectType,
            String objectName
    ) {
        return new DdlEvidenceInventory.Observation(
                "SOURCE_INDEX", "INDEX", EvidenceSourceType.DDL_FILE, "tables.sql", 1,
                Map.of(
                        "sourceFile", "tables.sql",
                        "sourceStatementId", statementId,
                        "sourceBlockId", blockId,
                        "sourceObjectType", objectType,
                        "sourceObjectName", objectName));
    }

    private CanonicalEndpointKey key(String schema, String table, String column) {
        return CanonicalEndpointKey.from(Endpoint.column(ColumnRef.of(TableId.of(schema, table), column)));
    }

    private RelationshipCandidate relationship(
            String sourceSchema,
            String sourceTable,
            String sourceColumn,
            String targetSchema,
            String targetTable,
            String targetColumn
    ) {
        return new RelationshipCandidate(
                Endpoint.column(ColumnRef.of(TableId.of(sourceSchema, sourceTable), sourceColumn)),
                Endpoint.column(ColumnRef.of(TableId.of(targetSchema, targetTable), targetColumn)),
                RelationType.FK_LIKE,
                RelationSubType.DDL_DECLARED_FK);
    }

    private boolean hasEvidence(RelationshipCandidate relationship, EvidenceType type, String source) {
        return relationship.evidence().stream().anyMatch(evidence ->
                evidence.type() == type && source.equals(evidence.source()));
    }

    private boolean hasEvidenceAttribute(
            RelationshipCandidate relationship,
            EvidenceType type,
            String key,
            String value
    ) {
        return relationship.evidence().stream().anyMatch(evidence ->
                evidence.type() == type && value.equals(evidence.attributes().get(key)));
    }
}
