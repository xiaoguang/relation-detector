package com.relationdetector.semantic.extract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.FilterReader;
import java.io.StringReader;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

final class SemanticTokenEstimateBudgetTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void rejectsOversizedValueBeforeReadingTheRemainingJson() throws Exception {
        String input = "[\"" + "x\",\"".repeat(100_000) + "x\"]";
        CountingReader source = new CountingReader(new StringReader(input));
        try (JsonParser parser = JSON.getFactory().createParser(source)) {
            assertEquals(JsonToken.START_ARRAY, parser.nextToken());
            SemanticTokenEstimateBudget budget = new SemanticTokenEstimateBudget(128);

            assertThrows(
                    SemanticExtractionValidationException.class,
                    () -> budget.readValue(parser, JSON, "semantic envelope"));

            assertTrue(source.readCharacters() < input.length());
        }
    }

    @Test
    void materializesAValueOnlyAfterItsCompleteTextFitsTheBudget() throws Exception {
        try (JsonParser parser = JSON.getFactory().createParser(
                "{\"catalog\":\"shop\",\"schema\":\"\"}")) {
            assertEquals(JsonToken.START_OBJECT, parser.nextToken());
            SemanticTokenEstimateBudget budget = new SemanticTokenEstimateBudget(1_000);

            JsonNode value = budget.readValue(parser, JSON, "semantic database envelope");

            assertEquals("shop", value.path("catalog").asText());
        }
    }

    private static final class CountingReader extends FilterReader {
        private long readCharacters;

        private CountingReader(StringReader delegate) {
            super(delegate);
        }

        @Override
        public int read() throws java.io.IOException {
            int value = super.read();
            if (value >= 0) {
                readCharacters++;
            }
            return value;
        }

        @Override
        public int read(char[] buffer, int offset, int length) throws java.io.IOException {
            int read = super.read(buffer, offset, length);
            if (read > 0) {
                readCharacters += read;
            }
            return read;
        }

        private long readCharacters() {
            return readCharacters;
        }
    }
}
