package com.relationdetector.semantic.reader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relationdetector.contracts.Enums.MetadataInventoryStatus;

final class SemanticInputStoreTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void streamsCompleteInventoryIntoTypedSectionSpoolsAndCleansWorkspace() throws Exception {
        Path input = writeScan("scan.json", "shop", "COMPLETE", "TABLE");
        Path workspace = tempDir.resolve(".semantic-input");
        List<String> tables = new ArrayList<>();

        try (SemanticInputStore store = new ScanResultReader().open(List.of(input), workspace)) {
            assertEquals(MetadataInventoryStatus.COMPLETE, store.descriptor().inventory().status());
            assertEquals(1, store.count(SemanticInputStore.Section.METADATA_TABLES));
            assertEquals(1, store.count(SemanticInputStore.Section.METADATA_COLUMNS));
            assertTrue(store.containsInventoryTable("shop", null, "orders"));
            assertTrue(store.containsInventoryColumn("shop", null, "orders", "id"));
            store.forEach(SemanticInputStore.Section.METADATA_TABLES,
                    item -> tables.add(item.path("tableName").asText()));
            assertEquals(List.of("orders"), tables);
            assertTrue(Files.isDirectory(workspace));
        }

        assertFalse(Files.exists(workspace));
    }

    @Test
    void rejectsNonCompleteInventoryBeforeReturningStore() throws Exception {
        Path input = writeScan("partial.json", "shop", "PARTIAL", "TABLE");
        Path workspace = tempDir.resolve(".semantic-partial");

        assertThrows(ScanResultContractException.class,
                () -> new ScanResultReader().open(List.of(input), workspace));
        assertFalse(Files.exists(workspace));
    }

    @Test
    void rejectsConflictingInventoryAcrossInputsAndCleansWorkspace() throws Exception {
        Path first = writeScan("first.json", "shop", "COMPLETE", "TABLE");
        Path second = writeScan("second.json", "shop", "COMPLETE", "VIEW");
        Path workspace = tempDir.resolve(".semantic-merged");

        assertThrows(ScanResultContractException.class,
                () -> new ScanResultReader().open(List.of(first, second), workspace));
        assertFalse(Files.exists(workspace));
    }

    private Path writeScan(
            String name,
            String catalog,
            String status,
            String tableType
    ) throws Exception {
        ObjectNode root = JSON.createObjectNode();
        root.putObject("database").put("type", "mysql").put("catalog", catalog).put("schema", "");
        root.put("generatedAt", "2026-07-28T00:00:00Z");
        ObjectNode summary = root.putObject("summary");
        summary.put("directRelationshipCount", 0);
        summary.put("derivedRelationshipCount", 0);
        summary.put("totalRelationshipCount", 0);
        summary.put("directDataLineageCount", 0);
        summary.put("derivedDataLineageCount", 0);
        summary.put("totalDataLineageCount", 0);
        summary.put("directNamingEvidenceCount", 0);
        summary.put("derivedNamingEvidenceCount", 0);
        summary.put("totalNamingEvidenceCount", 0);
        summary.put("warningCount", 0);
        summary.putArray("sources").add("metadata");
        ObjectNode inventory = root.putObject("metadataInventory");
        inventory.put("status", status);
        ObjectNode scope = inventory.putObject("scope");
        scope.put("catalog", catalog);
        scope.putNull("schema");
        scope.putArray("includeTables");
        scope.putArray("excludeTables");
        inventory.putObject("counts")
                .put("tables", 1)
                .put("columns", 1)
                .put("constraints", 0)
                .put("indexes", 0);
        inventory.putArray("tables").addObject()
                .put("catalog", catalog)
                .putNull("schema")
                .put("tableName", "orders")
                .put("tableType", tableType);
        inventory.putArray("columns").addObject()
                .put("catalog", catalog)
                .putNull("schema")
                .put("tableName", "orders")
                .put("columnName", "id")
                .put("dataType", "bigint")
                .put("columnType", "bigint")
                .put("nullable", false)
                .put("ordinalPosition", 1);
        inventory.putArray("constraints");
        inventory.putArray("indexes");
        root.putArray("relationships");
        root.putArray("dataLineages");
        root.putArray("derivedRelationships");
        root.putArray("derivedDataLineages");
        root.putArray("namingEvidence");
        root.putArray("derivedNamingEvidence");
        root.putArray("warnings");
        Path path = tempDir.resolve(name);
        JSON.writeValue(path.toFile(), root);
        return path;
    }
}
