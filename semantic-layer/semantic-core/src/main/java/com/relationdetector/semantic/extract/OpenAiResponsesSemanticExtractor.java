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
public final class OpenAiResponsesSemanticExtractor implements SemanticModelClient {
    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private final ResponsesTransport transport;
    private final URI responsesEndpoint;
    private final String apiKey;
    private final String model;
    private final String reasoningEffort;
    private final int maxOutputTokens;
    private final Duration requestTimeout;
    private final int maxTransportRetries;
    private final RetrySleeper retrySleeper;

    public OpenAiResponsesSemanticExtractor(
            String baseUrl,
            String apiKey,
            String model,
            String reasoningEffort,
            int maxOutputTokens
    ) {
        this(new HttpClientResponsesTransport(
                        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build()),
                Thread::sleep, baseUrl, apiKey, model,
                reasoningEffort, maxOutputTokens, 600, 0);
    }

    public OpenAiResponsesSemanticExtractor(
            String baseUrl,
            String apiKey,
            String model,
            String reasoningEffort,
            int maxOutputTokens,
            int requestTimeoutSeconds,
            int maxTransportRetries
    ) {
        this(new HttpClientResponsesTransport(
                        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build()),
                Thread::sleep, baseUrl, apiKey, model, reasoningEffort, maxOutputTokens,
                requestTimeoutSeconds, maxTransportRetries);
    }

    OpenAiResponsesSemanticExtractor(
            HttpClient client,
            String baseUrl,
            String apiKey,
            String model,
            String reasoningEffort,
            int maxOutputTokens
    ) {
        this(new HttpClientResponsesTransport(client), Thread::sleep, baseUrl, apiKey, model,
                reasoningEffort, maxOutputTokens, 600, 0);
    }

    OpenAiResponsesSemanticExtractor(
            ResponsesTransport transport,
            String baseUrl,
            String apiKey,
            String model,
            String reasoningEffort,
            int maxOutputTokens
    ) {
        this(transport, Thread::sleep, baseUrl, apiKey, model, reasoningEffort, maxOutputTokens, 600, 0);
    }

    OpenAiResponsesSemanticExtractor(
            ResponsesTransport transport,
            RetrySleeper retrySleeper,
            String baseUrl,
            String apiKey,
            String model,
            String reasoningEffort,
            int maxOutputTokens,
            int requestTimeoutSeconds,
            int maxTransportRetries
    ) {
        this.transport = transport;
        this.retrySleeper = retrySleeper;
        this.responsesEndpoint = responsesEndpoint(baseUrl);
        this.apiKey = apiKey == null ? "" : apiKey;
        this.model = model == null || model.isBlank() ? "gpt-5.6-sol" : model;
        this.reasoningEffort = reasoningEffort == null || reasoningEffort.isBlank() ? "xhigh" : reasoningEffort;
        if (maxOutputTokens <= 0) {
            throw new IllegalArgumentException("maxOutputTokens must be positive");
        }
        if (requestTimeoutSeconds <= 0) {
            throw new IllegalArgumentException("requestTimeoutSeconds must be positive");
        }
        if (maxTransportRetries < 0) {
            throw new IllegalArgumentException("maxTransportRetries must be zero or positive");
        }
        this.maxOutputTokens = maxOutputTokens;
        this.requestTimeout = Duration.ofSeconds(requestTimeoutSeconds);
        this.maxTransportRetries = maxTransportRetries;
    }

    public SemanticExtractionResult extract(SemanticExtractionPrompt prompt) {
        if (apiKey.isBlank()) {
            throw new IllegalArgumentException("OpenAI API key is required");
        }
        try {
            String requestBody = JSON.writeValueAsString(request(prompt));
            HttpRequest request = HttpRequest.newBuilder(responsesEndpoint)
                    .timeout(requestTimeout)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
            return sendWithRetry(request, requestBody);
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

    @FunctionalInterface
    interface RetrySleeper {
        void sleep(long millis) throws InterruptedException;
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

    private SemanticExtractionResult sendWithRetry(HttpRequest request, String requestBody)
            throws IOException, InterruptedException {
        int attempt = 0;
        while (true) {
            try {
                TransportResponse response = transport.send(request);
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    JsonNode responseJson = parseJson(response.body());
                    return new SemanticExtractionResult(
                            requestBody, response.body(), outputText(responseJson), responseJson, attempt + 1);
                }
                if (!retryable(response.statusCode()) || attempt >= maxTransportRetries) {
                    throw new IllegalArgumentException(
                            "OpenAI Responses API failed with HTTP " + response.statusCode());
                }
            } catch (IOException error) {
                if (attempt >= maxTransportRetries) {
                    throw error;
                }
            }
            retrySleeper.sleep(backoffMillis(attempt));
            attempt++;
        }
    }

    private boolean retryable(int statusCode) {
        return statusCode == 429 || statusCode >= 500 && statusCode <= 599;
    }

    private long backoffMillis(int attempt) {
        return Math.min(2000L, 250L << Math.min(attempt, 3));
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
