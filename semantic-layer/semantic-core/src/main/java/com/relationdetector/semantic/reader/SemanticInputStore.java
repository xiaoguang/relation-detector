package com.relationdetector.semantic.reader;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.relationdetector.contracts.Enums.MetadataInventoryStatus;
import com.relationdetector.contracts.metadata.MetadataColumnFact;
import com.relationdetector.contracts.metadata.MetadataConstraintFact;
import com.relationdetector.contracts.metadata.MetadataIndexFact;
import com.relationdetector.contracts.metadata.MetadataTableFact;
import com.relationdetector.contracts.spi.ScanScope;

/**
 * CN: 提供磁盘后备semantic输入的稳定读取facade和生命周期边界；输入加载、metadata闭包与外排索引构建
 * 分别由同package组件完成。调用方可按section或typed fact顺序读取，关闭store会释放索引并删除工作目录。
 * 本类不解析scan JSON、不推断metadata身份，也不持有完整事实集合。
 *
 * EN: Provides the stable read facade and lifecycle boundary for disk-backed semantic input. Package collaborators
 * load scan JSON and build metadata-closure indexes. Callers read sections or typed facts in order; closing the
 * store releases indexes and removes its workspace. This class neither parses scan JSON nor infers metadata identity
 * nor retains the complete fact set.
 */
public final class SemanticInputStore implements AutoCloseable {
    public enum Section {
        METADATA_TABLES("metadataTables"),
        METADATA_COLUMNS("metadataColumns"),
        METADATA_CONSTRAINTS("metadataConstraints"),
        METADATA_INDEXES("metadataIndexes"),
        RELATIONSHIPS("relationships"),
        DATA_LINEAGES("dataLineages"),
        DERIVED_RELATIONSHIPS("derivedRelationships"),
        DERIVED_DATA_LINEAGES("derivedDataLineages"),
        NAMING_EVIDENCE("namingEvidence"),
        WARNINGS("warnings");

        private final String wireName;

        Section(String wireName) {
            this.wireName = wireName;
        }

        public String wireName() {
            return wireName;
        }
    }

    private static final ObjectMapper JSON = new ObjectMapper();
    private final Path workspace;
    private final Descriptor descriptor;
    private final Map<Section, Path> sectionPaths;
    private final Map<Section, Long> sectionCounts;
    private final SortedTextIndex tableIndex;
    private final SortedTextIndex columnIndex;
    private final SortedTextIndex factIndex;
    private boolean closed;

    SemanticInputStore(
            Path workspace,
            Descriptor descriptor,
            Map<Section, Path> sectionPaths,
            Map<Section, Long> sectionCounts,
            SortedTextIndex tableIndex,
            SortedTextIndex columnIndex,
            SortedTextIndex factIndex
    ) {
        this.workspace = workspace;
        this.descriptor = descriptor;
        this.sectionPaths = Map.copyOf(sectionPaths);
        this.sectionCounts = Map.copyOf(sectionCounts);
        this.tableIndex = tableIndex;
        this.columnIndex = columnIndex;
        this.factIndex = factIndex;
    }

    static SemanticInputStore open(List<Path> inputs, Path workspace) {
        if (inputs == null || inputs.isEmpty()) {
            throw new IllegalArgumentException("at least one scan result file is required");
        }
        if (workspace == null) {
            throw new IllegalArgumentException("semantic input workspace is required");
        }
        return new SemanticInputStoreLoader(workspace).load(inputs);
    }

    public Descriptor descriptor() {
        return descriptor;
    }

    public long count(Section section) {
        return sectionCounts.getOrDefault(section, 0L);
    }

    public boolean containsInventoryTable(String catalog, String schema, String table) {
        return contains(tableIndex, encodeKey(
                MetadataInventoryClosureRules.tableIdentity(catalog, schema, table)));
    }

    public boolean containsInventoryColumn(String catalog, String schema, String table, String column) {
        return contains(columnIndex, encodeKey(
                MetadataInventoryClosureRules.columnIdentity(catalog, schema, table, column)));
    }

    public boolean containsFact(String factId) {
        return contains(factIndex, encodeKey(factId));
    }

    public void forEach(Section section, Consumer<JsonNode> consumer) {
        ensureOpen();
        if (section == null || consumer == null) {
            throw new IllegalArgumentException("semantic input section and consumer are required");
        }
        try (BufferedReader reader = Files.newBufferedReader(sectionPaths.get(section), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                consumer.accept(JSON.readTree(line));
            }
        } catch (IOException failure) {
            throw new ScanResultContractException("failed to read semantic input spool", failure);
        }
    }

