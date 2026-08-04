package com.relationdetector.semantic.extraction.normalization;

import com.relationdetector.semantic.extraction.prompt.SemanticPromptBudgetEstimator;
import com.relationdetector.semantic.extraction.prompt.SemanticTokenEstimateBudget;

import java.io.FilterInputStream;
import java.io.FilterReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * CN: 以独立 byte、UTF-8、token、depth、string 与单对象边界读取不可信 semantic JSON 文件。
 * EN: Reads an untrusted semantic JSON file behind independent byte, strict UTF-8, token, nesting, string, and
 * exact-one-object limits. The final path is opened NOFOLLOW and all failures use sanitized messages.
 */
public final class SemanticBoundedJsonReader {
    public static final int MAX_NESTING_DEPTH = 128;
    public static final int MAX_STRING_CODE_POINTS = 1_048_576;
    private static final long RESPONSE_BASE_BYTES = 1_048_576L;
    private static final long RESPONSE_BYTES_PER_OUTPUT_TOKEN = 32L;
    private static final ObjectMapper JSON = new ObjectMapper(
            JsonFactory.builder()
                    .streamReadConstraints(StreamReadConstraints.builder()
                            .maxNestingDepth(MAX_NESTING_DEPTH)
                            .maxStringLength(Math.multiplyExact(MAX_STRING_CODE_POINTS, 2))
                            .build())
                    .build());

    public ObjectNode readObject(Path path, int maxEstimatedTokens, String label) {
        long maxBytes = tokenDerivedByteLimit(maxEstimatedTokens);
        if (maxBytes <= 0) {
            throw budgetExceeded(safeLabel(label));
        }
        return readObject(path, new Limits(maxBytes, maxEstimatedTokens), label);
    }

    public ObjectNode readObject(Path path, Limits limits, String label) {
        if (path == null || limits == null) {
            throw new IllegalArgumentException("semantic JSON path and limits are required");
        }
        String safeLabel = safeLabel(label);
        Path normalized = path.toAbsolutePath().normalize();
        try {
            BasicFileAttributes attributes = Files.readAttributes(
                    normalized, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!attributes.isRegularFile() || attributes.size() > limits.maxBytes()) {
                throw byteLimitExceeded(safeLabel);
            }
            if (SemanticPromptBudgetEstimator.minimumEstimateForUtf8Bytes(attributes.size())
                    > limits.maxEstimatedTokens()) {
                throw budgetExceeded(safeLabel);
            }
            Set<OpenOption> options = Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
            try (SeekableByteChannel channel = Files.newByteChannel(normalized, options);
                 InputStream bytes = new ByteLimitInputStream(
                         Channels.newInputStream(channel), limits.maxBytes(), safeLabel);
                 Reader source = new InputStreamReader(
                         bytes,
                         StandardCharsets.UTF_8.newDecoder()
                                 .onMalformedInput(CodingErrorAction.REPORT)
                                 .onUnmappableCharacter(CodingErrorAction.REPORT));
                 TokenBudgetReader bounded = new TokenBudgetReader(
                         source, limits.maxEstimatedTokens(), safeLabel);
                 JsonParser parser = JSON.getFactory().createParser(bounded)) {
                JsonNode value = JSON.readTree(parser);
                if (value == null || !value.isObject() || parser.nextToken() != null) {
                    throw new SemanticExtractionValidationException(
                            safeLabel + " must contain exactly one JSON object");
                }
                bounded.requireCompleteCodePoint();
                requireStringLimits(value, safeLabel);
                return (ObjectNode) value;
            }
        } catch (SemanticExtractionValidationException failure) {
            throw failure;
        } catch (IOException failure) {
            throw new SemanticExtractionValidationException(
                    "failed to read bounded " + safeLabel);
        }
    }

    public static long tokenDerivedByteLimit(int maxEstimatedTokens) {
        if (maxEstimatedTokens <= 0) {
            throw new IllegalArgumentException("semantic token limit must be positive");
        }
        long scaled = Math.multiplyExact((long) maxEstimatedTokens, 100L);
        long maximumBase = scaled / 115L;
        long maximumQuarterBytes = Math.max(0L, maximumBase - 64L);
        return Math.multiplyExact(maximumQuarterBytes, 4L);
    }

    public static long responseEnvelopeByteLimit(int maxOutputTokens) {
        if (maxOutputTokens <= 0) {
            throw new IllegalArgumentException("semantic output token limit must be positive");
        }
        return Math.addExact(
                RESPONSE_BASE_BYTES,
                Math.multiplyExact(RESPONSE_BYTES_PER_OUTPUT_TOKEN, (long) maxOutputTokens));
    }

    private static void requireStringLimits(JsonNode node, String label) {
        if (node.isTextual()) {
            requireCodePoints(node.textValue(), label);
            return;
        }
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                requireCodePoints(field.getKey(), label);
                requireStringLimits(field.getValue(), label);
            }
            return;
        }
        if (node.isArray()) {
            node.forEach(value -> requireStringLimits(value, label));
        }
    }

    private static void requireCodePoints(String value, String label) {
        if (value.codePointCount(0, value.length()) > MAX_STRING_CODE_POINTS) {
            throw new SemanticExtractionValidationException(
                    label + " contains a string above the configured limit");
        }
    }

    private static String safeLabel(String label) {
        return label == null || label.isBlank() ? "semantic JSON input" : label;
    }

    private static SemanticExtractionValidationException budgetExceeded(String label) {
        return new SemanticExtractionValidationException(
                label + " exceeds the configured estimated token limit");
    }

    private static SemanticExtractionValidationException byteLimitExceeded(String label) {
        return new SemanticExtractionValidationException(
                label + " exceeds the configured byte limit");
    }

    public record Limits(long maxBytes, int maxEstimatedTokens) {
        public Limits {
            if (maxBytes <= 0 || maxEstimatedTokens <= 0) {
                throw new IllegalArgumentException("semantic JSON limits must be positive");
            }
        }
    }

    private static final class ByteLimitInputStream extends FilterInputStream {
        private final long maxBytes;
        private final String label;
        private long bytes;

        private ByteLimitInputStream(InputStream delegate, long maxBytes, String label) {
            super(delegate);
            this.maxBytes = maxBytes;
            this.label = label;
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
            if (bytes > maxBytes) {
                throw byteLimitExceeded(label);
            }
        }
    }

    private static final class TokenBudgetReader extends FilterReader {
        private final String label;
        private final SemanticTokenEstimateBudget budget;

        private TokenBudgetReader(Reader delegate, int maxEstimatedTokens, String label) {
            super(delegate);
            this.label = label;
            this.budget = new SemanticTokenEstimateBudget(maxEstimatedTokens);
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

        private void requireCompleteCodePoint() {
            budget.requireCompleteCodePoint(label);
        }
    }
}
