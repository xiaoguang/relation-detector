package com.relationdetector.semantic.extraction.normalization;

import com.relationdetector.semantic.extraction.normalization.SemanticExtractionValidationException;

import com.relationdetector.semantic.extraction.normalization.SemanticBoundedJsonReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class SemanticBoundedJsonReaderTest {
    @TempDir
    Path tempDir;

    @Test
    void acceptsInputAtTheConservativeTokenLimit() throws Exception {
        Path input = tempDir.resolve("bounded.json");
        Files.writeString(input, "{}");

        assertEquals(0, new SemanticBoundedJsonReader()
                .readObject(input, 75, "semantic model result")
                .size());
    }

    @Test
    void rejectsInputBeforeMaterializationWhenTheConservativeLimitIsExceeded() throws Exception {
        Path input = tempDir.resolve("oversized.json");
        Files.writeString(input, "{\"value\":\"订单订单订单订单\"}");

        assertThrows(SemanticExtractionValidationException.class,
                () -> new SemanticBoundedJsonReader()
                        .readObject(input, 75, "semantic model result"));
    }

    @Test
    void rejectsTrailingJsonAndMalformedInput() throws Exception {
        Path trailing = tempDir.resolve("trailing.json");
        Files.writeString(trailing, "{}{}");
        Path malformed = tempDir.resolve("malformed.json");
        Files.writeString(malformed, "{\"value\":");

        SemanticBoundedJsonReader reader = new SemanticBoundedJsonReader();
        assertThrows(SemanticExtractionValidationException.class,
                () -> reader.readObject(trailing, 100, "semantic model result"));
        assertThrows(SemanticExtractionValidationException.class,
                () -> reader.readObject(malformed, 100, "semantic model result"));
    }

    @Test
    void rejectsMalformedUtf8InsteadOfReplacingInvalidBytes() throws Exception {
        Path input = tempDir.resolve("malformed-utf8.json");
        Files.write(input, new byte[] {'{', '"', 'v', '"', ':', '"', (byte) 0xc3, '"', '}'});

        assertThrows(SemanticExtractionValidationException.class,
                () -> new SemanticBoundedJsonReader()
                        .readObject(input, 100, "semantic model result"));
    }
}
