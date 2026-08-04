package com.relationdetector.semantic.extraction.artifact;

import com.relationdetector.semantic.extraction.normalization.SemanticExtractionValidationException;

import com.relationdetector.semantic.extraction.normalization.SemanticEvidenceLookup;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.relationdetector.semantic.internal.io.SemanticFileTreeOperations;
import com.relationdetector.semantic.internal.store.ExternalJsonRecordStore;
import com.relationdetector.semantic.evidence.SemanticEvidenceStore;
import com.relationdetector.semantic.model.PhysicalEndpointRef;

/**
 * CN: 从已校验重建的完整 evidence bundle 建立磁盘后备只读引用索引；输入逐记录流式读取，输出供
 * Codex-session 结果闭包校验，关闭时删除索引工作目录。本类不重建候选或改变证据内容。
 * EN: Builds a disk-backed read-only reference index from a verified reconstructed evidence bundle. It streams one
 * record at a time for Codex-session closure checks and removes its workspace on close without changing evidence.
 */
public final class SemanticReconstructedEvidenceLookup implements SemanticEvidenceLookup, AutoCloseable {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final Path workspace;
    private final ExternalJsonRecordStore references;
    private final ExternalJsonRecordStore sectionReferences;
    private final ExternalJsonRecordStore evidence;
    private final ExternalJsonRecordStore physicalTables;
    private final ExternalJsonRecordStore physicalEndpoints;
    private boolean closed;

    public SemanticReconstructedEvidenceLookup(Path bundle, Path workspace) {
        if (bundle == null || workspace == null) {
            throw new IllegalArgumentException("semantic reconstructed bundle and lookup workspace are required");
        }
        this.workspace = workspace;
        this.references = new ExternalJsonRecordStore(workspace.resolve("references"));
        this.sectionReferences = new ExternalJsonRecordStore(
                workspace.resolve("section-references"));
        this.evidence = new ExternalJsonRecordStore(workspace.resolve("evidence"));
        this.physicalTables = new ExternalJsonRecordStore(
                workspace.resolve("physical-tables"));
        this.physicalEndpoints = new ExternalJsonRecordStore(
                workspace.resolve("physical-endpoints"));
        try {
            index(bundle);
            references.finish();
            sectionReferences.finish();
            evidence.finish();
            physicalTables.finish();
            physicalEndpoints.finish();
        } catch (RuntimeException failure) {
            closeAfterFailure(failure);
            throw failure;
        }
    }

    @Override
    public boolean containsReference(String reference) {
        ensureOpen();
        return reference != null && !reference.isBlank() && references.containsKey(reference);
    }

    @Override
    public boolean containsReference(
            SemanticEvidenceStore.Section section,
            String reference
    ) {
        ensureOpen();
        return section != null
                && reference != null
                && !reference.isBlank()
                && sectionReferences.containsKey(membershipKey(section, reference));
    }

    @Override
    public Optional<JsonNode> findEvidence(String reference) {
        ensureOpen();
        if (reference == null || reference.isBlank()) {
            return Optional.empty();
        }
        return evidence.get(reference).map(ExternalJsonRecordStore.Record::value);
    }

    @Override
    public boolean containsPhysicalTable(String table) {
        ensureOpen();
        String identity = normalized(table);
        return !identity.isBlank() && physicalTables.containsKey(identity);
    }

    @Override
    public boolean containsPhysicalColumn(String column) {
        ensureOpen();
        String identity = normalized(column);
        if (identity.isBlank()
                || containsPhysicalTable(identity)
                || !physicalEndpoints.containsKey(identity)) {
            return false;
        }
        try {
            return containsPhysicalTable(PhysicalEndpointRef.column(identity).table());
        } catch (IllegalArgumentException failure) {
            return false;
        }
    }

