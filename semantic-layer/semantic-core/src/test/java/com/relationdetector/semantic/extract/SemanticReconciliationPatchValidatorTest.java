package com.relationdetector.semantic.extract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

final class SemanticReconciliationPatchValidatorTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void selectsOnlyAnExistingConflictVariant() {
        ObjectNode left = document("订单");
        ObjectNode right = document("销售订单");
        SemanticShardMergeResult merge = new SemanticShardResultMerger().merge(List.of(
                new SemanticShardNormalizedResult("shard-0001", left),
                new SemanticShardNormalizedResult("shard-0002", right)));
        String selectedHash = merge.conflicts().get(0).variants().get(1).hash();
        ObjectNode patch = JSON.createObjectNode();
        patch.putArray("resolutions").addObject()
                .put("section", "entities")
                .put("id", "entity:orders")
                .put("selectedVariantHash", selectedHash);
        patch.putArray("renames");
        patch.putArray("relations");

        ObjectNode resolved = new SemanticReconciliationPatchValidator().apply(
                merge, patch, evidenceBundle());

        assertEquals("销售订单", resolved.path("entities").get(0).path("name").asText());
    }

    @Test
    void rejectsUnknownEvidenceInAddedRelation() {
        SemanticShardMergeResult merge = new SemanticShardResultMerger().merge(List.of(
                new SemanticShardNormalizedResult("shard-0001", document("订单"))));
        ObjectNode patch = JSON.createObjectNode();
        patch.putArray("resolutions");
        patch.putArray("renames");
        patch.putArray("relations").addObject()
                .put("id", "relation:orders")
                .put("name", "关联")
                .put("type", "业务关系")
                .put("fromEntityRef", "entity:orders")
                .put("toEntityRef", "entity:orders")
                .putArray("evidenceRefs").add("unknown");

        assertThrows(SemanticExtractionValidationException.class,
                () -> new SemanticReconciliationPatchValidator().apply(merge, patch, evidenceBundle()));
    }

    private ObjectNode document(String name) {
        ObjectNode root = JSON.createObjectNode();
        root.putArray("entities").addObject()
                .put("id", "entity:orders")
                .put("name", name)
                .put("type", "业务实体")
                .put("physicalName", "shop.orders")
                .putArray("evidenceRefs").add("rel:orders");
        for (String section : List.of("events", "relations", "lineage", "metrics", "dimensions", "triplets",
                "reviewItems")) {
            root.putArray(section);
        }
        return root;
    }

    private ObjectNode evidenceBundle() {
        ObjectNode root = JSON.createObjectNode();
        root.putArray("tables").add("shop.orders");
        root.putArray("evidence");
        root.putArray("relationships").addObject().put("id", "rel:orders");
        for (String section : List.of("lineage", "derivedRelationships", "derivedLineage", "namingEvidence",
                "diagnostics", "eventCandidates", "tripletCandidates", "reviewItemCandidates")) {
            root.putArray(section);
        }
        return root;
    }
}
