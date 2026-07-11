package com.relationdetector.semantic.extract;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

final class OpenAiResponsesSemanticExtractorTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void reportsUnauthorizedApiFailureWithoutCallingRealProvider() {
        OpenAiResponsesSemanticExtractor extractor = new OpenAiResponsesSemanticExtractor(
                request -> new OpenAiResponsesSemanticExtractor.TransportResponse(
                        401,
                        "{\"error\":{\"message\":\"invalid api key\"}}"),
                "http://unit.test/v1",
                "bad-key",
                "gpt-5.5",
                "high",
                1000);
        SemanticExtractionPrompt prompt = new SemanticExtractionPrompt(
                "Return JSON only.",
                "Extract candidates.",
                JSON.createObjectNode().put("focus", "test"));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> extractor.extract(prompt));

        assertTrue(error.getMessage().contains("HTTP 401"));
        assertTrue(error.getMessage().contains("invalid api key"));
    }
}
