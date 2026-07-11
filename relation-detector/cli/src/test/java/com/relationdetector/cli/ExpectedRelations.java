package com.relationdetector.cli;

import java.nio.file.Path;
import java.util.List;

record ExpectedRelations(
        List<String> fingerprints,
        List<String> forbiddenTables
) {
    static ExpectedRelations read(Path file) {
        return CorrectnessJson.readRelations(file);
    }
}
