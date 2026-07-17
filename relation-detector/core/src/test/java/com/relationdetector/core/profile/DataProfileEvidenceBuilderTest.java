package com.relationdetector.core.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.Enums.EvidenceType;
import com.relationdetector.contracts.Enums.EvidenceSourceType;
import com.relationdetector.contracts.Enums.RelationSubType;
import com.relationdetector.contracts.Enums.RelationType;
import com.relationdetector.contracts.model.ColumnRef;
import com.relationdetector.contracts.model.Endpoint;
import com.relationdetector.contracts.model.Evidence;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.model.TableId;
import com.relationdetector.contracts.spi.DataProfileOptions;
import com.relationdetector.contracts.spi.ProfileRequest;

class DataProfileEvidenceBuilderTest {
    private final DataProfileEvidenceBuilder builder = new DataProfileEvidenceBuilder();

    @Test
    void highContainmentProducesContainmentEvidence() {
        ProfileRequest request = request(options());
        DataProfileMetrics metrics = DataProfileMetrics.live(120, 100, 99, 1, 100, false, false);

        List<Evidence> evidence = builder.build(request, metrics, "test-profiler");

        Evidence containment = only(evidence, EvidenceType.VALUE_CONTAINMENT_HIGH);
        assertEquals("0.99", String.valueOf(containment.attributes().get("containmentRatio")));
        assertEquals(99L, containment.attributes().get("matchedDistinctSourceValues"));
        assertEquals(100L, containment.attributes().get("sourceDistinctValues"));
        assertEquals(120L, containment.attributes().get("sourceNonNullRows"));
        assertEquals(100L, containment.attributes().get("targetDistinctValues"));
        assertFalse(containment.attributes().containsKey("sourceDistinctValuesSampled"));
        assertFalse(containment.attributes().containsKey("sourceNonNullRowsSampled"));
        assertFalse(containment.attributes().containsKey("targetDistinctValuesSampled"));
        assertFalse(containment.attributes().containsKey("sampleRows"));
        assertFalse(containment.attributes().containsKey("maxDistinctValues"));
        assertEquals("LIVE_DATABASE", containment.attributes().get("profileMode"));
    }

    @Test
    void overlapBelowContainmentProducesOverlapEvidence() {
        DataProfileOptions options = DataProfileOptions.defaults()
                .withMinContainmentRatio(0.98d)
                .withMinOverlapRatio(0.70d);
        DataProfileMetrics metrics = DataProfileMetrics.live(120, 100, 75, 25, 100, false, false);

        List<Evidence> evidence = builder.build(request(options), metrics, "test-profiler");

        assertTrue(evidence.stream().noneMatch(e -> e.type() == EvidenceType.VALUE_CONTAINMENT_HIGH));
        Evidence overlap = only(evidence, EvidenceType.VALUE_OVERLAP_HIGH);
        assertEquals("0.75", String.valueOf(overlap.attributes().get("overlapRatio")));
    }

    @Test
    void negativeMismatchRequiresConfiguredGates() {
        DataProfileOptions options = DataProfileOptions.defaults()
                .withMaxMismatchRatio(0.50d)
                .withMinRowsForNegative(50)
                .withMinDistinctValues(20);
        DataProfileMetrics metrics = DataProfileMetrics.live(200, 100, 20, 80, 100, false, false);

        List<Evidence> evidence = builder.build(request(declaredCandidate(EvidenceType.DDL_FOREIGN_KEY), options),
                metrics, "test-profiler");

        Evidence mismatch = only(evidence, EvidenceType.NEGATIVE_VALUE_MISMATCH);
        assertEquals("0.8", String.valueOf(mismatch.attributes().get("missingRatio")));
        assertEquals(80L, mismatch.attributes().get("missingDistinctSourceValues"));
        assertEquals("DECLARED_FOREIGN_KEY_ONLY", mismatch.attributes().get("negativePolicy"));
    }

    @Test
    void inferredRelationshipDoesNotProduceNegativeMismatch() {
        DataProfileOptions options = DataProfileOptions.defaults()
                .withMaxMismatchRatio(0.50d)
                .withMinRowsForNegative(50)
                .withMinDistinctValues(20);
        DataProfileMetrics metrics = DataProfileMetrics.live(200, 100, 20, 80, 100, false, false);

        List<Evidence> evidence = builder.build(request(options), metrics, "test-profiler");

        assertTrue(evidence.stream().noneMatch(e -> e.type() == EvidenceType.NEGATIVE_VALUE_MISMATCH));
    }

    @Test
    void inferredSqlAndNamingCandidatesKeepPositiveEvidenceWithoutNegativeEvidence() {
        DataProfileOptions options = DataProfileOptions.defaults()
                .withMinContainmentRatio(0.98d)
                .withMinOverlapRatio(0.70d)
                .withMaxMismatchRatio(0.20d)
                .withMinRowsForNegative(50)
                .withMinDistinctValues(20);
        DataProfileMetrics metrics = DataProfileMetrics.live(200, 100, 75, 25, 100, false, false);

        for (EvidenceType type : List.of(
                EvidenceType.SQL_LOG_JOIN,
                EvidenceType.SQL_LOG_EXISTS,
                EvidenceType.SQL_LOG_SUBQUERY_IN,
                EvidenceType.NAMING_MATCH)) {
            RelationshipCandidate candidate = inferredCandidate(type);
            List<Evidence> evidence = builder.build(request(candidate, options), metrics, "test-profiler");

            only(evidence, EvidenceType.VALUE_OVERLAP_HIGH);
            assertTrue(evidence.stream().noneMatch(e -> e.type() == EvidenceType.NEGATIVE_VALUE_MISMATCH),
                    type.name());
        }
    }

