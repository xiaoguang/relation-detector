package com.relationdetector.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

record ExpectedNamingEvidence(
        boolean exists,
        List<String> fingerprints
) {
    static ExpectedNamingEvidence readIfPresent(Path file) {
        if (!Files.exists(file)) {
            return new ExpectedNamingEvidence(false, List.of());
        }
        return CorrectnessJson.readNamingEvidence(file);
    }
}
