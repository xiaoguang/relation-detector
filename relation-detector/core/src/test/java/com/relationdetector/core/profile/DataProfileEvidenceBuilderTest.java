package com.relationdetector.core.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.Enums.EvidenceType;
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

        List<Evidence> evidence = builder.build(request(options), metrics, "test-profiler");

        Evidence mismatch = only(evidence, EvidenceType.NEGATIVE_VALUE_MISMATCH);
        assertEquals("0.8", String.valueOf(mismatch.attributes().get("missingRatio")));
        assertEquals(80L, mismatch.attributes().get("missingDistinctSourceValues"));
    }

    @Test
    void partialOfflineSampleDoesNotProduceNegativeMismatch() {
        DataProfileOptions options = DataProfileOptions.defaults()
                .withMaxMismatchRatio(0.50d)
                .withMinRowsForNegative(50)
                .withMinDistinctValues(20);
        DataProfileMetrics metrics = DataProfileMetrics.offlinePartial(200, 100, 20, 80, 100);

        List<Evidence> evidence = builder.build(request(options), metrics, "offline-profiler");

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

    private Evidence only(List<Evidence> evidence, EvidenceType type) {
        return evidence.stream()
                .filter(e -> e.type() == type)
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing " + type + " in " + evidence));
    }
}
