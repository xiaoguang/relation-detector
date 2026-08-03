package com.relationdetector.semantic.kg.store;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.UUID;

import com.relationdetector.semantic.ingest.ScanResultContractException;
import com.relationdetector.semantic.internal.io.SemanticAtomicFiles;
import com.relationdetector.semantic.internal.io.SemanticFileTreeOperations;

/**
 * CN: 在目标同级隐藏目录中完成整套KG artifact构建，并在全部文件通过后一次性原子发布目标目录。
 * 上游是KG writer，下游是文件系统；本类不渲染JSON、不覆盖已有目标，也不保留失败的staging目录。
 * EN: Builds a complete KG artifact set in a hidden sibling directory and atomically publishes the target only after
 * every file succeeds. It sits between the KG writer and filesystem without rendering JSON, replacing an existing
 * target, or retaining failed staging directories.
 */
final class SemanticKgArtifactPublisher {
    <T> T publish(Path target, StagedWriter<T> writer) {
        if (target == null || writer == null) {
            throw new IllegalArgumentException("semantic KG target and staged writer are required");
        }
        Path normalized = target.toAbsolutePath().normalize();
        Path parent = normalized.getParent();
        if (parent == null) {
            throw new ScanResultContractException("semantic KG target has no parent directory");
        }
        Path staging = null;
        try {
            Files.createDirectories(parent);
            if (Files.exists(normalized, LinkOption.NOFOLLOW_LINKS)) {
                throw new ScanResultContractException("semantic KG output target already exists");
            }
            staging = createStaging(parent, normalized.getFileName().toString());
            T result = writer.write(staging);
            SemanticAtomicFiles.publishDirectory(staging, normalized);
            staging = null;
            return result;
        } catch (ScanResultContractException failure) {
            throw failure;
        } catch (IOException failure) {
            throw new ScanResultContractException("failed to atomically publish semantic KG artifacts", failure);
        } finally {
            SemanticFileTreeOperations.deleteRecursivelyBestEffort(staging);
        }
    }

    private Path createStaging(Path parent, String targetName) throws IOException {
        while (true) {
            Path candidate = parent.resolve("." + targetName + ".staging-" + UUID.randomUUID());
            try {
                return Files.createDirectory(candidate);
            } catch (FileAlreadyExistsException ignored) {
                // Retry the atomic claim if a UUID collision or concurrent stale path exists.
            }
        }
    }

    @FunctionalInterface
    interface StagedWriter<T> {
        T write(Path staging) throws IOException;
    }
}
