package com.relationdetector.cli;

import java.nio.file.Path;
import java.util.Map;

record ExpectedDiagnostics(
        String fixtureSha256,
        Map<String, Long> warningCodes
) {
    static ExpectedDiagnostics read(Path file) {
        return CorrectnessJson.readDiagnostics(file);
    }
}
