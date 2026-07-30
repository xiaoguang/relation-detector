package com.relationdetector.semantic.reader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;

final class ExternalJsonRecordStoreTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void largePayloadLookupUsesACompactExternalOffsetIndex() throws Exception {
        Path workspace = tempDir.resolve("records");
        String payload = "x".repeat(128 * 1024);
        try (ExternalJsonRecordStore store = new ExternalJsonRecordStore(workspace)) {
            for (int index = 0; index < 32; index++) {
                store.append("record-%03d".formatted(index),
                        JSON.createObjectNode().put("ordinal", index).put("payload", payload));
            }
            store.finish();

            Path records = workspace.resolve("records.unique");
            Path offsets = workspace.resolve("records.offsets");
            assertTrue(Files.size(records) > 4L * 1024L * 1024L);
            assertTrue(Files.isRegularFile(offsets));
            assertTrue(Files.size(offsets) < 16L * 1024L);
            assertFalse(Files.exists(workspace.resolve("records.keys")));

            assertEquals(0, store.get("record-000").orElseThrow().value().path("ordinal").asInt());
            assertEquals(17, store.get("record-017").orElseThrow().value().path("ordinal").asInt());
            assertEquals(31, store.get("record-031").orElseThrow().value().path("ordinal").asInt());
            assertFalse(store.get("record-missing").isPresent());
            assertTrue(store.containsKey("record-017"));
            assertFalse(store.containsKey("record-missing"));
        }
    }

    @Test
    void canonicalConflictHashIgnoresObjectFieldOrderButPreservesArrayOrder() {
        try (ExternalJsonRecordStore store = new ExternalJsonRecordStore(tempDir.resolve("canonical"))) {
            var first = JSON.createObjectNode();
            first.put("first", 1);
            first.putArray("values").add("a").add("b");
            var reordered = JSON.createObjectNode();
            reordered.putArray("values").add("a").add("b");
            reordered.put("first", 1);
            store.append("same", first);
            store.append("same", reordered);

            store.finish();
            assertEquals(1, store.count());
        }

        try (ExternalJsonRecordStore store = new ExternalJsonRecordStore(tempDir.resolve("conflict"))) {
            var first = JSON.createObjectNode();
            first.putArray("values").add("a").add("b");
            var reversed = JSON.createObjectNode();
            reversed.putArray("values").add("b").add("a");
            store.append("same", first);
            store.append("same", reversed);

            assertThrows(ScanResultContractException.class, store::finish);
        }
    }
}
