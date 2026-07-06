package com.relationdetector.cli;

import java.nio.file.Files;
import java.nio.file.Path;

final class TestWorkspacePaths {
    private TestWorkspacePaths() {
    }

    static Path relationDetectorRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (isRelationDetectorRoot(current)) {
                return current;
            }
            Path nested = current.resolve("relation-detector");
            if (isRelationDetectorRoot(nested)) {
                return nested;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate relation-detector workspace root");
    }

    private static boolean isRelationDetectorRoot(Path path) {
        return Files.isDirectory(path.resolve("sample-data"))
                && Files.isDirectory(path.resolve("test-fixtures"));
    }
}
