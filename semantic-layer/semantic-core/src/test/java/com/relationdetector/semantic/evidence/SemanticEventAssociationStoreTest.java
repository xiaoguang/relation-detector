package com.relationdetector.semantic.evidence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relationdetector.semantic.event.SemanticEventCandidate;
import com.relationdetector.semantic.extraction.shard.SemanticShardingException;

final class SemanticEventAssociationStoreTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    java.nio.file.Path tempDir;

    @Test
    void externallyJoinsTypedEventAndFactKeysWithStableDeduplication() {
        try (SemanticEventAssociationStore store =
                     new SemanticEventAssociationStore(tempDir.resolve("associations"))) {
            store.appendEvent(event());
            store.appendRelationship(relationship(
                    "relationship:endpoint", "shop.audit_log.id", "shop.users.id"));
            store.appendRelationship(relationship(
                    "relationship:pair", "shop.orders.customer_id", "shop.audit_log.id"));
            store.appendRelationship(relationship(
                    "relationship:unrelated", "shop.products.id", "shop.categories.id"));
            store.appendDerivedLineage(lineage(
                    "derived:table", "shop.orders.customer_id", "shop.audit_log.id"));
            store.appendDerivedLineage(lineage(
                    "derived:unrelated", "shop.products.id", "shop.categories.id"));
            store.finish();

            assertEquals(
                    List.of("relationship:endpoint", "relationship:pair"),
                    store.relationshipRefs("event:write-audit"));
            assertEquals(
                    List.of("derived:table"),
                    store.derivedLineageRefs("event:write-audit"));
            assertEquals(3, store.referenceCount("event:write-audit"));
            long expectedBytes = 2L * List.of(
                            "relationship:endpoint", "relationship:pair", "derived:table")
                    .stream()
                    .mapToLong(reference -> JSON.valueToTree(reference).toString()
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8).length + 1L)
                    .sum();
            assertEquals(expectedBytes, store.estimatedReferenceBytes("event:write-audit"));
        }
    }

    @Test
    void rejectsHighFanoutBeforeMaterializingReferenceLists() {
        try (SemanticEventAssociationStore store =
                     new SemanticEventAssociationStore(tempDir.resolve("high-fanout"))) {
            store.appendEvent(event());
            for (int index = 0; index < 100; index++) {
                store.appendRelationship(relationship(
                        "relationship:%04d".formatted(index),
                        "shop.orders.customer_id",
                        "shop.audit_log.id"));
            }
            store.finish();

            assertThrows(SemanticShardingException.class,
                    () -> SemanticEventInputBudget.requireWithin(
                            64, store.estimatedReferenceBytes("event:write-audit"), 20));
        }
    }

    private SemanticEventCandidate event() {
        return new SemanticEventCandidate(
                "event:write-audit",
                "SQL_WRITE_OPERATION",
                "ROUTINE",
                "shop.write_audit",
                "PROCEDURE",
                "write_audit",
                "procedures/write_audit.sql",
                "statement:1",
                "write audit",
                "",
                "",
                List.of("INSERT"),
                List.of("shop.orders.customer_id"),
                List.of("shop.audit_log.id"),
                List.of("lineage:direct"),
                List.of(),
                List.of(),
                List.of("lineage:direct"),
                BigDecimal.valueOf(0.9),
                Map.of("directLineageCount", 1));
    }

    private ObjectNode relationship(String id, String source, String target) {
        ObjectNode value = JSON.createObjectNode();
        value.put("id", id);
        value.put("source", source);
        value.put("target", target);
        return value;
    }

    private ObjectNode lineage(String id, String source, String target) {
        ObjectNode value = JSON.createObjectNode();
        value.put("id", id);
        value.putArray("sources").add(source);
        value.put("target", target);
        return value;
    }
}
