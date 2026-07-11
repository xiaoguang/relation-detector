package com.relationdetector.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

record ExpectedLineage(
        boolean exists,
        List<String> fingerprints,
        List<String> forbiddenSources,
        List<String> forbiddenTargets,
        Map<String, Long> warningCodes
) {
    static ExpectedLineage readIfPresent(Path file) {
        if (!Files.exists(file)) {
            return new ExpectedLineage(false, List.of(), List.of(), List.of(), Map.of());
        }
        return CorrectnessJson.readLineage(file);
    }
}
