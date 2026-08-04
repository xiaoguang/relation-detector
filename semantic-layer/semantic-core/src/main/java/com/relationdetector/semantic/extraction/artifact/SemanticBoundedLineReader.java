package com.relationdetector.semantic.extraction.artifact;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

import com.relationdetector.semantic.extraction.normalization.SemanticExtractionValidationException;

/** Streams strict UTF-8 lines without ever allocating more than the trusted per-line byte limit. */
final class SemanticBoundedLineReader {
    private SemanticBoundedLineReader() {
    }

    static long forEach(Path path, int maximumLineBytes, Consumer<String> consumer) {
        if (path == null || maximumLineBytes <= 0 || consumer == null) {
            throw new IllegalArgumentException("semantic bounded line input is required");
        }
        long lines = 0;
        try (InputStream input = Files.newInputStream(path)) {
            ByteArrayOutputStream line = new ByteArrayOutputStream(Math.min(maximumLineBytes, 8192));
            int value;
            while ((value = input.read()) >= 0) {
                if (value == '\n') {
                    consumer.accept(decode(line.toByteArray()));
                    lines = Math.addExact(lines, 1);
                    line.reset();
                    continue;
                }
                if (line.size() >= maximumLineBytes) {
                    throw invalid();
                }
                line.write(value);
            }
            if (line.size() > 0) {
                consumer.accept(decode(line.toByteArray()));
                lines = Math.addExact(lines, 1);
            }
            return lines;
        } catch (SemanticExtractionValidationException failure) {
            throw failure;
        } catch (IOException | RuntimeException failure) {
            throw invalid();
        }
    }

    private static String decode(byte[] bytes) {
        int length = bytes.length;
        if (length > 0 && bytes[length - 1] == '\r') {
            length--;
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes, 0, length))
                    .toString();
        } catch (IOException failure) {
            throw invalid();
        }
    }

    private static SemanticExtractionValidationException invalid() {
        return new SemanticExtractionValidationException(
                "semantic request bundle package is invalid");
    }
}
