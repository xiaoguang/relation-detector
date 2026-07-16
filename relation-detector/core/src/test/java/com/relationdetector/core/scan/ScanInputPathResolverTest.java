package com.relationdetector.core.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ScanInputPathResolverTest {
    private final ScanInputPathResolver resolver = new ScanInputPathResolver();

    @TempDir
    Path tempDir;

    @Test
    void deduplicatesFilesAndRepeatedRootsByCanonicalPath() throws Exception {
        Path root = Files.createDirectories(tempDir.resolve("input"));
        Path file = Files.writeString(root.resolve("schema.sql"), "SELECT 1;");

        List<Path> resolved = resolver.resolve(
                List.of(file, file), List.of(root, root), List.of("*.sql"), tempDir);

        assertEquals(List.of(file.toRealPath()), resolved);
    }

    @Test
    void resolvesRelativeFilesAgainstBaseDirectory() throws Exception {
        Path file = Files.createDirectories(tempDir.resolve("config"))
                .resolve("schema.sql");
        Files.writeString(file, "SELECT 1;");

        List<Path> resolved = resolver.resolve(
                List.of(Path.of("config/schema.sql")), List.of(), List.of(), tempDir);

        assertEquals(List.of(file.toRealPath()), resolved);
    }

    @Test
    void emptyIncludesUseExistingRecursiveSqlDefaultForRoots() throws Exception {
        Path root = Files.createDirectories(tempDir.resolve("input/nested"));
        Path topLevel = Files.writeString(tempDir.resolve("input/top.sql"), "SELECT 1;");
        Path nested = Files.writeString(root.resolve("nested.sql"), "SELECT 2;");
        Files.writeString(root.resolve("ignored.txt"), "ignore");

        List<Path> resolved = resolver.resolve(
                List.of(), List.of(Path.of("input")), List.of(), tempDir);

        assertEquals(List.of(nested.toRealPath(), topLevel.toRealPath()).stream()
                .sorted(java.util.Comparator.comparing(Path::toString)).toList(), resolved);
    }

    @Test
    void emptyFilesAndRootsResolveToEmptyList() {
        assertEquals(List.of(), resolver.resolve(List.of(), List.of(), List.of(), tempDir));
    }

    @Test
    void rejectsMissingConfiguredRoot() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> resolver.resolve(List.of(), List.of(Path.of("missing")), List.of(), tempDir));

        assertTrue(error.getMessage().contains("source path does not exist"));
    }

    @Test
    void rejectsNonRegularConfiguredFile() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> resolver.resolve(List.of(tempDir), List.of(), List.of(), tempDir));

        assertTrue(error.getMessage().contains("source file is unreadable"));
    }

    @Test
    void rejectsUnreadableConfiguredFileWhenFilesystemEnforcesPermissions() throws Exception {
        Path file = Files.writeString(tempDir.resolve("unreadable.sql"), "SELECT 1;");
        Set<PosixFilePermission> original = Files.getPosixFilePermissions(file);
        try {
            Files.setPosixFilePermissions(file, Set.of(PosixFilePermission.OWNER_WRITE));
            assumeFalse(Files.isReadable(file), "filesystem or effective user bypasses POSIX read permissions");

            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> resolver.resolve(List.of(file), List.of(), List.of(), tempDir));

            assertTrue(error.getMessage().contains("source file is unreadable"));
        } finally {
            Files.setPosixFilePermissions(file, original);
        }
    }
}