    private void index(Path bundle) {
        try (JsonParser parser = JSON.getFactory().createParser(bundle.toFile())) {
            require(parser.nextToken() == JsonToken.START_OBJECT);
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                String field = parser.currentName();
                parser.nextToken();
                SemanticEvidenceStore.Section section = section(field);
                if (section == null) {
                    parser.skipChildren();
                    continue;
                }
                require(parser.currentToken() == JsonToken.START_ARRAY);
                while (parser.nextToken() != JsonToken.END_ARRAY) {
                    JsonNode item = JSON.readTree(parser);
                    if (section == SemanticEvidenceStore.Section.TABLES) {
                        String table = item.isTextual() ? normalized(item.asText()) : "";
                        require(!table.isBlank());
                        physicalTables.append(table, item);
                        sectionReferences.append(membershipKey(section, table), item);
                        continue;
                    }
                    String id = item.path("id").asText("");
                    require(!id.isBlank());
                    references.append(id, JSON.getNodeFactory().textNode(id));
                    sectionReferences.append(
                            membershipKey(section, id), JSON.getNodeFactory().textNode(id));
                    if (section == SemanticEvidenceStore.Section.EVIDENCE) {
                        evidence.append(id, item);
                    }
                    indexPhysical(section, item);
                }
            }
            require(parser.nextToken() == null);
        } catch (IOException failure) {
            throw invalidBundle();
        }
    }

    private void indexPhysical(SemanticEvidenceStore.Section section, JsonNode item) {
        switch (section) {
            case METADATA_TABLES -> appendPhysicalTable(item.path("table"));
            case METADATA_COLUMNS -> appendPhysicalEndpoint(item.path("column"));
            case RELATIONSHIPS, DERIVED_RELATIONSHIPS, NAMING_EVIDENCE -> {
                appendPhysicalEndpoint(item.path("source"));
                appendPhysicalEndpoint(item.path("target"));
            }
            case LINEAGE, DERIVED_LINEAGE -> {
                appendPhysicalEndpoint(item.path("sources"));
                appendPhysicalEndpoint(item.path("source"));
                appendPhysicalEndpoint(item.path("target"));
            }
            case EVENT_CANDIDATES -> {
                appendPhysicalEndpoint(item.path("inputEndpoints"));
                appendPhysicalEndpoint(item.path("outputEndpoints"));
            }
            default -> {
                // This section carries references but no typed physical endpoints.
            }
        }
    }

    private void appendPhysicalTable(JsonNode value) {
        if (value.isTextual() && !value.asText().isBlank()) {
            String identity = normalized(value.asText());
            physicalTables.append(identity, JSON.getNodeFactory().textNode(identity));
        }
    }

    private void appendPhysicalEndpoint(JsonNode value) {
        if (value.isArray()) {
            value.forEach(this::appendPhysicalEndpoint);
            return;
        }
        if (value.isTextual() && !value.asText().isBlank()) {
            String identity = normalized(value.asText());
            physicalEndpoints.append(identity, JSON.getNodeFactory().textNode(identity));
        }
    }

    private String membershipKey(SemanticEvidenceStore.Section section, String reference) {
        return section.wireName() + "\u0000" + reference;
    }

    private String normalized(String value) {
        return value == null ? "" : value.strip();
    }

    private SemanticEvidenceStore.Section section(String wireName) {
        for (SemanticEvidenceStore.Section section : SemanticEvidenceStore.Section.values()) {
            if (section.wireName().equals(wireName)) {
                return section;
            }
        }
        return null;
    }

    private void require(boolean condition) {
        if (!condition) {
            throw invalidBundle();
        }
    }

    private SemanticExtractionValidationException invalidBundle() {
        return new SemanticExtractionValidationException(
                "semantic reconstructed evidence bundle is invalid");
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("semantic reconstructed evidence lookup is closed");
        }
    }

    private void closeAfterFailure(RuntimeException failure) {
        try {
            close();
        } catch (RuntimeException cleanup) {
            failure.addSuppressed(cleanup);
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        RuntimeException failure = null;
        for (AutoCloseable store : new AutoCloseable[] {
                references, sectionReferences, evidence, physicalTables, physicalEndpoints}) {
            try {
                store.close();
            } catch (Exception error) {
                failure = new IllegalStateException("failed to close semantic reconstructed evidence lookup", error);
            }
        }
        SemanticFileTreeOperations.deleteRecursivelyBestEffort(workspace);
        if (failure != null) {
            throw failure;
        }
    }
}
