package com.relationdetector.semantic.extract;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * CN: 在可复用 output root 中原子声明唯一 staging/run 路径，并在完整 manifest 写入后执行同文件系统
 * rename；上游是 run artifact writer，下游是文件系统，禁止写业务 payload 或清理失败 staging。
 *
 * EN: Atomically claims unique staging and run paths under a reusable output root and performs the same-filesystem
 * rename after the manifest is complete. It neither writes domain payloads nor removes failed staging directories.
 */
final class RunArtifactPublisher {
    RunDirectory begin(Path outputRoot) {
        if (outputRoot == null) {
            throw new IllegalArgumentException("semantic extraction output root is required");
        }
        Path root = outputRoot.toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
            while (true) {
                String runId = UUID.randomUUID().toString();
                Path staging = root.resolve(".staging-" + runId);
                try {
                    Files.createDirectory(staging);
                    return new RunDirectory(runId, staging, root.resolve("run-" + runId));
                } catch (java.nio.file.FileAlreadyExistsException ignored) {
                    // UUID collision is improbable, but retrying keeps the directory claim atomic.
                }
            }
        } catch (IOException error) {
            throw new IllegalArgumentException("failed to create semantic extraction run staging directory", error);
        }
    }

    Path publish(RunDirectory runDirectory) {
        try {
            return Files.move(
                    runDirectory.stagingDirectory(),
                    runDirectory.publishedDirectory(),
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException error) {
            throw new IllegalArgumentException("failed to atomically publish semantic extraction run", error);
        }
    }

    record RunDirectory(String runId, Path stagingDirectory, Path publishedDirectory) {
        RunDirectory {
            if (runId == null || runId.isBlank()
                    || stagingDirectory == null || publishedDirectory == null) {
                throw new IllegalArgumentException("semantic extraction run directory is incomplete");
            }
        }
    }
}
