package com.relationdetector.semantic.extraction.runtime;

import com.relationdetector.semantic.extraction.prompt.SemanticExtractionPrompt;
import com.relationdetector.semantic.extraction.artifact.SemanticArtifactRef;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

final class OpenAiResponsesSemanticExtractorTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void reportsUnauthorizedApiFailureWithoutMaterializingOrExposingErrorBody() {
        AtomicBoolean closed = new AtomicBoolean();
        OpenAiResponsesSemanticExtractor extractor = new OpenAiResponsesSemanticExtractor(
                request -> response(
                        401,
                        "{\"error\":{\"message\":\"password=secret-value\"}}",
                        closed),
                ignored -> {
                },
                "http://unit.test/v1",
                "bad-key",
                "gpt-5.5",
                "high",
                30,
                0);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> extractor.extract(prompt(), context("unauthorized", 1000)));

        assertTrue(error.getMessage().contains("HTTP 401"));
        assertFalse(error.getMessage().contains("secret-value"));
        assertFalse(error.getMessage().contains("password"));
        assertTrue(closed.get());
        assertFalse(Files.exists(context("unused", 1000).responsePath()));
    }

    @Test
    void plannedExtractionUsesContextOutputLimitAndReturnsOnlyFileReferences() throws Exception {
        OpenAiResponsesSemanticExtractor extractor = new OpenAiResponsesSemanticExtractor(
                request -> response(
                        200,
                        "{\"output_text\":\"{}\",\"usage\":{\"input_tokens\":321,\"output_tokens\":12}}"),
                ignored -> {
                },
                "http://unit.test/v1",
                "test-key",
                "",
                "",
                30,
                0);
        SemanticModelCallContext context = context("success", 777);

        SemanticModelCallResult result = extractor.extract(prompt(), context);
        JsonNode request = JSON.readTree(result.request().path().toFile());

        assertEquals("gpt-5.6-sol", request.path("model").asText());
        assertEquals("xhigh", request.path("reasoning").path("effort").asText());
        assertEquals(777, request.path("max_output_tokens").asInt());
        assertEquals(context.requestPath(), result.request().path());
        assertEquals(context.responsePath(), result.response().path());
        assertEquals(context.outputPath(), result.output().path());
        assertEquals("{}", Files.readString(result.output().path()));
        assertEquals(321, result.inputTokens());
        assertEquals(12, result.outputTokens());
    }

    @Test
    void standaloneRequestRendererWritesAReferencedFileWithItsExplicitOutputLimit() throws Exception {
        OpenAiResponsesSemanticExtractor extractor = new OpenAiResponsesSemanticExtractor(
                request -> response(200, "{}"),
                ignored -> {
                },
                "http://unit.test/v1",
                "test-key",
                "gpt-5.6-sol",
                "xhigh",
                30,
                0);
        Path target = tempDir.resolve("rendered-request.json");

        SemanticArtifactRef rendered = extractor.renderRequest(prompt(), target, 456);
        JsonNode request = JSON.readTree(rendered.path().toFile());

        assertEquals(456, request.path("max_output_tokens").asInt());
        assertEquals(target.toAbsolutePath().normalize(), rendered.path());
    }

    @Test
    void rejectsInvalidNumericRequestLimitsInsteadOfReplacingThemWithDefaults() {
        OpenAiResponsesSemanticExtractor.ResponsesTransport transport =
                request -> response(200, "{}");

        assertThrows(IllegalArgumentException.class,
                () -> new OpenAiResponsesSemanticExtractor(
                        transport, ignored -> {
                        }, "http://unit.test/v1", "test-key", "", "", 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new OpenAiResponsesSemanticExtractor(
                        transport, ignored -> {
                        }, "http://unit.test/v1", "test-key", "", "", 30, -1));
    }

    @Test
    void retriesOnlyRetryableTransportStatusesAndClosesEveryBody() {
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger closes = new AtomicInteger();
        OpenAiResponsesSemanticExtractor extractor = new OpenAiResponsesSemanticExtractor(
                request -> calls.incrementAndGet() == 1
                        ? response(429, "ignored", closes)
                        : response(200, "{\"output_text\":\"{}\",\"usage\":{}}", closes),
                ignored -> {
                },
                "http://unit.test/v1",
                "test-key",
                "gpt-5.6-sol",
                "xhigh",
                30,
                2);

        extractor.extract(prompt(), context("retry", 1000));

        assertEquals(2, calls.get());
        assertEquals(2, closes.get());
    }

    @Test
    void rejectsOversizedSuccessEnvelopeWithoutPublishingPartialResponse() {
        OpenAiResponsesSemanticExtractor extractor = new OpenAiResponsesSemanticExtractor(
                request -> new OpenAiResponsesSemanticExtractor.TransportResponse(
                        200,
                        new InputStream() {
                            private long remaining = SemanticModelCallContext.responseEnvelopeByteLimit(100)
                                    + 1L;

                            @Override
                            public int read() {
                                return remaining-- > 0 ? 'x' : -1;
                            }
                        }),
                ignored -> {
                },
                "http://unit.test/v1",
                "test-key",
                "gpt-5.6-sol",
                "xhigh",
                30,
                0);
        SemanticModelCallContext context = context("oversized", 100);

        assertThrows(IllegalArgumentException.class,
                () -> extractor.extract(prompt(), context));
        assertFalse(Files.exists(context.responsePath()));
    }

    private SemanticExtractionPrompt prompt() {
        return new SemanticExtractionPrompt(
                "Return JSON only.",
                "Extract candidates.",
                JSON.createObjectNode().put("kind", "test"));
    }

    private SemanticModelCallContext context(String name, int maxOutputTokens) {
        Path scratch = tempDir.resolve(name).toAbsolutePath().normalize();
        return new SemanticModelCallContext(
                scratch,
                scratch.resolve("request.json"),
                scratch.resolve("response.json"),
                scratch.resolve("output.json"),
                maxOutputTokens);
    }

    private OpenAiResponsesSemanticExtractor.TransportResponse response(int status, String body) {
        return new OpenAiResponsesSemanticExtractor.TransportResponse(
                status,
                new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
    }

    private OpenAiResponsesSemanticExtractor.TransportResponse response(
            int status,
            String body,
            AtomicBoolean closed
    ) {
        return new OpenAiResponsesSemanticExtractor.TransportResponse(
                status,
                new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)) {
                    @Override
                    public void close() throws IOException {
                        closed.set(true);
                        super.close();
                    }
                });
    }

    private OpenAiResponsesSemanticExtractor.TransportResponse response(
            int status,
            String body,
            AtomicInteger closes
    ) {
        return new OpenAiResponsesSemanticExtractor.TransportResponse(
                status,
                new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)) {
                    @Override
                    public void close() throws IOException {
                        closes.incrementAndGet();
                        super.close();
                    }
                });
    }
}
