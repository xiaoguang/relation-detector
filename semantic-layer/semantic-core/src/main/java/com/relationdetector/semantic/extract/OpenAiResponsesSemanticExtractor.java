package com.relationdetector.semantic.extract;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * CN: 将 evidence-grounded prompt 调用 OpenAI Responses API，并解析结构化输出与成功响应 artifact；HTTP/transport 失败只抛固定脱敏消息，不暴露 response body 或密钥。
 * EN: Calls the OpenAI Responses API with an evidence-grounded prompt and parses structured output plus successful response artifacts. HTTP or transport failures expose only sanitized messages, never response bodies or secrets.
 */
public final class OpenAiResponsesSemanticExtractor {
    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private final ResponsesTransport transport;
    private final URI responsesEndpoint;
    private final String apiKey;
    private final String model;
    private final String reasoningEffort;
    private final int maxOutputTokens;

    public OpenAiResponsesSemanticExtractor(
            String baseUrl,
            String apiKey,
            String model,
            String reasoningEffort,
            int maxOutputTokens
    ) {
        this(new HttpClientResponsesTransport(
                        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build()),
                baseUrl, apiKey, model,
                reasoningEffort, maxOutputTokens);
    }

    OpenAiResponsesSemanticExtractor(
            HttpClient client,
            String baseUrl,
            String apiKey,
            String model,
            String reasoningEffort,
            int maxOutputTokens
    ) {
        this(new HttpClientResponsesTransport(client), baseUrl, apiKey, model, reasoningEffort, maxOutputTokens);
    }

    OpenAiResponsesSemanticExtractor(
            ResponsesTransport transport,
            String baseUrl,
            String apiKey,
            String model,
            String reasoningEffort,
            int maxOutputTokens
    ) {
        this.transport = transport;
        this.responsesEndpoint = responsesEndpoint(baseUrl);
        this.apiKey = apiKey == null ? "" : apiKey;
        this.model = model == null || model.isBlank() ? "gpt-5.5" : model;
        this.reasoningEffort = reasoningEffort == null || reasoningEffort.isBlank() ? "high" : reasoningEffort;
        this.maxOutputTokens = maxOutputTokens <= 0 ? 12000 : maxOutputTokens;
    }

    public SemanticExtractionResult extract(SemanticExtractionPrompt prompt) {
        if (apiKey.isBlank()) {
            throw new IllegalArgumentException("OpenAI API key is required");
        }
        try {
            String requestBody = JSON.writeValueAsString(request(prompt));
            HttpRequest request = HttpRequest.newBuilder(responsesEndpoint)
                    .timeout(Duration.ofMinutes(10))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
            TransportResponse response = transport.send(request);
            JsonNode responseJson = parseJson(response.body());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalArgumentException(
                        "OpenAI Responses API failed with HTTP " + response.statusCode());
            }
            return new SemanticExtractionResult(requestBody, response.body(), outputText(responseJson), responseJson);
        } catch (IOException e) {
            throw new IllegalArgumentException("failed to call OpenAI Responses API", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException("OpenAI Responses API call interrupted", e);
        }
    }

    @FunctionalInterface
    interface ResponsesTransport {
        TransportResponse send(HttpRequest request) throws IOException, InterruptedException;
    }

    record TransportResponse(int statusCode, String body) {
    }

    private record HttpClientResponsesTransport(HttpClient client) implements ResponsesTransport {
        @Override
        public TransportResponse send(HttpRequest request) throws IOException, InterruptedException {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return new TransportResponse(response.statusCode(), response.body());
        }
    }

    public String requestJson(SemanticExtractionPrompt prompt) {
        try {
            return JSON.writeValueAsString(request(prompt));
        } catch (IOException e) {
            throw new IllegalArgumentException("failed to serialize OpenAI request", e);
        }
    }

    private ObjectNode request(SemanticExtractionPrompt prompt) {
        ObjectNode root = JSON.createObjectNode();
        root.put("model", model);
        root.putObject("reasoning").put("effort", reasoningEffort);
        root.put("max_output_tokens", maxOutputTokens);
        ArrayNode input = root.putArray("input");
        input.add(message("developer", prompt.developerPrompt()));
        input.add(message("user", prompt.userPrompt()));
        return root;
    }

    private ObjectNode message(String role, String text) {
        ObjectNode message = JSON.createObjectNode();
        message.put("role", role);
        message.put("type", "message");
        ArrayNode content = message.putArray("content");
        ObjectNode textNode = content.addObject();
        textNode.put("type", "input_text");
        textNode.put("text", text);
        return message;
    }

    private URI responsesEndpoint(String baseUrl) {
        String resolved = baseUrl == null || baseUrl.isBlank() ? "https://api.openai.com/v1" : baseUrl;
        String trimmed = resolved.endsWith("/") ? resolved.substring(0, resolved.length() - 1) : resolved;
        return URI.create(trimmed.endsWith("/responses") ? trimmed : trimmed + "/responses");
    }

    private JsonNode parseJson(String text) {
        try {
            return JSON.readTree(text);
        } catch (IOException e) {
            throw new IllegalArgumentException("OpenAI response was not valid JSON", e);
        }
    }

    private String outputText(JsonNode responseJson) {
        String direct = responseJson.path("output_text").asText("");
        if (!direct.isBlank()) {
            return direct;
        }
        StringBuilder builder = new StringBuilder();
        for (JsonNode output : responseJson.path("output")) {
            for (JsonNode content : output.path("content")) {
                String text = content.path("text").asText("");
                if (!text.isBlank()) {
                    if (!builder.isEmpty()) {
                        builder.append('\n');
                    }
                    builder.append(text);
                }
            }
        }
        return builder.toString();
    }
}
