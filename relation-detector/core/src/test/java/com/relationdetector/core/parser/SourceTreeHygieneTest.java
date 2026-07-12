package com.relationdetector.core.parser;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class SourceTreeHygieneTest {
    private static final Set<String> SOURCE_MODULES = Set.of(
            "contracts", "core", "cli", "adaptor-mysql", "adaptor-postgres",
            "adaptor-oracle", "adaptor-sqlserver", "grammar");

    @Test
    void sourceAndFixtureTreesDoNotContainEmptyMigrationResidue() throws IOException {
        Path root = repoRoot();
        List<Path> offenders = new ArrayList<>();
        for (String module : SOURCE_MODULES) {
            Path moduleRoot = root.resolve(module);
            if (!Files.exists(moduleRoot)) continue;
            try (Stream<Path> paths = Files.walk(moduleRoot)) {
                paths.filter(Files::isDirectory)
                        .filter(path -> !ignored(path))
                        .filter(this::isEmpty)
                        .map(root::relativize)
                        .forEach(offenders::add);
            }
        }
        Path correctness = root.resolve("test-fixtures/correctness");
        try (Stream<Path> fixtures = Files.walk(correctness)) {
            for (Path directory : fixtures.filter(Files::isDirectory).toList()) {
                if (isEmpty(directory)
                        || (containsFixtureAsset(directory)
                                && !Files.exists(directory.resolve("manifest.yml")))) {
                    offenders.add(root.relativize(directory));
                }
            }
        }
        assertTrue(offenders.isEmpty(), "Empty or incomplete source/fixture directories: " + offenders);
    }

    private boolean ignored(Path path) {
        String value = path.toString();
        return value.contains("/target/") || value.endsWith("/target")
                || value.contains("/.mvn/build-cache/");
    }

    private boolean isEmpty(Path directory) {
        try (Stream<Path> children = Files.list(directory)) {
            return children.findAny().isEmpty();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private boolean containsFixtureAsset(Path directory) {
        try (Stream<Path> children = Files.list(directory)) {
            return children.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .anyMatch(name -> name.equals("input.sql")
                            || name.startsWith("expected-") && name.endsWith(".json"));
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static Path repoRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.isDirectory(current.resolve("core"))
                    && Files.isDirectory(current.resolve("grammar"))) return current;
            current = current.getParent();
        }
        throw new IllegalStateException("Unable to locate relation-detector root");
    }
}
