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
    private final ExternalJsonRecordStore evidence;
    private boolean closed;

    public SemanticReconstructedEvidenceLookup(Path bundle, Path workspace) {
        if (bundle == null || workspace == null) {
            throw new IllegalArgumentException("semantic reconstructed bundle and lookup workspace are required");
        }
        this.workspace = workspace;
        this.references = new ExternalJsonRecordStore(workspace.resolve("references"));
        this.evidence = new ExternalJsonRecordStore(workspace.resolve("evidence"));
        try {
            index(bundle);
            references.finish();
            evidence.finish();
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
    public Optional<JsonNode> findEvidence(String reference) {
        ensureOpen();
        if (reference == null || reference.isBlank()) {
            return Optional.empty();
        }
        return evidence.get(reference).map(ExternalJsonRecordStore.Record::value);
    }

    private void index(Path bundle) {
        try (JsonParser parser = JSON.getFactory().createParser(bundle.toFile())) {
            require(parser.nextToken() == JsonToken.START_OBJECT);
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                String field = parser.currentName();
                parser.nextToken();
                SemanticEvidenceStore.Section section = section(field);
                if (section == null || section == SemanticEvidenceStore.Section.TABLES) {
                    parser.skipChildren();
                    continue;
                }
                require(parser.currentToken() == JsonToken.START_ARRAY);
                while (parser.nextToken() != JsonToken.END_ARRAY) {
                    JsonNode item = JSON.readTree(parser);
                    String id = item.path("id").asText("");
                    require(!id.isBlank());
                    references.append(id, JSON.getNodeFactory().textNode(id));
                    if (section == SemanticEvidenceStore.Section.EVIDENCE) {
                        evidence.append(id, item);
                    }
                }
            }
            require(parser.nextToken() == null);
        } catch (IOException failure) {
            throw invalidBundle();
        }
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
        for (AutoCloseable store : new AutoCloseable[] {references, evidence}) {
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
