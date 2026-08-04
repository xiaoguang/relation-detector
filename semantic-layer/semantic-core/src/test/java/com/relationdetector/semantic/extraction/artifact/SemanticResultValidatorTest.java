package com.relationdetector.semantic.extraction.artifact;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relationdetector.semantic.evidence.SemanticEvidenceStore;
import com.relationdetector.semantic.extraction.normalization.SemanticEvidenceLookup;
import com.relationdetector.semantic.extraction.normalization.SemanticExtractionValidationException;
import com.relationdetector.semantic.internal.store.ExternalJsonRecordStore;

final class SemanticResultValidatorTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String EVIDENCE = "evidence:ddl";
    private static final String FACT = "relationship:orders-customer";
    private static final String EVENT_CANDIDATE = "event-candidate:orders";
    private static final String TRIPLET_CANDIDATE = "triplet-candidate:orders-customer";

    @TempDir
    Path tempDir;

    @Test
    void revalidatesTheReconciliationSelectedVariantAgainstPhysicalIdentity() {
        try (TestSections state = new TestSections(tempDir.resolve("selected-variant"))) {
            ObjectNode conflict = JSON.createObjectNode().put("id", "entity:orders");
            conflict.putArray("__semanticVariants")
                    .addObject()
                    .put("hash", "valid")
                    .set("document", entity("entity:orders", "shop.orders"));
            conflict.withArray("__semanticVariants")
                    .addObject()
                    .put("hash", "partial-identity")
                    .set("document", entity("entity:orders", "orders"));
            state.append(SemanticResultStore.Section.ENTITIES, conflict);
            state.finish();

            ObjectNode patch = JSON.createObjectNode();
            patch.putArray("resolutions").addObject()
                    .put("section", "entities")
                    .put("id", "entity:orders")
                    .put("selectedVariantHash", "partial-identity");
            patch.putArray("renames");
            state.selection.applyPatch(patch);

            assertThrows(SemanticExtractionValidationException.class,
                    () -> state.validator(lookup()).validate());
        }
    }

    @Test
    void rejectsEvidenceOnlyOwnedGroundingAtTheFinalBoundary() {
        try (TestSections state = new TestSections(tempDir.resolve("evidence-grounding"))) {
            ObjectNode entity = entity("entity:orders", "shop.orders");
            entity.withArray("ownedGroundingRefs").removeAll().add(EVIDENCE);
            state.append(SemanticResultStore.Section.ENTITIES, entity);
            state.finish();

            assertThrows(SemanticExtractionValidationException.class,
                    () -> state.validator(lookup()).validate());
        }
    }

    @Test
    void rejectsEventAndTripletCandidatesFromTheWrongTypedSections() {
        try (TestSections eventState = new TestSections(tempDir.resolve("event-candidate"))) {
            ObjectNode event = item("event:orders", TRIPLET_CANDIDATE);
            event.put("eventCandidateRef", TRIPLET_CANDIDATE);
            eventState.append(SemanticResultStore.Section.EVENTS, event);
            eventState.finish();
            assertThrows(SemanticExtractionValidationException.class,
                    () -> eventState.validator(lookup()).validate());
        }

        try (TestSections tripletState = new TestSections(tempDir.resolve("triplet-candidate"))) {
            ObjectNode triplet = item("triplet:orders", EVENT_CANDIDATE);
            triplet.put("candidateRef", EVENT_CANDIDATE);
            tripletState.append(SemanticResultStore.Section.TRIPLETS, triplet);
            tripletState.finish();
            assertThrows(SemanticExtractionValidationException.class,
                    () -> tripletState.validator(lookup()).validate());
        }
    }

    @Test
    void rejectsPhysicalColumnsThatDropCatalogAndTableIdentity() {
        try (TestSections state = new TestSections(tempDir.resolve("physical-column"))) {
            ObjectNode lineage = item("lineage:orders", FACT);
            lineage.putArray("fromPhysical").add("orders.id");
            lineage.put("toPhysical", "shop.orders.id");
            state.append(SemanticResultStore.Section.LINEAGE, lineage);
            state.finish();

            assertThrows(SemanticExtractionValidationException.class,
                    () -> state.validator(lookup()).validate());
        }
    }

    @Test
    void rejectsUnclosedSemanticEntityReferences() {
        try (TestSections state = new TestSections(tempDir.resolve("semantic-ref"))) {
            ObjectNode event = item("event:orders", EVENT_CANDIDATE);
            event.put("eventCandidateRef", EVENT_CANDIDATE);
            event.putArray("inputEntityRefs").add("entity:missing");
            state.append(SemanticResultStore.Section.EVENTS, event);
            state.finish();

            assertThrows(SemanticExtractionValidationException.class,
                    () -> state.validator(lookup()).validate());
        }
    }

    @Test
    void reviewTargetsCloseAgainstTheirDeclaredSemanticOrEvidenceSection() {
        try (TestSections state = new TestSections(tempDir.resolve("review-target-valid"))) {
            ObjectNode review = item("review:relationship", FACT);
            review.put("targetSection", "relationships");
            review.put("targetRef", FACT);
            state.append(SemanticResultStore.Section.REVIEW_ITEMS, review);
            state.finish();

            assertDoesNotThrow(() -> state.validator(lookup()).validate());
        }

        try (TestSections state = new TestSections(tempDir.resolve("review-target-wrong-section"))) {
            ObjectNode review = item("review:candidate", FACT);
            review.put("targetSection", "eventCandidates");
            review.put("targetRef", TRIPLET_CANDIDATE);
            state.append(SemanticResultStore.Section.REVIEW_ITEMS, review);
            state.finish();

            assertThrows(SemanticExtractionValidationException.class,
                    () -> state.validator(lookup()).validate());
        }
    }

    private ObjectNode entity(String id, String physicalName) {
        return item(id, FACT).put("physicalName", physicalName);
    }

    private ObjectNode item(String id, String grounding) {
        ObjectNode item = JSON.createObjectNode().put("id", id);
        item.putArray("ownedGroundingRefs").add(grounding);
        item.putArray("evidenceRefs").add(EVIDENCE);
        return item;
    }

    private SemanticEvidenceLookup lookup() {
        Map<SemanticEvidenceStore.Section, Set<String>> members = new EnumMap<>(
                SemanticEvidenceStore.Section.class);
        members.put(SemanticEvidenceStore.Section.EVIDENCE, Set.of(EVIDENCE));
        members.put(SemanticEvidenceStore.Section.RELATIONSHIPS, Set.of(FACT));
        members.put(SemanticEvidenceStore.Section.EVENT_CANDIDATES, Set.of(EVENT_CANDIDATE));
        members.put(SemanticEvidenceStore.Section.TRIPLET_CANDIDATES, Set.of(TRIPLET_CANDIDATE));
        return new SemanticEvidenceLookup() {
            @Override
            public boolean containsReference(String reference) {
                return members.values().stream().anyMatch(values -> values.contains(reference));
            }

            @Override
            public boolean containsReference(
                    SemanticEvidenceStore.Section section,
                    String reference
            ) {
                return members.getOrDefault(section, Set.of()).contains(reference);
            }

            @Override
            public Optional<JsonNode> findEvidence(String reference) {
                return containsReference(SemanticEvidenceStore.Section.EVIDENCE, reference)
                        ? Optional.of(JSON.createObjectNode().put("id", reference))
                        : Optional.empty();
            }

            @Override
            public boolean containsPhysicalTable(String table) {
                return Set.of("shop.orders", "shop.customers").contains(table);
            }

            @Override
            public boolean containsPhysicalColumn(String column) {
                return Set.of("shop.orders.id", "shop.orders.customer_id", "shop.customers.id")
                        .contains(column);
            }
        };
    }

    private static final class TestSections implements AutoCloseable {
        private final Map<SemanticResultStore.Section, ExternalJsonRecordStore> stores =
                new EnumMap<>(SemanticResultStore.Section.class);
        private final SemanticResultSelection selection;

        private TestSections(Path workspace) {
            for (SemanticResultStore.Section section : SemanticResultStore.Section.values()) {
                stores.put(section, new ExternalJsonRecordStore(workspace.resolve(section.wireName())));
            }
            selection = new SemanticResultSelection(stores);
        }

        private void append(SemanticResultStore.Section section, ObjectNode value) {
            stores.get(section).append(value.path("id").asText(), value);
        }

        private void finish() {
            stores.values().forEach(ExternalJsonRecordStore::finish);
        }

        private SemanticResultValidator validator(SemanticEvidenceLookup lookup) {
            return new SemanticResultValidator(lookup, stores, selection);
        }

        @Override
        public void close() {
            stores.values().forEach(ExternalJsonRecordStore::close);
        }
    }
}
