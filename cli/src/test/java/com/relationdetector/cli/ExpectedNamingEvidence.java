package com.relationdetector.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

record ExpectedNamingEvidence(
        boolean exists,
        List<String> fingerprints
) {
    static ExpectedNamingEvidence readIfPresent(Path file) throws Exception {
        if (!Files.exists(file)) {
            return new ExpectedNamingEvidence(false, List.of());
        }
        String text = Files.readString(file);
        return new ExpectedNamingEvidence(true, CorrectnessJson.stringArray(text, "fingerprints"));
    }
}
