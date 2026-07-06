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
    static ExpectedLineage readIfPresent(Path file) throws Exception {
        if (!Files.exists(file)) {
            return new ExpectedLineage(false, List.of(), List.of(), List.of(), Map.of());
        }
        String text = Files.readString(file);
        return new ExpectedLineage(true,
                CorrectnessJson.stringArray(text, "fingerprints"),
                CorrectnessJson.stringArray(text, "forbiddenSources"),
                CorrectnessJson.stringArray(text, "forbiddenTargets"),
                CorrectnessJson.objectLongs(text, "warningCodes"));
    }
}