    public void forEachTable(Consumer<MetadataTableFact> consumer) {
        forEachTyped(Section.METADATA_TABLES, MetadataTableFact.class, consumer);
    }

    public void forEachColumn(Consumer<MetadataColumnFact> consumer) {
        forEachTyped(Section.METADATA_COLUMNS, MetadataColumnFact.class, consumer);
    }

    public void forEachConstraint(Consumer<MetadataConstraintFact> consumer) {
        forEachTyped(Section.METADATA_CONSTRAINTS, MetadataConstraintFact.class, consumer);
    }

    public void forEachIndex(Consumer<MetadataIndexFact> consumer) {
        forEachTyped(Section.METADATA_INDEXES, MetadataIndexFact.class, consumer);
    }

    public void forEachRelationship(boolean derived, Consumer<ScanRelationshipFact> consumer) {
        Section section = derived ? Section.DERIVED_RELATIONSHIPS : Section.RELATIONSHIPS;
        forEach(section, item -> consumer.accept(ScanFactFactory.relationships(List.of(item), derived).get(0)));
    }

    public void forEachLineage(boolean derived, Consumer<ScanLineageFact> consumer) {
        Section section = derived ? Section.DERIVED_DATA_LINEAGES : Section.DATA_LINEAGES;
        forEach(section, item -> consumer.accept(ScanFactFactory.lineages(List.of(item), derived).get(0)));
    }

    public void forEachNamingEvidence(Consumer<ScanNamingEvidenceFact> consumer) {
        forEach(Section.NAMING_EVIDENCE,
                item -> consumer.accept(ScanFactFactory.naming(List.of(item)).get(0)));
    }

    public void forEachDiagnostic(Consumer<ScanDiagnosticFact> consumer) {
        forEach(Section.WARNINGS,
                item -> consumer.accept(ScanFactFactory.diagnostics(List.of(item)).get(0)));
    }

    Path sectionPath(Section section) {
        ensureOpen();
        return sectionPaths.get(section);
    }

    Path workspace() {
        ensureOpen();
        return workspace;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        RuntimeException failure = null;
        for (AutoCloseable value : List.of(tableIndex, columnIndex, factIndex)) {
            try {
                value.close();
            } catch (Exception error) {
                failure = new IllegalStateException("failed to close semantic disk index", error);
            }
        }
        try {
            deleteRecursively(workspace);
        } catch (IOException error) {
            if (failure == null) {
                failure = new IllegalStateException("failed to clean semantic input workspace", error);
            } else {
                failure.addSuppressed(error);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    static String encodeKey(String value) {
        return java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private boolean contains(SortedTextIndex index, String key) {
        ensureOpen();
        try {
            return index.contains(key);
        } catch (IOException failure) {
            throw new ScanResultContractException("failed to query semantic disk index", failure);
        }
    }

    private <T> void forEachTyped(Section section, Class<T> type, Consumer<T> consumer) {
        forEach(section, item -> {
            try {
                consumer.accept(JSON.treeToValue(item, type));
            } catch (IOException failure) {
                throw new ScanResultContractException("failed to decode typed semantic input fact", failure);
            }
        });
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("semantic input store is closed");
        }
    }

    public record InventoryDescriptor(
            MetadataInventoryStatus status,
            ScanScope scope,
            long tableCount,
            long columnCount,
            long constraintCount,
            long indexCount,
            String fingerprint
    ) {
        public InventoryDescriptor {
            if (status != MetadataInventoryStatus.COMPLETE || scope == null
                    || fingerprint == null || fingerprint.isBlank()) {
                throw new ScanResultContractException("semantic inventory descriptor is incomplete");
            }
        }
    }

    public record Descriptor(
            String databaseType,
            String catalog,
            String schema,
            String generatedAt,
            List<String> sources,
            List<String> inputFiles,
            Map<String, Integer> summary,
            InventoryDescriptor inventory
    ) {
        public Descriptor {
            sources = List.copyOf(sources);
            inputFiles = List.copyOf(inputFiles);
            summary = Map.copyOf(summary);
            if (inventory == null) {
                throw new ScanResultContractException("semantic input inventory is required");
            }
        }
    }
}
