package com.relationdetector.semantic.extraction.artifact;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relationdetector.semantic.internal.io.SemanticAtomicFiles;
import com.relationdetector.semantic.internal.io.SemanticFileDigest;
import com.relationdetector.semantic.internal.io.SemanticFileTreeOperations;

/**
 * CN: 为 semantic run artifact 提供原子 manifest/目录写入、文件索引、流式 SHA-256、选择性复制和尽力清理；
 * 输入是已声明的 staging/candidate 路径，输出是可复核文件事务。本类不构造业务 manifest、不调用模型，
 * 也不决定 retention 应保留哪些业务 artifact。
 * EN: Provides atomic manifest/directory writes, file indexing, streaming SHA-256, selective copying, and
 * best-effort cleanup for semantic run artifacts. It neither builds domain manifests nor calls models or chooses
 * which business artifacts a retention policy keeps.
 */
public final class RunArtifactFileStore {
    private final ObjectMapper json;

    public RunArtifactFileStore(ObjectMapper json) {
        this.json = json;
    }

    public void writeJson(Path path, Object value) {
        try {
            Files.createDirectories(path.getParent());
            json.writeValue(path.toFile(), value);
        } catch (IOException failure) {
            throw new IllegalArgumentException("failed to write semantic JSON artifact", failure);
        }
    }

    public void writeText(Path path, String value) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, value == null ? "" : value);
        } catch (IOException failure) {
            throw new IllegalArgumentException("failed to write semantic text artifact", failure);
        }
    }

    public void writeManifest(Path directory, ObjectNode manifest) {
        Path target = directory.resolve("run-manifest.json");
        try {
            SemanticAtomicFiles.replace(target, temporary -> json.writeValue(temporary.toFile(), manifest));
        } catch (IOException failure) {
            throw new IllegalArgumentException("failed to update semantic run manifest", failure);
        }
    }

    public void writeDirectoryAtomically(Path temporary, Path target, Consumer<Path> writer) {
        try {
            Files.createDirectories(temporary.getParent());
            writer.accept(temporary);
            SemanticAtomicFiles.publishDirectory(temporary, target);
        } catch (IOException failure) {
            deleteRecursivelyBestEffort(temporary);
            throw new IllegalArgumentException("failed to publish semantic audit directory", failure);
        } catch (RuntimeException | Error failure) {
            deleteRecursivelyBestEffort(temporary);
            throw failure;
        }
    }

    public void copyFile(Path source, Path target, String failureMessage) {
        try {
            Files.createDirectories(target.getParent());
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException failure) {
            throw new IllegalArgumentException(failureMessage, failure);
        }
    }

    public void copyMatching(
            Path source,
            Path target,
            Predicate<String> retained,
            String failureMessage
    ) {
        for (Path file : regularFiles(source)) {
            String relative = relative(source, file);
            if (!retained.test(relative)) {
                continue;
            }
            copyFreshFile(file, target.resolve(relative), failureMessage);
        }
    }

    public List<ArtifactEntry> artifactEntries(Path root, Predicate<String> included) {
        List<Path> files = regularFiles(root);
        Map<Path, ArtifactEntry> precomputed = precomputedEntries(root, files);
        return files.stream()
                .map(path -> precomputed.containsKey(path)
                        ? precomputed.get(path)
                        : artifactEntry(root, path))
                .filter(entry -> included.test(entry.path()))
                .sorted(Comparator.comparing(ArtifactEntry::path))
                .toList();
    }

    public void writeArtifactEntries(ArrayNode target, List<ArtifactEntry> entries) {
        for (ArtifactEntry artifact : entries) {
            target.addObject()
                    .put("path", artifact.path())
                    .put("size", artifact.size())
                    .put("sha256", artifact.sha256());
        }
    }

    public void deleteRecursivelyBestEffort(Path root) {
        SemanticFileTreeOperations.deleteRecursivelyBestEffort(root);
    }

    private List<Path> regularFiles(Path root) {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(path -> relative(root, path)))
                    .toList();
        } catch (IOException failure) {
            throw new IllegalArgumentException("failed to inspect semantic run artifacts", failure);
        }
    }

    private void copyFreshFile(Path source, Path target, String failureMessage) {
        try {
            Files.createDirectories(target.getParent());
            Files.copy(source, target);
        } catch (IOException failure) {
            throw new IllegalArgumentException(failureMessage, failure);
        }
    }

    private ArtifactEntry artifactEntry(Path root, Path file) {
        try {
            SemanticFileDigest.Digest digest = SemanticFileDigest.compute(file);
            return new ArtifactEntry(relative(root, file), digest.bytes(), digest.sha256());
        } catch (IOException failure) {
            throw new IllegalArgumentException("failed to inspect semantic artifact", failure);
        }
    }

    private Map<Path, ArtifactEntry> precomputedEntries(Path root, List<Path> files) {
        Map<Path, ArtifactEntry> result = new HashMap<>();
        for (Path report : files) {
            if (!"semantic-kg-digests.json".equals(report.getFileName().toString())) {
                continue;
            }
            try {
                JsonNode document = json.readTree(report.toFile());
                if (!"FULL".equals(document.path("mode").asText())
                        || !document.path("artifacts").isArray()) {
                    continue;
                }
                for (JsonNode artifact : document.path("artifacts")) {
                    String name = artifact.path("path").asText("");
                    long bytes = artifact.path("bytes").asLong(-1);
                    String sha = artifact.path("sha256").asText("");
                    Path target = report.getParent().resolve(name).normalize();
                    if (name.isBlank()
                            || !target.toAbsolutePath().normalize().startsWith(
                                    root.toAbsolutePath().normalize())
                            || !Files.isRegularFile(target) || Files.size(target) != bytes
                            || !sha.matches("[0-9a-f]{64}")) {
                        throw new IllegalArgumentException(
                                "semantic KG digest report does not match persisted artifacts");
                    }
                    result.put(target, new ArtifactEntry(relative(root, target), bytes, sha));
                }
            } catch (IOException failure) {
                throw new IllegalArgumentException("failed to read semantic KG digest report", failure);
            }
        }
        return result;
    }

    private String relative(Path root, Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }

    public record ArtifactEntry(String path, long size, String sha256) {
    }
}
