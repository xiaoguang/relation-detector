package com.relationdetector.semantic.ingest;

import com.relationdetector.semantic.internal.store.ExternalLineSorter;

import com.relationdetector.semantic.internal.store.DiskUnionFind;

import com.relationdetector.semantic.internal.store.DiskStringDictionary;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.relationdetector.contracts.metadata.MetadataColumnFact;
import com.relationdetector.contracts.metadata.MetadataConstraintFact;
import com.relationdetector.contracts.metadata.MetadataIndexFact;
import com.relationdetector.contracts.metadata.MetadataTableFact;
import com.relationdetector.contracts.spi.ScanScope;
import com.relationdetector.semantic.internal.io.SemanticFileTreeOperations;
import com.relationdetector.semantic.model.PhysicalEndpointRef;

/**
 * CN: 按typed table component稳定排列SemanticInputStore记录，并以原始字节上限逐个materialize输入运输窗口；
 * 字节阈值只限制单次I/O内存，不定义event、owner、shard或KG语义边界。
 * EN: Orders SemanticInputStore records stably by typed table component and materializes input transport windows
 * under a raw-byte ceiling. The byte limit controls only per-window I/O memory and never defines event, owner,
 * shard, or KG semantics.
 */
public final class SemanticInputWindowStore implements AutoCloseable {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final SemanticInputStore input;
    private final Path workspace;
    private final DiskStringDictionary dictionary;
    private final DiskUnionFind components;
    private final Path assignments;
    private boolean closed;

    public SemanticInputWindowStore(SemanticInputStore input, Path workspace) {
        if (input == null || workspace == null) {
            throw new IllegalArgumentException("semantic input store and window workspace are required");
        }
        this.input = input;
        this.workspace = workspace;
        try {
            if (Files.exists(workspace)) {
                throw new ScanResultContractException("semantic input window workspace already exists");
            }
            Files.createDirectories(workspace);
            Path rawTables = workspace.resolve("tables.raw");
            Path rawEdges = workspace.resolve("edges.raw");
            try (BufferedWriter tableWriter = writer(rawTables);
                 BufferedWriter edgeWriter = writer(rawEdges)) {
                collectGraph(tableWriter, edgeWriter);
            }
            this.dictionary = DiskStringDictionary.build(
                    rawTables, workspace.resolve("tables.dictionary"), workspace.resolve("dictionary-work"));
            this.components = new DiskUnionFind(workspace.resolve("parents.bin"), dictionary.size());
            unionEdges(rawEdges);
            Path rawAssignments = workspace.resolve("assignments.raw");
            try (BufferedWriter writer = writer(rawAssignments)) {
                collectAssignments(writer);
            }
            this.assignments = workspace.resolve("assignments.sorted");
            new ExternalLineSorter().sort(
                    rawAssignments, assignments, workspace.resolve("assignment-sort-work"));
        } catch (IOException failure) {
            throw new ScanResultContractException("failed to build semantic input window store", failure);
        }
    }

    public void forEachWindow(long maxRawBytes, Consumer<InputWindow> consumer) {
        ensureOpen();
        if (maxRawBytes <= 0 || consumer == null) {
            throw new IllegalArgumentException("input window byte limit and consumer are required");
        }
        try (BufferedReader reader = Files.newBufferedReader(assignments, StandardCharsets.UTF_8)) {
            String currentRoot = null;
            int window = 0;
            long bytes = 0;
            Path windowPath = null;
            BufferedWriter windowWriter = null;
            String line;
            while ((line = reader.readLine()) != null) {
                String root = line.substring(0, line.indexOf('\t'));
                long lineBytes = line.getBytes(StandardCharsets.UTF_8).length + 1L;
                if (!root.equals(currentRoot)
                        || windowWriter != null && bytes > 0 && bytes + lineBytes > maxRawBytes) {
                    if (windowWriter != null) {
                        windowWriter.close();
                        consume(currentRoot, window++, windowPath, bytes, consumer);
                    }
                    if (!root.equals(currentRoot)) {
                        currentRoot = root;
                        window = 0;
                    }
                    windowPath = workspace.resolve("input-" + root + "-%06d.window".formatted(window));
                    windowWriter = writer(windowPath);
                    bytes = 0;
                }
                windowWriter.write(line.substring(line.indexOf('\t') + 1));
                windowWriter.newLine();
                bytes += lineBytes;
            }
            if (windowWriter != null) {
                windowWriter.close();
                consume(currentRoot, window, windowPath, bytes, consumer);
            }
        } catch (IOException failure) {
            throw new ScanResultContractException("failed to iterate semantic input windows", failure);
        }
    }

