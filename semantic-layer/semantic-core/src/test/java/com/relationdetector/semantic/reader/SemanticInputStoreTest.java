package com.relationdetector.semantic.reader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
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
    void keepsStreamingLoaderOutsideThePublicStoreFacade() {
        List<String> nestedTypes = Arrays.stream(SemanticInputStore.class.getDeclaredClasses())
                .map(Class::getSimpleName)
                .toList();

        assertFalse(nestedTypes.contains("Builder"));
    }

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
    void closeIsIdempotentAndOnlyDiskBackedOperationsBecomeUnavailable() throws Exception {
        Path input = writeScan("close.json", "shop", "COMPLETE", "TABLE");
        Path workspace = tempDir.resolve(".semantic-close");
        SemanticInputStore store = new ScanResultReader().open(List.of(input), workspace);

        store.close();
        store.close();

        assertEquals("mysql", store.descriptor().databaseType());
        assertEquals(1, store.count(SemanticInputStore.Section.METADATA_TABLES));
        assertThrows(IllegalStateException.class,
                () -> store.containsInventoryTable("shop", null, "orders"));
        assertThrows(IllegalStateException.class,
                () -> store.forEach(SemanticInputStore.Section.METADATA_TABLES, ignored -> {
                }));
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

    @Test
    void diskBackedReaderRejectsDanglingForeignKeyAndIndexMembers() throws Exception {
        ObjectNode foreignKey = scanRoot("shop", "COMPLETE", "TABLE");
        addSelfForeignKey(foreignKey, "missing_id", "id");
        Path foreignKeyInput = writeRoot("dangling-fk.json", foreignKey);
        Path foreignKeyWorkspace = tempDir.resolve(".semantic-dangling-fk");

        assertThrows(ScanResultContractException.class,
                () -> new ScanResultReader().open(List.of(foreignKeyInput), foreignKeyWorkspace));
        assertFalse(Files.exists(foreignKeyWorkspace));

        ObjectNode index = scanRoot("shop", "COMPLETE", "TABLE");
        addIndex(index, "missing_id", List.of(""), List.of(1));
        Path indexInput = writeRoot("dangling-index.json", index);
        Path indexWorkspace = tempDir.resolve(".semantic-dangling-index");

        assertThrows(ScanResultContractException.class,
                () -> new ScanResultReader().open(List.of(indexInput), indexWorkspace));
        assertFalse(Files.exists(indexWorkspace));
    }

    @Test
    void diskBackedReaderAcceptsClosedForeignKeyAndPrefixIndex() throws Exception {
        ObjectNode root = scanRoot("shop", "COMPLETE", "TABLE");
        addSelfForeignKey(root, "id", "id");
        addIndex(root, "id", List.of("8"), List.of(1));
        Path input = writeRoot("closed-inventory.json", root);

        try (SemanticInputStore store = new ScanResultReader().open(
                List.of(input), tempDir.resolve(".semantic-closed"))) {
            assertEquals(1, store.count(SemanticInputStore.Section.METADATA_CONSTRAINTS));
            assertEquals(1, store.count(SemanticInputStore.Section.METADATA_INDEXES));
        }
    }

    @Test
    void diskBackedReaderRejectsDuplicateAndMalformedIndexIdentity() throws Exception {
        ObjectNode duplicate = scanRoot("shop", "COMPLETE", "TABLE");
        addIndex(duplicate, "id", List.of(""), List.of(1));
        addIndex(duplicate, "id", List.of(""), List.of(1));
        Path duplicateInput = writeRoot("duplicate-index.json", duplicate);

        assertThrows(ScanResultContractException.class,
                () -> new ScanResultReader().open(
                        List.of(duplicateInput), tempDir.resolve(".semantic-duplicate-index")));

        ObjectNode malformed = scanRoot("shop", "COMPLETE", "TABLE");
        addIndex(malformed, "id", List.of("8", ""), List.of(1));
        Path malformedInput = writeRoot("malformed-index.json", malformed);

        assertThrows(ScanResultContractException.class,
                () -> new ScanResultReader().open(
                        List.of(malformedInput), tempDir.resolve(".semantic-malformed-index")));
    }

    private Path writeScan(
            String name,
            String catalog,
            String status,
            String tableType
    ) throws Exception {
        return writeRoot(name, scanRoot(catalog, status, tableType));
    }

    private ObjectNode scanRoot(
            String catalog,
            String status,
            String tableType
    ) {
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
        return root;
    }

    private Path writeRoot(String name, ObjectNode root) throws Exception {
        Path path = tempDir.resolve(name);
        JSON.writeValue(path.toFile(), root);
        return path;
    }

    private void addSelfForeignKey(ObjectNode root, String sourceColumn, String targetColumn) {
        ObjectNode inventory = (ObjectNode) root.path("metadataInventory");
        ObjectNode constraint = inventory.withArray("constraints").addObject();
        constraint.put("catalog", "shop");
        constraint.putNull("schema");
        constraint.put("tableName", "orders");
        constraint.put("constraintName", "fk_orders_parent");
        constraint.put("constraintType", "FOREIGN KEY");
        constraint.putArray("columns").add(sourceColumn);
        constraint.put("referencedCatalog", "shop");
        constraint.putNull("referencedSchema");
        constraint.put("referencedTable", "orders");
        constraint.putArray("referencedColumns").add(targetColumn);
        constraint.put("updateRule", "NO ACTION");
        constraint.put("deleteRule", "NO ACTION");
        ((ObjectNode) inventory.path("counts")).put(
                "constraints", inventory.path("constraints").size());
    }

    private void addIndex(
            ObjectNode root,
            String column,
            List<String> subParts,
            List<Integer> positions
    ) {
        ObjectNode inventory = (ObjectNode) root.path("metadataInventory");
        ObjectNode index = inventory.withArray("indexes").addObject();
        index.put("catalog", "shop");
        index.putNull("schema");
        index.put("tableName", "orders");
        index.put("indexName", "idx_orders_id");
        index.put("unique", false);
        index.put("primary", false);
        index.put("indexType", "BTREE");
        index.put("visible", true);
        index.putArray("columns").add(column);
        index.putArray("expressions");
        var subPartArray = index.putArray("subParts");
        subParts.forEach(subPartArray::add);
        var positionArray = index.putArray("seqInIndex");
        positions.forEach(positionArray::add);
        ((ObjectNode) inventory.path("counts")).put(
                "indexes", inventory.path("indexes").size());
    }
}
