package com.relationdetector.semantic.evidence;

import com.relationdetector.semantic.internal.store.ExternalLineSorter;

import com.relationdetector.semantic.ingest.ScanResultContractException;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * CN: 将KG所需与graph可提供的reference分别外排排序，并以一次归并验证完整引用闭包；上游是磁盘KG
 * 构建器，下游是原子artifact发布，本类不解释reference所属业务section，也不保留全量内存集合。
 * EN: Externally sorts required and available graph references and validates closure with one merge pass. It serves
 * the disk-backed KG builder and atomic artifact publisher without interpreting business sections or retaining a
 * whole-reference in-memory set.
 */
public final class SemanticReferenceClosureStore implements AutoCloseable {
    private final Path workspace;
    private final Path requiredRaw;
    private final Path availableRaw;
    private final BufferedWriter requiredWriter;
    private final BufferedWriter availableWriter;
    private boolean validated;
    private boolean closed;

    public SemanticReferenceClosureStore(Path workspace) {
        if (workspace == null) {
            throw new IllegalArgumentException("semantic reference closure workspace is required");
        }
        this.workspace = workspace;
        try {
            Files.createDirectories(workspace);
            requiredRaw = workspace.resolve("required.raw");
            availableRaw = workspace.resolve("available.raw");
            requiredWriter = Files.newBufferedWriter(
                    requiredRaw, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            availableWriter = Files.newBufferedWriter(
                    availableRaw, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        } catch (IOException failure) {
            throw new ScanResultContractException(
                    "failed to create semantic reference closure store", failure);
        }
    }

    public void require(String reference) {
        append(requiredWriter, reference, "required");
    }

    public void provide(String reference) {
        append(availableWriter, reference, "available");
    }

    public void validate() {
        ensureOpen();
        if (validated) {
            return;
        }
        try {
            requiredWriter.close();
            availableWriter.close();
            Path required = workspace.resolve("required.sorted");
            Path available = workspace.resolve("available.sorted");
            ExternalLineSorter sorter = new ExternalLineSorter();
            sorter.sort(requiredRaw, required, workspace.resolve("required-sort"));
            sorter.sort(availableRaw, available, workspace.resolve("available-sort"));
            mergeValidate(required, available);
            validated = true;
        } catch (IOException failure) {
            throw new ScanResultContractException(
                    "failed to validate semantic reference closure", failure);
        }
    }

    private void mergeValidate(Path required, Path available) throws IOException {
        try (BufferedReader requiredReader = Files.newBufferedReader(required, StandardCharsets.UTF_8);
             BufferedReader availableReader = Files.newBufferedReader(available, StandardCharsets.UTF_8)) {
            String demand = nextDistinct(requiredReader, null);
            String candidate = nextDistinct(availableReader, null);
            while (demand != null) {
                while (candidate != null && candidate.compareTo(demand) < 0) {
                    candidate = nextDistinct(availableReader, candidate);
                }
                if (!demand.equals(candidate)) {
                    throw new ScanResultContractException(
                            "semantic graph contains an unresolved evidence reference");
                }
                demand = nextDistinct(requiredReader, demand);
            }
        }
    }

    private String nextDistinct(BufferedReader reader, String previous) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            if (!line.equals(previous)) {
                return line;
            }
        }
        return null;
    }

    private void append(BufferedWriter writer, String reference, String role) {
        ensureWritable();
        if (reference == null || reference.isBlank()
                || reference.indexOf('\n') >= 0 || reference.indexOf('\r') >= 0) {
            throw new ScanResultContractException(
                    "semantic " + role + " reference is invalid");
        }
        try {
            writer.write(reference);
            writer.newLine();
        } catch (IOException failure) {
            throw new ScanResultContractException(
                    "failed to spool semantic " + role + " reference", failure);
        }
    }

    private void ensureWritable() {
        ensureOpen();
        if (validated) {
            throw new IllegalStateException("semantic reference closure is already validated");
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("semantic reference closure store is closed");
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        RuntimeException failure = null;
        if (!validated) {
            failure = closeWriter(requiredWriter, failure);
            failure = closeWriter(availableWriter, failure);
        }
        if (failure != null) {
            throw failure;
        }
    }

    private RuntimeException closeWriter(BufferedWriter writer, RuntimeException failure) {
        try {
            writer.close();
        } catch (IOException error) {
            RuntimeException closeFailure = new IllegalStateException(
                    "failed to close semantic reference closure spool", error);
            if (failure == null) {
                return closeFailure;
            }
            failure.addSuppressed(closeFailure);
        }
        return failure;
    }
}
