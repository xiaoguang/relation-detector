package com.relationdetector.oracle.log;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.relationdetector.contracts.Enums.LogFormatHint;
import com.relationdetector.core.log.SourceNameNormalizer;

class OracleLogExtractorTest {
    @TempDir
    Path tempDir;

    @Test
    void externalPlainLogUsesContentAddressedSource() throws Exception {
        Path log = Files.writeString(tempDir.resolve("oracle.log"), "SELECT 1 FROM dual;\n");

        var statements = new OracleLogExtractor().extract(log, LogFormatHint.PLAIN_SQL).toList();

        assertEquals(SourceNameNormalizer.normalizeFile(log),
                statements.get(0).attributes().get("sourceFile"));
        assertFalse(statements.get(0).sourceName().contains(tempDir.toString()));
    }
}