    @Test
    void metadataDeclaredForeignKeyCanProduceNegativeMismatch() {
        DataProfileOptions options = DataProfileOptions.defaults()
                .withMaxMismatchRatio(0.50d)
                .withMinRowsForNegative(50)
                .withMinDistinctValues(20);
        DataProfileMetrics metrics = DataProfileMetrics.live(200, 100, 20, 80, 100, false, false);

        List<Evidence> evidence = builder.build(
                request(declaredCandidate(EvidenceType.METADATA_FOREIGN_KEY), options), metrics, "test-profiler");

        only(evidence, EvidenceType.NEGATIVE_VALUE_MISMATCH);
    }

    @Test
    void conditionalDeclaredRelationshipDoesNotProduceNegativeMismatch() {
        RelationshipCandidate candidate = declaredCandidate(EvidenceType.DDL_FOREIGN_KEY);
        candidate.attributes().put("conditional", true);
        candidate.attributes().put("polymorphic", true);
        DataProfileOptions options = DataProfileOptions.defaults()
                .withMaxMismatchRatio(0.50d)
                .withMinRowsForNegative(50)
                .withMinDistinctValues(20);
        DataProfileMetrics metrics = DataProfileMetrics.live(200, 100, 20, 80, 100, false, false);

        List<Evidence> evidence = builder.build(request(candidate, options), metrics, "test-profiler");

        assertTrue(evidence.stream().noneMatch(e -> e.type() == EvidenceType.NEGATIVE_VALUE_MISMATCH));
    }

    @Test
    void structuralEvidenceGuardPreventsNegativeMismatchBeforeRelationshipMerge() {
        RelationshipCandidate candidate = declaredCandidate(EvidenceType.DDL_FOREIGN_KEY);
        Evidence declared = candidate.evidence().remove(0);
        candidate.evidence().add(new Evidence(
                declared.type(),
                declared.score(),
                declared.sourceType(),
                declared.source(),
                declared.detail(),
                java.util.Map.of(
                        "conditional", true,
                        "conditions", List.of(java.util.Map.of(
                                "discriminator", "orders.party_type",
                                "operator", "EQUALS",
                                "value", "customer")))));
        DataProfileOptions options = DataProfileOptions.defaults()
                .withMaxMismatchRatio(0.50d)
                .withMinRowsForNegative(50)
                .withMinDistinctValues(20);
        DataProfileMetrics metrics = DataProfileMetrics.live(200, 100, 20, 80, 100, false, false);

        List<Evidence> evidence = builder.build(request(candidate, options), metrics, "test-profiler");

        assertTrue(evidence.stream().noneMatch(e -> e.type() == EvidenceType.NEGATIVE_VALUE_MISMATCH));
    }

    @Test
    void smallSamplesDoNotProduceStrongProfileEvidence() {
        DataProfileOptions options = DataProfileOptions.defaults().withMinDistinctValues(20);
        DataProfileMetrics metrics = DataProfileMetrics.live(10, 5, 5, 0, 5, false, false);

        List<Evidence> evidence = builder.build(request(options), metrics, "test-profiler");

        assertTrue(evidence.isEmpty());
    }

    private ProfileRequest request(DataProfileOptions options) {
        return new ProfileRequest(candidate(), options);
    }

    private ProfileRequest request(RelationshipCandidate candidate, DataProfileOptions options) {
        return new ProfileRequest(candidate, options);
    }

    private DataProfileOptions options() {
        return DataProfileOptions.defaults()
                .withMinContainmentRatio(0.98d)
                .withMinOverlapRatio(0.80d)
                .withMinDistinctValues(20)
                .withMinRowsForNegative(50);
    }

    private RelationshipCandidate candidate() {
        return new RelationshipCandidate(
                Endpoint.column(ColumnRef.of(TableId.of(null, "orders"), "customer_id")),
                Endpoint.column(ColumnRef.of(TableId.of(null, "customers"), "id")),
                RelationType.FK_LIKE,
                RelationSubType.PROFILE_SUPPORTED_FK);
    }

    private RelationshipCandidate declaredCandidate(EvidenceType type) {
        RelationshipCandidate candidate = new RelationshipCandidate(
                Endpoint.column(ColumnRef.of(TableId.of(null, "orders"), "customer_id")),
                Endpoint.column(ColumnRef.of(TableId.of(null, "customers"), "id")),
                RelationType.FK_LIKE,
                type == EvidenceType.DDL_FOREIGN_KEY
                        ? RelationSubType.DDL_DECLARED_FK
                        : RelationSubType.DECLARED_FK);
        candidate.evidence().add(Evidence.of(type, 0.98d,
                type == EvidenceType.DDL_FOREIGN_KEY ? EvidenceSourceType.DDL_FILE : EvidenceSourceType.METADATA,
                "test", "declared foreign key"));
        return candidate;
    }

    private RelationshipCandidate inferredCandidate(EvidenceType type) {
        RelationshipCandidate candidate = candidate();
        candidate.evidence().add(Evidence.of(type, 0.80d,
                type == EvidenceType.NAMING_MATCH
                        ? EvidenceSourceType.NAMING_HEURISTIC
                        : EvidenceSourceType.PLAIN_SQL,
                "test", "inferred relationship"));
        return candidate;
    }

    private Evidence only(List<Evidence> evidence, EvidenceType type) {
        return evidence.stream()
                .filter(e -> e.type() == type)
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing " + type + " in " + evidence));
    }
}
