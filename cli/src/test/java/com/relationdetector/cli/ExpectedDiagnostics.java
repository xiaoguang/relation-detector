package com.relationdetector.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

record ExpectedDiagnostics(
        String fixtureSha256,
        Map<String, Long> warningCodes
) {
    static ExpectedDiagnostics read(Path file) throws Exception {
        String text = Files.readString(file);
        return new ExpectedDiagnostics(
                CorrectnessJson.stringField(text, "fixtureSha256"),
                CorrectnessJson.objectLongs(text, "warningCodes"));
    }
}
