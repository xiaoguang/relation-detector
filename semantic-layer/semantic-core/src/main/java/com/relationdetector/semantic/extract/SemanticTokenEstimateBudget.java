package com.relationdetector.semantic.extract;

import java.io.IOException;
import java.io.Writer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * CN: 以统一的code-point计数累计semantic输入预算，并在单个JSON子树完整物化前通过有界writer
 * 流式复制和拒绝超限；输入是文本或当前位置的parser value，输出只允许预算内的JsonNode。
 * 本类不估算模型精确token，也不修改JSON语义。
 * EN: Accumulates the shared semantic input estimate by code point and copies one JSON subtree through a bounded
 * writer so oversized values fail before complete materialization. It returns only budget-compliant JsonNodes and
 * neither claims exact model tokens nor changes JSON semantics.
 */
final class SemanticTokenEstimateBudget {
    private final int maxEstimatedTokens;
    private long asciiCodePoints;
    private long nonAsciiCodePoints;
    private boolean previousWasHighSurrogate;

    SemanticTokenEstimateBudget(int maxEstimatedTokens) {
        if (maxEstimatedTokens <= 0) {
            throw new IllegalArgumentException("semantic token estimate budget must be positive");
        }
        this.maxEstimatedTokens = maxEstimatedTokens;
    }

    void addText(CharSequence value) {
        if (value == null) {
            return;
        }
        for (int index = 0; index < value.length(); index++) {
            add(value.charAt(index));
        }
    }

    void add(char[] value, int offset, int length) {
        for (int index = 0; index < length; index++) {
            add(value[offset + index]);
        }
    }

    void add(char value) {
        if (Character.isLowSurrogate(value) && previousWasHighSurrogate) {
            previousWasHighSurrogate = false;
            return;
        }
        previousWasHighSurrogate = Character.isHighSurrogate(value);
        if (value <= 0x7f) {
            asciiCodePoints++;
        } else {
            nonAsciiCodePoints++;
        }
        requireWithinBudget();
    }

    JsonNode readValue(JsonParser parser, ObjectMapper mapper, String label) {
        if (parser == null || mapper == null || parser.currentToken() == null) {
            throw new IllegalArgumentException(
                    "semantic JSON parser, mapper and current value are required");
        }
        String safeLabel = label == null || label.isBlank()
                ? "semantic JSON value"
                : label;
        StringBuilder buffer = new StringBuilder();
        try (JsonGenerator generator = mapper.getFactory().createGenerator(
                new BudgetWriter(buffer, this))) {
            generator.copyCurrentStructure(parser);
        } catch (SemanticExtractionValidationException failure) {
            throw failure;
        } catch (IOException failure) {
            throw new SemanticExtractionValidationException(
                    "failed to stream " + safeLabel);
        }
        requireCompleteCodePoint(safeLabel);
        try {
            JsonNode value = mapper.readTree(buffer.toString());
            if (value == null) {
                throw new SemanticExtractionValidationException(
                        safeLabel + " is required");
            }
            return value;
        } catch (IOException failure) {
            throw new SemanticExtractionValidationException(
                    "failed to read " + safeLabel);
        }
    }

    void requireCompleteCodePoint(String label) {
        if (previousWasHighSurrogate) {
            throw new SemanticExtractionValidationException(
                    label + " contains an incomplete Unicode code point");
        }
    }

    void requireMayFitUtf8Bytes(long bytes) {
        int current = SemanticPromptBudgetEstimator.estimate(
                asciiCodePoints, nonAsciiCodePoints);
        int minimum = SemanticPromptBudgetEstimator.minimumEstimateForUtf8Bytes(bytes);
        if ((long) current + minimum > maxEstimatedTokens) {
            throw new SemanticExtractionValidationException(
                    "semantic evidence closure exceeds the configured estimated input-token limit");
        }
    }

    int maximumSingleStringLength() {
        long maximum = Math.multiplyExact((long) maxEstimatedTokens, 4L);
        return maximum > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) maximum;
    }

    private void requireWithinBudget() {
        if (SemanticPromptBudgetEstimator.estimate(
                asciiCodePoints, nonAsciiCodePoints) > maxEstimatedTokens) {
            throw new SemanticExtractionValidationException(
                    "semantic evidence closure exceeds the configured estimated input-token limit");
        }
    }

    private static final class BudgetWriter extends Writer {
        private final StringBuilder target;
        private final SemanticTokenEstimateBudget budget;

        private BudgetWriter(
                StringBuilder target,
                SemanticTokenEstimateBudget budget
        ) {
            this.target = target;
            this.budget = budget;
        }

        @Override
        public void write(char[] buffer, int offset, int length) {
            budget.add(buffer, offset, length);
            target.append(buffer, offset, length);
        }

        @Override
        public void write(int value) {
            char character = (char) value;
            budget.add(character);
            target.append(character);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }
}
