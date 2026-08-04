package com.relationdetector.semantic.extraction.artifact;

import java.io.FilterInputStream;
import java.io.FilterReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relationdetector.semantic.extraction.normalization.SemanticExtractionValidationException;
import com.relationdetector.semantic.extraction.prompt.SemanticPromptBudgetEstimator;
import com.relationdetector.semantic.extraction.prompt.SemanticTokenEstimateBudget;

/** Bounded exact-object reader used only for detached request-package artifacts. */
final class SemanticRequestPackageJsonReader {
    ObjectNode readObject(
            Path path,
            long maximumBytes,
            Integer maximumEstimatedTokens,
            SemanticRequestPackageLimits limits
    ) {
        if (path == null || maximumBytes <= 0 || limits == null
                || maximumEstimatedTokens != null && maximumEstimatedTokens <= 0) {
            throw new IllegalArgumentException("semantic request JSON path and limits are required");
        }
        try {
            long size = Files.size(path);
            if (size > maximumBytes
                    || maximumEstimatedTokens != null
                    && SemanticPromptBudgetEstimator.minimumEstimateForUtf8Bytes(size)
                    > maximumEstimatedTokens) {
                throw invalid();
            }
            ObjectMapper mapper = mapper(limits);
            try (InputStream file = Files.newInputStream(path);
                 InputStream bytes = new ByteLimitInputStream(file, maximumBytes);
                 Reader decoded = new InputStreamReader(
                         bytes,
                         StandardCharsets.UTF_8.newDecoder()
                                 .onMalformedInput(CodingErrorAction.REPORT)
                                 .onUnmappableCharacter(CodingErrorAction.REPORT));
                 Reader bounded = maximumEstimatedTokens == null
                         ? decoded
                         : new TokenLimitReader(decoded, maximumEstimatedTokens);
                 JsonParser parser = mapper.getFactory().createParser(bounded)) {
                JsonNode value = mapper.readTree(parser);
                if (value == null || !value.isObject() || parser.nextToken() != null) {
                    throw invalid();
                }
                if (bounded instanceof TokenLimitReader tokenReader) {
                    tokenReader.requireComplete();
                }
                requireStringLimits(value, limits.maxStringCodePoints());
                return (ObjectNode) value;
            }
        } catch (SemanticExtractionValidationException failure) {
            throw failure;
        } catch (IOException | RuntimeException failure) {
            throw invalid();
        }
    }

    ObjectMapper mapper(SemanticRequestPackageLimits limits) {
        int maximumUtf16Units = limits.maxStringCodePoints() > Integer.MAX_VALUE / 2
                ? Integer.MAX_VALUE
                : limits.maxStringCodePoints() * 2;
        JsonFactory factory = JsonFactory.builder()
                .streamReadConstraints(StreamReadConstraints.builder()
                        .maxNestingDepth(limits.maxJsonDepth())
                        .maxStringLength(maximumUtf16Units)
                        .build())
                .build();
        return new ObjectMapper(factory);
    }

    void requireStringLimits(JsonNode node, int maximumCodePoints) {
        if (node.isTextual()) {
            requireCodePoints(node.textValue(), maximumCodePoints);
            return;
        }
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                requireCodePoints(field.getKey(), maximumCodePoints);
                requireStringLimits(field.getValue(), maximumCodePoints);
            }
            return;
        }
        if (node.isArray()) {
            node.forEach(value -> requireStringLimits(value, maximumCodePoints));
        }
    }

    private void requireCodePoints(String value, int maximumCodePoints) {
        if (value.codePointCount(0, value.length()) > maximumCodePoints) {
            throw invalid();
        }
    }

    private static final class ByteLimitInputStream extends FilterInputStream {
        private final long maximumBytes;
        private long bytes;

        private ByteLimitInputStream(InputStream delegate, long maximumBytes) {
            super(delegate);
            this.maximumBytes = maximumBytes;
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value >= 0) {
                add(1);
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int read = super.read(buffer, offset, length);
            if (read > 0) {
                add(read);
            }
            return read;
        }

        private void add(int count) {
            bytes = Math.addExact(bytes, count);
            if (bytes > maximumBytes) {
                throw invalid();
            }
        }
    }

    private static final class TokenLimitReader extends FilterReader {
        private final SemanticTokenEstimateBudget budget;

        private TokenLimitReader(Reader delegate, int maximumEstimatedTokens) {
            super(delegate);
            this.budget = new SemanticTokenEstimateBudget(maximumEstimatedTokens);
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value >= 0) {
                budget.add((char) value);
            }
            return value;
        }

        @Override
        public int read(char[] buffer, int offset, int length) throws IOException {
            int read = super.read(buffer, offset, length);
            if (read > 0) {
                budget.add(buffer, offset, read);
            }
            return read;
        }

        private void requireComplete() {
            budget.requireCompleteCodePoint("semantic request JSON");
        }
    }

    static SemanticExtractionValidationException invalid() {
        return new SemanticExtractionValidationException(
                "semantic request bundle package is invalid");
    }
}
