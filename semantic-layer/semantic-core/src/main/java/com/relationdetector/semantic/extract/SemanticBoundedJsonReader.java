package com.relationdetector.semantic.extract;

import java.io.FilterReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * CN: 在物化单个模型 JSON 前执行与 prompt 一致的保守 token 门限；输入是文件和既有 token 配置，
 * 输出是有界对象树。文件大小只用于快速拒绝，解析流会再次逐码点计数，禁止因路径替换竞态绕过门限。
 *
 * EN: Applies the same conservative token estimate used for prompts before materializing one model JSON document.
 * File size is only an early rejection signal; the parsing reader counts code points again so path replacement
 * races cannot bypass the configured bound.
 */
public final class SemanticBoundedJsonReader {
    private static final ObjectMapper JSON = new ObjectMapper();

    public ObjectNode readObject(Path path, int maxEstimatedTokens, String label) {
        if (path == null || maxEstimatedTokens <= 0) {
            throw new IllegalArgumentException("semantic JSON path and token limit are required");
        }
        String safeLabel = label == null || label.isBlank() ? "semantic JSON input" : label;
        try {
            if (SemanticPromptBudgetEstimator.minimumEstimateForUtf8Bytes(Files.size(path))
                    > maxEstimatedTokens) {
                throw budgetExceeded(safeLabel);
            }
            try (Reader source = new InputStreamReader(
                    Files.newInputStream(path),
                    StandardCharsets.UTF_8.newDecoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT));
                 TokenBudgetReader bounded = new TokenBudgetReader(
                         source, maxEstimatedTokens, safeLabel);
                 JsonParser parser = JSON.getFactory().createParser(bounded)) {
                JsonNode value = JSON.readTree(parser);
                if (value == null || !value.isObject() || parser.nextToken() != null) {
                    throw new SemanticExtractionValidationException(
                            safeLabel + " must contain exactly one JSON object");
                }
                bounded.requireCompleteCodePoint();
                return (ObjectNode) value;
            }
        } catch (SemanticExtractionValidationException failure) {
            throw failure;
        } catch (IOException failure) {
            throw new SemanticExtractionValidationException(
                    "failed to read bounded " + safeLabel);
        }
    }

    private static SemanticExtractionValidationException budgetExceeded(String label) {
        return new SemanticExtractionValidationException(
                label + " exceeds the configured estimated token limit");
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
