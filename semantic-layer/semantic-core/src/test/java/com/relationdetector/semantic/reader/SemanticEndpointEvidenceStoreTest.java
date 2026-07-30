package com.relationdetector.semantic.reader;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class SemanticEndpointEvidenceStoreTest {
    @TempDir
    java.nio.file.Path tempDir;

    @Test
    void externallyGroupsAndDeduplicatesEndpointEvidenceOnce() {
        try (SemanticEndpointEvidenceStore store =
                     new SemanticEndpointEvidenceStore(tempDir.resolve("endpoint-evidence"))) {
            store.append("shop.orders.id", List.of("evidence:b", "evidence:a"));
            store.append("shop.orders.id", List.of("evidence:a", "evidence:c"));
            store.append("shop.customers.id", List.of("evidence:d"));
            store.finish();

            assertEquals(
                    List.of("evidence:a", "evidence:b", "evidence:c"),
                    store.evidence("shop.orders.id"));
            assertEquals(
                    List.of("evidence:d"),
                    store.evidence("shop.customers.id"));
            assertEquals(List.of(), store.evidence("shop.missing.id"));
        }
    }
}
