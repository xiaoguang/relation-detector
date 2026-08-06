package com.relationdetector.semantic.extraction.artifact;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relationdetector.semantic.extraction.normalization.SemanticCanonicalIdentity;
import com.relationdetector.semantic.extraction.normalization.SemanticExtractionValidationException;
import com.relationdetector.semantic.internal.store.ExternalJsonRecordStore;

final class SemanticResultSelectionTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void rejectsNameChangesThatWouldChangeFormalIdentity() {
        assertMaterialRenameRejected(
                "business-entity",
                SemanticResultStore.Section.ENTITIES,
                businessEntity("Orders"));
        assertMaterialRenameRejected(
                "metric",
                SemanticResultStore.Section.METRICS,
                metric("Revenue"));
        assertMaterialRenameRejected(
                "dimension",
                SemanticResultStore.Section.DIMENSIONS,
                dimension("Region"));
    }

    @Test
    void acceptsNullNameWithDescriptionOnly() {
        try (TestSections state = new TestSections(tempDir.resolve("description-only"))) {
            ObjectNode entity = businessEntity("Orders");
            state.append(SemanticResultStore.Section.ENTITIES, entity);
            state.finish();

            ObjectNode patch = renamePatch(
                    SemanticResultStore.Section.ENTITIES, entity.path("id").asText());
            ObjectNode rename = (ObjectNode) patch.withArray("renames").get(0);
            rename.putNull("name");
            rename.put("description", "Curated description");

            assertDoesNotThrow(() -> state.selection.applyPatch(patch));
            JsonNode renamed = state.selection.renamed(
                    SemanticResultStore.Section.ENTITIES,
                    entity.path("id").asText(),
                    entity);
            assertEquals("Orders", renamed.path("name").asText());
            assertEquals("Curated description", renamed.path("description").asText());
        }
    }

    @Test
    void acceptsPhysicalEntityDisplayRenameAndCanonicalEquivalentBusinessName() {
        try (TestSections state = new TestSections(tempDir.resolve("equivalent-renames"))) {
            ObjectNode physical = physicalEntity("Orders", "shop.orders");
            ObjectNode business = businessEntity("Revenue");
            state.append(SemanticResultStore.Section.ENTITIES, physical);
            state.append(SemanticResultStore.Section.ENTITIES, business);
            state.finish();

            ObjectNode patch = emptyPatch();
            patch.withArray("renames").addObject()
                    .put("section", "entities")
                    .put("id", physical.path("id").asText())
                    .put("name", "Customer orders")
                    .putNull("description");
            patch.withArray("renames").addObject()
                    .put("section", "entities")
                    .put("id", business.path("id").asText())
                    .put("name", " REVENUE ")
                    .putNull("description");

            assertDoesNotThrow(() -> state.selection.applyPatch(patch));
        }
    }

    @Test
    void validatesRenameAgainstTheSelectedConflictVariant() {
        try (TestSections state = new TestSections(tempDir.resolve("selected-conflict"))) {
            ObjectNode selected = businessEntity("Revenue");
            ObjectNode nonSelected = selected.deepCopy().put("name", "Cost");
            ObjectNode conflict = JSON.createObjectNode().put("id", selected.path("id").asText());
            conflict.putArray("__semanticVariants")
                    .addObject()
                    .put("hash", "not-selected")
                    .set("document", nonSelected);
            conflict.withArray("__semanticVariants")
                    .addObject()
                    .put("hash", "selected")
                    .set("document", selected);
            state.append(SemanticResultStore.Section.ENTITIES, conflict);
            state.finish();

            ObjectNode patch = emptyPatch();
            patch.withArray("resolutions").addObject()
                    .put("section", "entities")
                    .put("id", selected.path("id").asText())
                    .put("selectedVariantHash", "selected");
            patch.withArray("renames").addObject()
                    .put("section", "entities")
                    .put("id", selected.path("id").asText())
                    .put("name", " REVENUE ")
                    .putNull("description");

            assertDoesNotThrow(() -> state.selection.applyPatch(patch));
        }
    }

    @Test
    void rejectedPatchDoesNotCommitEarlierConflictSelection() {
        try (TestSections state = new TestSections(tempDir.resolve("atomic-patch"))) {
            ObjectNode fallback = businessEntity("Cost");
            ObjectNode selected = fallback.deepCopy().put("name", "Revenue");
            ObjectNode conflict = JSON.createObjectNode().put("id", fallback.path("id").asText());
            conflict.putArray("__semanticVariants")
                    .addObject()
                    .put("hash", "fallback")
                    .set("document", fallback);
            conflict.withArray("__semanticVariants")
                    .addObject()
                    .put("hash", "selected")
                    .set("document", selected);
            state.append(SemanticResultStore.Section.ENTITIES, conflict);
            state.finish();

            ObjectNode patch = emptyPatch();
            patch.withArray("resolutions").addObject()
                    .put("section", "entities")
                    .put("id", fallback.path("id").asText())
                    .put("selectedVariantHash", "selected");
            patch.withArray("renames").addObject()
                    .put("section", "entities")
                    .put("id", "entity:missing")
                    .put("description", "invalid target");

            assertThrows(
                    SemanticExtractionValidationException.class,
                    () -> state.selection.applyPatch(patch));
            assertEquals(
                    "Cost",
                    state.selection.selectedDocument(
                            SemanticResultStore.Section.ENTITIES, conflict).path("name").asText());
        }
    }

    private void assertMaterialRenameRejected(
            String workspace,
            SemanticResultStore.Section section,
            ObjectNode value
    ) {
        try (TestSections state = new TestSections(tempDir.resolve(workspace))) {
            state.append(section, value);
            state.finish();
            ObjectNode patch = renamePatch(section, value.path("id").asText());
            ObjectNode rename = (ObjectNode) patch.withArray("renames").get(0);
            rename.put("name", "Materially different");
            rename.put("description", "Allowed display metadata");

            assertThrows(
                    SemanticExtractionValidationException.class,
                    () -> state.selection.applyPatch(patch));
        }
    }

    private ObjectNode businessEntity(String name) {
        List<String> grounding = List.of("relationship:orders");
        ObjectNode entity = base(name, "BUSINESS_ENTITY", grounding);
        entity.put("id", SemanticCanonicalIdentity.entity(
                null, name, "BUSINESS_ENTITY", null, grounding).canonicalId());
        return entity;
    }

    private ObjectNode physicalEntity(String name, String physicalName) {
        List<String> grounding = List.of("relationship:orders");
        ObjectNode entity = base(name, "BUSINESS_ENTITY", grounding)
                .put("physicalName", physicalName);
        entity.put("id", SemanticCanonicalIdentity.entity(
                physicalName, name, "BUSINESS_ENTITY", null, grounding).canonicalId());
        return entity;
    }

    private ObjectNode metric(String name) {
        ObjectNode metric = base(name, "CURRENCY", List.of("relationship:orders"))
                .put("physicalField", "shop.orders.amount");
        metric.putArray("sourceFields").add("shop.orders.amount");
        metric.put("id", SemanticCanonicalIdentity.metric(
                name,
                "CURRENCY",
                null,
                "shop.orders.amount",
                List.of("shop.orders.amount")));
        return metric;
    }

    private ObjectNode dimension(String name) {
        ObjectNode dimension = base(name, "ATTRIBUTE", List.of("relationship:orders"))
                .put("physicalField", "shop.orders.region_id")
                .put("dimensionTable", "shop.regions");
        dimension.put("id", SemanticCanonicalIdentity.dimension(
                name,
                "ATTRIBUTE",
                null,
                "shop.orders.region_id",
                "shop.regions"));
        return dimension;
    }

    private ObjectNode base(String name, String machineType, List<String> grounding) {
        ObjectNode result = JSON.createObjectNode()
                .put("name", name)
                .put("machineType", machineType);
        ArrayNode refs = result.putArray("ownedGroundingRefs");
        grounding.forEach(refs::add);
        return result;
    }

    private ObjectNode renamePatch(SemanticResultStore.Section section, String id) {
        ObjectNode patch = emptyPatch();
        patch.withArray("renames").addObject()
                .put("section", section.wireName())
                .put("id", id);
        return patch;
    }

    private ObjectNode emptyPatch() {
        ObjectNode patch = JSON.createObjectNode();
        patch.putArray("resolutions");
        patch.putArray("renames");
        return patch;
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

        @Override
        public void close() {
            stores.values().forEach(ExternalJsonRecordStore::close);
        }
    }
}
