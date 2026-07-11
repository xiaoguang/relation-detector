package com.relationdetector.cli;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Files;

import org.junit.jupiter.api.Test;

final class DataLineageAuditGeneratorArchitectureTest {
    @Test
    void reportGenerationConsumesGoldenInsteadOfRunningParsersAgain() throws Exception {
        String source = Files.readString(TestWorkspacePaths.relationDetectorRoot()
                .resolve("cli/src/test/java/com/relationdetector/cli/DataLineageAuditGenerator.java"));

        assertFalse(source.contains("ParserBundleSelector"));
        assertFalse(source.contains("StructuredDataLineageExtractor"));
        assertFalse(source.contains("CommonTokenEventStructuredSqlParser"));
        assertFalse(source.contains("lineageCandidates("));
    }
}
