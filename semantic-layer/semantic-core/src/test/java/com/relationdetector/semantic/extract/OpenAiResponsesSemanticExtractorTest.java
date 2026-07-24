package com.relationdetector.semantic.extract;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.atomic.AtomicInteger;

final class OpenAiResponsesSemanticExtractorTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void reportsUnauthorizedApiFailureWithoutCallingRealProvider() {
        OpenAiResponsesSemanticExtractor extractor = new OpenAiResponsesSemanticExtractor(
                request -> new OpenAiResponsesSemanticExtractor.TransportResponse(
                        401,
                        "{\"error\":{\"message\":\"password=secret-value\"}}"),
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
        assertFalse(error.getMessage().contains("secret-value"));
        assertFalse(error.getMessage().contains("password"));
    }

    @Test
    void defaultsToGpt56SolWithExtraHighReasoning() throws Exception {
        OpenAiResponsesSemanticExtractor extractor = new OpenAiResponsesSemanticExtractor(
                request -> new OpenAiResponsesSemanticExtractor.TransportResponse(
                        200,
                        "{\"output_text\":\"{}\",\"usage\":{\"input_tokens\":321,\"output_tokens\":12}}"),
                "http://unit.test/v1",
                "test-key",
                "",
                "",
                1000);
        SemanticExtractionPrompt prompt = new SemanticExtractionPrompt(
                "Return JSON only.",
                "Extract candidates.",
                JSON.createObjectNode().put("focus", "test"));

        JsonNode request = JSON.readTree(extractor.requestJson(prompt));
        SemanticExtractionResult result = extractor.extract(prompt);

        assertEquals("gpt-5.6-sol", request.path("model").asText());
        assertEquals("xhigh", request.path("reasoning").path("effort").asText());
        assertEquals(321, result.inputTokens());
        assertEquals(12, result.outputTokens());
    }

    @Test
    void rejectsInvalidNumericRequestLimitsInsteadOfReplacingThemWithDefaults() {
        OpenAiResponsesSemanticExtractor.ResponsesTransport transport =
                request -> new OpenAiResponsesSemanticExtractor.TransportResponse(200, "{}");

        assertThrows(IllegalArgumentException.class,
                () -> new OpenAiResponsesSemanticExtractor(
                        transport, ignored -> {
                        }, "http://unit.test/v1", "test-key", "", "", 0, 30, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new OpenAiResponsesSemanticExtractor(
                        transport, ignored -> {
                        }, "http://unit.test/v1", "test-key", "", "", 1000, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new OpenAiResponsesSemanticExtractor(
                        transport, ignored -> {
                        }, "http://unit.test/v1", "test-key", "", "", 1000, 30, -1));
    }

    @Test
    void retriesOnlyRetryableTransportStatusesWithinConfiguredBound() {
        AtomicInteger calls = new AtomicInteger();
        OpenAiResponsesSemanticExtractor extractor = new OpenAiResponsesSemanticExtractor(
                request -> calls.incrementAndGet() == 1
                        ? new OpenAiResponsesSemanticExtractor.TransportResponse(429, "{}")
                        : new OpenAiResponsesSemanticExtractor.TransportResponse(
                                200, "{\"output_text\":\"{}\",\"usage\":{}}"),
                ignored -> {
                },
                "http://unit.test/v1",
                "test-key",
                "gpt-5.6-sol",
                "xhigh",
                1000,
                30,
                2);

        extractor.extract(prompt());

        assertEquals(2, calls.get());
    }

    @Test
    void doesNotRetryNonRetryableClientFailure() {
        AtomicInteger calls = new AtomicInteger();
        OpenAiResponsesSemanticExtractor extractor = new OpenAiResponsesSemanticExtractor(
                request -> {
                    calls.incrementAndGet();
                    return new OpenAiResponsesSemanticExtractor.TransportResponse(400, "{}");
                },
                ignored -> {
                },
                "http://unit.test/v1",
                "test-key",
                "gpt-5.6-sol",
                "xhigh",
                1000,
                30,
                2);

        assertThrows(IllegalArgumentException.class, () -> extractor.extract(prompt()));

        assertEquals(1, calls.get());
    }

    private SemanticExtractionPrompt prompt() {
        return new SemanticExtractionPrompt(
                "Return JSON only.",
                "Extract candidates.",
                JSON.createObjectNode().put("focus", "test"));
    }
}
