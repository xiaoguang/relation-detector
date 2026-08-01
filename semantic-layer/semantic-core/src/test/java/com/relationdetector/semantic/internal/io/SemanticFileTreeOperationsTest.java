package com.relationdetector.semantic.internal.io;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

final class SemanticFileTreeOperationsTest {
    @TempDir
    Path tempDir;

    @Test
    @Timeout(120)
    void deletesManyPathsWithoutFollowingSymbolicLinks() throws Exception {
        Path root = tempDir.resolve("tree");
        Path external = tempDir.resolve("external.txt");
        Files.writeString(external, "keep");
        Files.createDirectories(root);
        for (int index = 0; index < 20_000; index++) {
            Path group = root.resolve("group-%03d".formatted(index % 100));
            Files.createDirectories(group);
            Files.writeString(group.resolve("file-%05d".formatted(index)), "x");
        }
        Files.createSymbolicLink(root.resolve("external-link"), external);

        SemanticFileTreeOperations.deleteRecursively(root);

        assertFalse(Files.exists(root));
        assertTrue(Files.isRegularFile(external));
    }

    @Test
    void strictAndBestEffortDeletionAcceptMissingOrFileRoots() throws Exception {
        Path file = tempDir.resolve("single-file");
        Files.writeString(file, "x");

        SemanticFileTreeOperations.deleteRecursively(file);

        assertFalse(Files.exists(file));
        assertDoesNotThrow(() -> SemanticFileTreeOperations.deleteRecursively(
                tempDir.resolve("missing")));
        assertDoesNotThrow(() -> SemanticFileTreeOperations.deleteRecursivelyBestEffort(
                tempDir.resolve("missing")));
    }

    @Test
    void strictPropagatesDeletionFailureWhileBestEffortPreservesTheCallerFailure()
            throws Exception {
        assumeTrue(Files.getFileStore(tempDir)
                .supportsFileAttributeView("posix"));
        Path root = tempDir.resolve("protected-tree");
        Files.createDirectories(root);
        Files.writeString(root.resolve("retained"), "x");
        Set<PosixFilePermission> original = Files.getPosixFilePermissions(root);
        Files.setPosixFilePermissions(root, Set.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_EXECUTE));
        try {
            assertThrows(
                    IOException.class,
                    () -> SemanticFileTreeOperations.deleteRecursively(root));
            assertDoesNotThrow(
                    () -> SemanticFileTreeOperations.deleteRecursivelyBestEffort(root));
            assertTrue(Files.exists(root));
        } finally {
            Files.setPosixFilePermissions(root, original);
            SemanticFileTreeOperations.deleteRecursively(root);
        }
    }

    @Test
    void semanticProductionCleanupDoesNotMaterializeReverseOrderedPathLists()
            throws Exception {
        Path root = repoRoot().resolve("semantic-layer");
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths
                    .filter(value -> value.toString().contains("/src/main/java/"))
                    .filter(value -> value.toString().endsWith(".java"))
                    .toList()) {
                String source = Files.readString(path).replaceAll("\\s+", "");
                if (source.contains("reverseOrder()).toList()")) {
                    offenders.add(repoRoot().relativize(path).toString());
                }
            }
        }

        assertTrue(offenders.isEmpty(), "cleanup path lists remain: " + offenders);
    }

    private Path repoRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.isDirectory(current.resolve("semantic-layer/semantic-core"))
                    && Files.isDirectory(current.resolve("relation-detector/core"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Unable to locate repository root");
    }
}
