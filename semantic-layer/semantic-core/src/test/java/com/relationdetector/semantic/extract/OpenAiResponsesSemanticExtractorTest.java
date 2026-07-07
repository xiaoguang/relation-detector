package com.relationdetector.semantic.extract;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

final class OpenAiResponsesSemanticExtractorTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void reportsUnauthorizedApiFailureWithoutCallingRealProvider() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/responses", exchange -> {
            byte[] body = "{\"error\":{\"message\":\"invalid api key\"}}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(401, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            OpenAiResponsesSemanticExtractor extractor = new OpenAiResponsesSemanticExtractor(
                    HttpClient.newHttpClient(),
                    baseUrl,
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
        } finally {
            server.stop(0);
        }
    }
}
