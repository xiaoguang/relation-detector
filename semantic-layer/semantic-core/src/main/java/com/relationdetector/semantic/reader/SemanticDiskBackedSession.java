package com.relationdetector.semantic.reader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * CN: 为一次production semantic命令拥有input/evidence/component磁盘工作区并统一清理；输入是完整scan文件，
 * 输出是可流式使用的evidence store，本会话不执行业务构建或保留跨命令缓存。
 * EN: Owns and cleans the input, evidence, and component disk workspace for one production semantic command. It
 * exposes a streaming evidence store over complete scan files and provides neither business assembly nor caching.
 */
public final class SemanticDiskBackedSession implements AutoCloseable {
    private final Path workspace;
    private final SemanticInputStore inputStore;
    private final SemanticEvidenceStore evidenceStore;
    private boolean closed;

    private SemanticDiskBackedSession(
            Path workspace,
            SemanticInputStore inputStore,
            SemanticEvidenceStore evidenceStore
    ) {
        this.workspace = workspace;
        this.inputStore = inputStore;
        this.evidenceStore = evidenceStore;
    }

    public static SemanticDiskBackedSession open(List<Path> inputs, Path workspace) {
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
            evidence = new SemanticEvidenceStore(input, workspace.resolve("evidence"));
            return new SemanticDiskBackedSession(workspace, input, evidence);
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

    public static SemanticDiskBackedSession openForOutput(
            List<Path> inputs,
            Path output,
            String purpose
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
        return open(inputs, parent.resolve("." + label + "-work-" + UUID.randomUUID()));
    }

    public SemanticEvidenceStore evidenceStore() {
        ensureOpen();
        return evidenceStore;
    }

    public void writeKgArtifacts(Path outputDirectory) {
        ensureOpen();
        new SemanticDiskBackedArtifactWriter().writeArtifacts(evidenceStore, outputDirectory);
    }

    public void writeEvidenceBundle(Path target) {
        ensureOpen();
        evidenceStore.writeBundle(target);
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
            deleteRecursively(workspace);
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

    private static void deleteRecursively(Path root) throws IOException {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static void deleteRecursivelyBestEffort(Path root) {
        try {
            deleteRecursively(root);
        } catch (IOException ignored) {
            // A construction failure remains primary; the caller receives the original typed error.
        }
    }
}
