package com.relationdetector.semantic.extraction.runtime;

import com.relationdetector.semantic.kg.store.SemanticKgArtifactReport;

import com.relationdetector.semantic.kg.store.SemanticKgArtifactMode;

import com.relationdetector.semantic.kg.store.SemanticKgArtifactWriter;

import com.relationdetector.semantic.evidence.SemanticEvidenceStore;

import com.relationdetector.semantic.ingest.SemanticInputStore;

import com.relationdetector.semantic.ingest.ScanResultReader;

import com.relationdetector.semantic.ingest.ScanResultContractException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import com.relationdetector.semantic.internal.io.SemanticFileTreeOperations;

/**
 * CN: 为一次 production semantic 命令拥有 input、evidence 和全局 owner 规划磁盘工作区并统一清理；输入是完整 scan 文件，
 * 输出是可流式使用的evidence store，本会话不执行业务构建或保留跨命令缓存。
 * EN: Owns and cleans the input, evidence, and global-owner planning workspace for one production semantic command. It
 * exposes a streaming evidence store over complete scan files and provides neither business assembly nor caching.
 */
public final class SemanticProcessingSession implements AutoCloseable {
    private final Path workspace;
    private final SemanticInputStore inputStore;
    private final SemanticEvidenceStore evidenceStore;
    private boolean closed;

    private SemanticProcessingSession(
            Path workspace,
            SemanticInputStore inputStore,
            SemanticEvidenceStore evidenceStore
    ) {
        this.workspace = workspace;
        this.inputStore = inputStore;
        this.evidenceStore = evidenceStore;
    }

    public static SemanticProcessingSession open(
            List<Path> inputs,
            Path workspace,
            int maxInputTokens
    ) {
        if (inputs == null || inputs.isEmpty() || workspace == null) {
            throw new IllegalArgumentException("semantic inputs and workspace are required");
        }
        if (Files.exists(workspace)) {
            throw new ScanResultContractException("semantic command workspace already exists");
        }
        SemanticInputStore input = null;
        SemanticEvidenceStore evidence = null;
        try {
            Files.createDirectories(workspace);
            input = new ScanResultReader().open(inputs, workspace.resolve("input"));
            evidence = new SemanticEvidenceStore(
                    input,
                    workspace.resolve("evidence"),
                    SemanticEvidenceStore.DEFAULT_WINDOW_BYTES,
                    maxInputTokens);
            return new SemanticProcessingSession(workspace, input, evidence);
        } catch (IOException | RuntimeException failure) {
            if (evidence != null) {
                try {
                    evidence.close();
                } catch (RuntimeException cleanup) {
                    failure.addSuppressed(cleanup);
                }
            }
            if (input != null) {
                try {
                    input.close();
                } catch (RuntimeException cleanup) {
                    failure.addSuppressed(cleanup);
                }
            }
            deleteRecursivelyBestEffort(workspace);
            if (failure instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new ScanResultContractException("failed to create semantic disk-backed session", failure);
        }
    }

    public static SemanticProcessingSession openForOutput(
            List<Path> inputs,
            Path output,
            String purpose,
            int maxInputTokens
    ) {
        if (output == null) {
            throw new IllegalArgumentException("semantic output is required");
        }
        Path absolute = output.toAbsolutePath().normalize();
        Path parent = absolute.getParent();
        if (parent == null) {
            parent = Path.of(".").toAbsolutePath().normalize();
        }
        try {
            Files.createDirectories(parent);
        } catch (IOException failure) {
            throw new ScanResultContractException("failed to create semantic output parent", failure);
        }
        String label = purpose == null || purpose.isBlank() ? "semantic" : purpose;
        return open(
                inputs,
                parent.resolve("." + label + "-work-" + UUID.randomUUID()),
                maxInputTokens);
    }

    public SemanticEvidenceStore evidenceStore() {
        ensureOpen();
        return evidenceStore;
    }

    public SemanticKgArtifactReport writeKgArtifacts(
            Path outputDirectory,
            SemanticKgArtifactMode mode
    ) {
        ensureOpen();
        return new SemanticKgArtifactWriter().writeArtifacts(evidenceStore, outputDirectory, mode);
    }

    public Path workPath(String name) {
        ensureOpen();
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("semantic work path name is required");
        }
        return workspace.resolve(name);
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("semantic disk-backed session is closed");
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        RuntimeException failure = null;
        try {
            evidenceStore.close();
        } catch (RuntimeException error) {
            failure = error;
        }
        try {
            inputStore.close();
        } catch (RuntimeException error) {
            if (failure == null) {
                failure = error;
            } else {
                failure.addSuppressed(error);
            }
        }
        try {
            SemanticFileTreeOperations.deleteRecursively(workspace);
        } catch (IOException error) {
            if (failure == null) {
                failure = new IllegalStateException("failed to clean semantic command workspace", error);
            } else {
                failure.addSuppressed(error);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private static void deleteRecursivelyBestEffort(Path root) {
        SemanticFileTreeOperations.deleteRecursivelyBestEffort(root);
    }
}
