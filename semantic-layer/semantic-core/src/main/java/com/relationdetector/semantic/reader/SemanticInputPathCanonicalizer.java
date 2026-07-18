package com.relationdetector.semantic.reader;

import java.nio.file.Path;

/** Produces portable input-path labels without exposing local absolute paths. */
public final class SemanticInputPathCanonicalizer {
    private SemanticInputPathCanonicalizer() {
    }

    public static String canonicalize(Path input) {
        if (input == null) {
            return "external-input";
        }
        Path normalized = input.normalize();
        if (!normalized.isAbsolute()) {
            return separators(normalized.toString());
        }
        Path workspace = Path.of("").toAbsolutePath().normalize();
        if (normalized.startsWith(workspace)) {
            return separators(workspace.relativize(normalized).toString());
        }
        Path filename = normalized.getFileName();
        return filename == null ? "external-input" : separators(filename.toString());
    }

    private static String separators(String value) {
        return value.replace('\\', '/');
    }
}
