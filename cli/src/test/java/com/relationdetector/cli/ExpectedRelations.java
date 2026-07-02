package com.relationdetector.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

record ExpectedRelations(
        List<String> fingerprints,
        List<String> forbiddenTables
) {
    static ExpectedRelations read(Path file) throws Exception {
        String text = Files.readString(file);
        return new ExpectedRelations(
                CorrectnessJson.stringArray(text, "fingerprints"),
                CorrectnessJson.stringArray(text, "forbiddenTables"));
    }
}