    private void consume(
            String root,
            int window,
            Path path,
            long bytes,
            Consumer<InputWindow> consumer
    ) throws IOException {
        try {
            consumer.accept(new InputWindow(root + "-" + "%06d".formatted(window), bytes, materialize(path)));
        } finally {
            Files.deleteIfExists(path);
        }
    }

    private ScanBundle materialize(Path path) throws IOException {
        List<MetadataTableFact> tables = new ArrayList<>();
        List<MetadataColumnFact> columns = new ArrayList<>();
        List<MetadataConstraintFact> constraints = new ArrayList<>();
        List<MetadataIndexFact> indexes = new ArrayList<>();
        List<JsonNode> relationships = new ArrayList<>();
        List<JsonNode> lineages = new ArrayList<>();
        List<JsonNode> derivedRelationships = new ArrayList<>();
        List<JsonNode> derivedLineages = new ArrayList<>();
        List<JsonNode> naming = new ArrayList<>();
        List<JsonNode> warnings = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                int split = line.indexOf('\t');
                SemanticInputStore.Section section = SemanticInputStore.Section.valueOf(line.substring(0, split));
                JsonNode value = JSON.readTree(decode(line.substring(split + 1)));
                switch (section) {
                    case METADATA_TABLES -> tables.add(JSON.treeToValue(value, MetadataTableFact.class));
                    case METADATA_COLUMNS -> columns.add(JSON.treeToValue(value, MetadataColumnFact.class));
                    case METADATA_CONSTRAINTS -> constraints.add(JSON.treeToValue(value, MetadataConstraintFact.class));
                    case METADATA_INDEXES -> indexes.add(JSON.treeToValue(value, MetadataIndexFact.class));
                    case RELATIONSHIPS -> relationships.add(value);
                    case DATA_LINEAGES -> lineages.add(value);
                    case DERIVED_RELATIONSHIPS -> derivedRelationships.add(value);
                    case DERIVED_DATA_LINEAGES -> derivedLineages.add(value);
                    case NAMING_EVIDENCE -> naming.add(value);
                    case WARNINGS -> warnings.add(value);
                }
            }
        }
        ScanScope scope = input.descriptor().inventory().scope();
        ScanMetadataInventory inventory = ScanMetadataInventory.complete(
                input.descriptor().inventory().basis(),
                new ScanScope(scope.catalog(), scope.schema(), List.of(), List.of()),
                tables, columns, constraints, indexes);
        return new ScanBundle(
                input.descriptor().databaseType(),
                input.descriptor().catalog(),
                input.descriptor().schema(),
                input.descriptor().generatedAt(),
                input.descriptor().sources(),
                input.descriptor().inputFiles().stream().map(Path::of).toList(),
                Map.of(),
                inventory,
                relationships,
                lineages,
                derivedRelationships,
                derivedLineages,
                naming,
                warnings);
    }

    private void collectGraph(BufferedWriter tables, BufferedWriter edges) {
        input.forEachTable(table -> appendTable(tables, tableName(
                table.catalog(), table.schema(), table.tableName())));
        input.forEachColumn(column -> appendTable(tables, tableName(
                column.catalog(), column.schema(), column.tableName())));
        input.forEachConstraint(constraint -> appendTablesAndEdges(tables, edges, constraintTables(constraint)));
        input.forEachIndex(index -> appendTable(tables, tableName(
                index.catalog(), index.schema(), index.tableName())));
        input.forEachRelationship(false, fact -> appendTablesAndEdges(tables, edges,
                List.of(fact.source().table(), fact.target().table())));
        input.forEachRelationship(true, fact -> appendTablesAndEdges(tables, edges, pathTables(fact.document())));
        input.forEachLineage(false, fact -> appendTablesAndEdges(tables, edges, lineageTables(fact)));
        input.forEachLineage(true, fact -> appendTablesAndEdges(tables, edges, pathTables(fact.document())));
        input.forEachNamingEvidence(fact -> appendTablesAndEdges(tables, edges,
                List.of(fact.source().table(), fact.target().table())));
    }

    private void collectAssignments(BufferedWriter writer) {
        input.forEachTable(value -> appendAssignment(writer, SemanticInputStore.Section.METADATA_TABLES,
                JSON.valueToTree(value), List.of(tableName(value.catalog(), value.schema(), value.tableName()))));
        input.forEachColumn(value -> appendAssignment(writer, SemanticInputStore.Section.METADATA_COLUMNS,
                JSON.valueToTree(value), List.of(tableName(value.catalog(), value.schema(), value.tableName()))));
        input.forEachConstraint(value -> appendAssignment(writer, SemanticInputStore.Section.METADATA_CONSTRAINTS,
                JSON.valueToTree(value), constraintTables(value)));
        input.forEachIndex(value -> appendAssignment(writer, SemanticInputStore.Section.METADATA_INDEXES,
                JSON.valueToTree(value), List.of(tableName(value.catalog(), value.schema(), value.tableName()))));
        input.forEachRelationship(false, value -> appendAssignment(writer, SemanticInputStore.Section.RELATIONSHIPS,
                value.document(), List.of(value.source().table(), value.target().table())));
        input.forEachRelationship(true, value -> appendAssignment(
                writer, SemanticInputStore.Section.DERIVED_RELATIONSHIPS,
                value.document(), pathTables(value.document())));
        input.forEachLineage(false, value -> appendAssignment(writer, SemanticInputStore.Section.DATA_LINEAGES,
                value.document(), lineageTables(value)));
        input.forEachLineage(true, value -> appendAssignment(
                writer, SemanticInputStore.Section.DERIVED_DATA_LINEAGES,
                value.document(), pathTables(value.document())));
        input.forEachNamingEvidence(value -> appendAssignment(writer, SemanticInputStore.Section.NAMING_EVIDENCE,
                value.document(), List.of(value.source().table(), value.target().table())));
        input.forEachDiagnostic(value -> appendAssignment(
                writer, SemanticInputStore.Section.WARNINGS, value.document(), List.of()));
    }

    private void unionEdges(Path edges) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(edges, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                int split = line.indexOf('\t');
                int left = dictionary.id(line.substring(0, split)).orElseThrow(
                        () -> new ScanResultContractException(
                                "semantic table edge references an unknown table"));
                int right = dictionary.id(line.substring(split + 1)).orElseThrow(
                        () -> new ScanResultContractException(
                                "semantic table edge references an unknown table"));
                components.union(left, right);
            }
        }
    }

    private void appendAssignment(
            BufferedWriter writer,
            SemanticInputStore.Section section,
            JsonNode document,
            List<String> touchedTables
    ) {
        try {
            String componentKey = "global";
            if (!touchedTables.isEmpty() && dictionary.size() > 0) {
                int root = root(touchedTables.get(0));
                componentKey = rootKey(root);
                for (String table : touchedTables) {
                    if (root(table) != root) {
                        throw new ScanResultContractException(
                                "typed fact crosses disconnected table components");
                    }
                }
            }
            writer.write(componentKey);
            writer.write('\t');
            writer.write(section.name());
            writer.write('\t');
            writer.write(encode(JSON.writeValueAsString(document)));
            writer.newLine();
        } catch (IOException failure) {
            throw new ScanResultContractException("failed to spool semantic input assignment", failure);
        }
    }

    private int root(String table) throws IOException {
        int id = dictionary.id(encode(table)).orElseThrow(
                () -> new ScanResultContractException(
                        "semantic fact references an unknown physical table"));
        return components.find(id);
    }

    private void appendTablesAndEdges(
            BufferedWriter tableWriter,
            BufferedWriter edgeWriter,
            List<String> values
    ) {
        List<String> tables = values.stream().filter(value -> value != null && !value.isBlank())
                .distinct().sorted().toList();
        for (String table : tables) {
            appendTable(tableWriter, table);
        }
        try {
            for (int index = 1; index < tables.size(); index++) {
                edgeWriter.write(encode(tables.get(0)));
                edgeWriter.write('\t');
                edgeWriter.write(encode(tables.get(index)));
                edgeWriter.newLine();
            }
        } catch (IOException failure) {
            throw new ScanResultContractException("failed to spool semantic table edge", failure);
        }
    }

    private void appendTable(BufferedWriter writer, String table) {
        if (table == null || table.isBlank()) {
            return;
        }
        try {
            writer.write(encode(table));
            writer.newLine();
        } catch (IOException failure) {
            throw new ScanResultContractException("failed to spool semantic table identity", failure);
        }
    }

    private List<String> lineageTables(ScanLineageFact fact) {
        Set<String> result = new LinkedHashSet<>();
        fact.sources().stream().map(PhysicalEndpointRef::table).forEach(result::add);
        result.add(fact.target().table());
        return List.copyOf(result);
    }

    private List<String> pathTables(JsonNode document) {
        Set<String> result = new LinkedHashSet<>();
        document.path("path").forEach(endpoint -> {
            String table = endpoint.path("table").asText("");
            if (!table.isBlank()) {
                result.add(table);
            }
        });
        if (result.isEmpty()) {
            String source = document.path("source").path("table").asText("");
            String target = document.path("target").path("table").asText("");
            if (!source.isBlank()) result.add(source);
            if (!target.isBlank()) result.add(target);
        }
        return List.copyOf(result);
    }

    private List<String> constraintTables(MetadataConstraintFact value) {
        List<String> result = new ArrayList<>();
        result.add(tableName(value.catalog(), value.schema(), value.tableName()));
        if (value.referencedTable() != null && !value.referencedTable().isBlank()) {
            result.add(tableName(
                    value.referencedCatalog(), value.referencedSchema(), value.referencedTable()));
        }
        return result;
    }

    private String tableName(String catalog, String schema, String table) {
        List<String> parts = new ArrayList<>();
        if (catalog != null && !catalog.isBlank()) parts.add(catalog);
        if (schema != null && !schema.isBlank()) parts.add(schema);
        parts.add(table);
        return String.join(".", parts);
    }

    private String rootKey(int root) {
        return "%010d".formatted(root);
    }

    private String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String decode(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private BufferedWriter writer(Path path) throws IOException {
        return Files.newBufferedWriter(
                path, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("semantic input window store is closed");
        }
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        RuntimeException failure = null;
        try {
            dictionary.close();
            components.close();
        } catch (IOException error) {
            failure = new IllegalStateException("failed to close semantic input window indexes", error);
        }
        try {
            SemanticFileTreeOperations.deleteRecursively(workspace);
        } catch (IOException error) {
            if (failure == null) {
                failure = new IllegalStateException("failed to clean semantic input window store", error);
            }
            else failure.addSuppressed(error);
        }
        if (failure != null) throw failure;
    }

    public record InputWindow(String id, long rawBytes, ScanBundle bundle) {
        public InputWindow {
            if (id == null || id.isBlank() || rawBytes <= 0 || bundle == null) {
                throw new IllegalArgumentException("semantic input window is incomplete");
            }
        }
    }
}
