package com.relationdetector.semantic.extraction.normalization;

import com.relationdetector.semantic.extraction.normalization.SemanticExtractionValidationException;

import com.relationdetector.semantic.extraction.normalization.SemanticBoundedJsonReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void rejectsByteLimitBeforeParsingAndUsesExactLimitArithmetic() throws Exception {
        Path input = tempDir.resolve("byte-limit.json");
        Files.writeString(input, "{\"value\":1}");
        SemanticBoundedJsonReader reader = new SemanticBoundedJsonReader();

        assertThrows(SemanticExtractionValidationException.class,
                () -> reader.readObject(
                        input,
                        new SemanticBoundedJsonReader.Limits(4, 1000),
                        "semantic model result"));
        assertEquals(1_048_576L + 32L * 123L,
                SemanticBoundedJsonReader.responseEnvelopeByteLimit(123));
        assertEquals(4L * ((100L * 100L / 115L) - 64L),
                SemanticBoundedJsonReader.tokenDerivedByteLimit(100));
    }

    @Test
    void rejectsNestingPast128AndStringsPastOneMiCodePoints() throws Exception {
        Path nested = tempDir.resolve("nested.json");
        Files.writeString(nested, "{\"v\":" + "[".repeat(128) + "0" + "]".repeat(128) + "}");
        Path longString = tempDir.resolve("long-string.json");
        Files.writeString(longString, "{\"v\":\"" + "x".repeat(1_048_577) + "\"}");
        SemanticBoundedJsonReader reader = new SemanticBoundedJsonReader();
        SemanticBoundedJsonReader.Limits generous =
                new SemanticBoundedJsonReader.Limits(2_000_000, 2_000_000);

        assertThrows(SemanticExtractionValidationException.class,
                () -> reader.readObject(nested, generous, "semantic model result"));
        assertThrows(SemanticExtractionValidationException.class,
                () -> reader.readObject(longString, generous, "semantic model result"));
    }

    @Test
    void rejectsSymlinkEvenWhenItsTargetIsAValidObject() throws Exception {
        Path target = tempDir.resolve("target.json");
        Files.writeString(target, "{}");
        Path link = tempDir.resolve("link.json");
        try {
            Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException failure) {
            assertTrue(true);
            return;
        }

        assertThrows(SemanticExtractionValidationException.class,
                () -> new SemanticBoundedJsonReader()
                        .readObject(link, 100, "semantic model result"));
    }
}
