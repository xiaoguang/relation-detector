package com.relationdetector.semantic.extraction.runtime;

import com.relationdetector.semantic.extraction.artifact.SemanticArtifactRef;
import com.relationdetector.semantic.extraction.normalization.SemanticBoundedJsonReader;
import com.relationdetector.semantic.extraction.normalization.SemanticExtractionValidationException;
import com.relationdetector.semantic.extraction.prompt.SemanticExtractionPrompt;
import com.relationdetector.semantic.internal.io.SemanticAtomicFiles;
import com.relationdetector.semantic.internal.io.SemanticFileDigest;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * CN: 以文件边界调用 OpenAI Responses API；成功 envelope 有界流式落盘，错误 body 永不物化或回显。
 * EN: Calls the OpenAI Responses API through a file-backed boundary. Successful envelopes stream to a bounded file;
 * error bodies are never materialized or included in exceptions, and every retry closes its response stream.
 */
public final class OpenAiResponsesSemanticExtractor implements SemanticModelClient {
    private static final int COPY_BUFFER_BYTES = 64 * 1024;
    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private final ResponsesTransport transport;
    private final URI responsesEndpoint;
    private final String apiKey;
    private final String model;
    private final String reasoningEffort;
    private final Duration requestTimeout;
    private final int maxTransportRetries;
    private final RetrySleeper retrySleeper;
    private final SemanticBoundedJsonReader bounded = new SemanticBoundedJsonReader();

    public OpenAiResponsesSemanticExtractor(
            String baseUrl,
            String apiKey,
            String model,
            String reasoningEffort,
            int requestTimeoutSeconds,
            int maxTransportRetries
    ) {
        this(new HttpClientResponsesTransport(
                        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build()),
                Thread::sleep, baseUrl, apiKey, model, reasoningEffort,
                requestTimeoutSeconds, maxTransportRetries);
    }

    OpenAiResponsesSemanticExtractor(
            ResponsesTransport transport,
            RetrySleeper retrySleeper,
            String baseUrl,
            String apiKey,
            String model,
            String reasoningEffort,
            int requestTimeoutSeconds,
            int maxTransportRetries
    ) {
        if (transport == null || retrySleeper == null) {
            throw new IllegalArgumentException("OpenAI transport and retry sleeper are required");
        }
        this.transport = transport;
        this.retrySleeper = retrySleeper;
        this.responsesEndpoint = responsesEndpoint(baseUrl);
        this.apiKey = apiKey == null ? "" : apiKey;
        this.model = model == null || model.isBlank() ? "gpt-5.6-sol" : model;
        this.reasoningEffort = reasoningEffort == null || reasoningEffort.isBlank() ? "xhigh" : reasoningEffort;
        if (requestTimeoutSeconds <= 0) {
            throw new IllegalArgumentException("requestTimeoutSeconds must be positive");
        }
        if (maxTransportRetries < 0) {
            throw new IllegalArgumentException("maxTransportRetries must be zero or positive");
        }
        this.requestTimeout = Duration.ofSeconds(requestTimeoutSeconds);
        this.maxTransportRetries = maxTransportRetries;
    }

    @Override
    public SemanticModelCallResult extract(
            SemanticExtractionPrompt prompt,
            SemanticModelCallContext context
    ) {
        if (prompt == null || context == null) {
            throw new IllegalArgumentException("semantic extraction prompt and call context are required");
        }
        if (apiKey.isBlank()) {
            throw new IllegalArgumentException("OpenAI API key is required");
        }
        try {
            Files.createDirectories(context.scratchRoot());
            writeJson(context.requestPath(), request(prompt, context.maxOutputTokens()));
            HttpRequest request = HttpRequest.newBuilder(responsesEndpoint)
                    .timeout(requestTimeout)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofFile(context.requestPath()))
                    .build();
            int attempts = sendWithRetry(request, context);
            ObjectNode envelope = bounded.readObject(
                    context.responsePath(),
                    new SemanticBoundedJsonReader.Limits(
                            context.responseEnvelopeByteLimit(),
                            responseTokenLimit(context.maxOutputTokens())),
                    "OpenAI response envelope");
            writeOutput(context, outputText(envelope));
            ObjectNode output = bounded.readObject(
                    context.outputPath(),
                    new SemanticBoundedJsonReader.Limits(
                            context.outputByteLimit(), context.maxOutputTokens()),
                    "OpenAI semantic output");
            if (output == null) {
                throw new SemanticExtractionValidationException(
                        "OpenAI semantic output must contain exactly one JSON object");
            }
            return new SemanticModelCallResult(
                    reference(context.requestPath()),
                    reference(context.responsePath()),
                    reference(context.outputPath()),
                    usage(envelope, "input_tokens"),
                    usage(envelope, "output_tokens"),
                    attempts);
        } catch (SemanticExtractionValidationException failure) {
            throw failure;
        } catch (IOException failure) {
            throw new IllegalArgumentException("failed to call OpenAI Responses API", failure);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException("OpenAI Responses API call interrupted", failure);
        }
    }

    public SemanticArtifactRef renderRequest(
            SemanticExtractionPrompt prompt,
            Path target,
            int maxOutputTokens
    ) {
        if (prompt == null || target == null || maxOutputTokens <= 0) {
            throw new IllegalArgumentException(
                    "semantic request prompt, target and output limit are required");
        }
        try {
            writeJson(target.toAbsolutePath().normalize(), request(prompt, maxOutputTokens));
            return reference(target.toAbsolutePath().normalize());
        } catch (IOException failure) {
            throw new IllegalArgumentException("failed to render OpenAI request", failure);
        }
    }

    @FunctionalInterface
    public interface ResponsesTransport {
        TransportResponse send(HttpRequest request) throws IOException, InterruptedException;
    }

    @FunctionalInterface
    public interface RetrySleeper {
        void sleep(long millis) throws InterruptedException;
    }

    public record TransportResponse(int statusCode, InputStream body) {
        public TransportResponse {
            if (body == null) {
                throw new IllegalArgumentException("OpenAI transport response body stream is required");
            }
        }
    }

    private record HttpClientResponsesTransport(HttpClient client) implements ResponsesTransport {
        @Override
        public TransportResponse send(HttpRequest request) throws IOException, InterruptedException {
            HttpResponse<InputStream> response = client.send(
                    request, HttpResponse.BodyHandlers.ofInputStream());
            return new TransportResponse(response.statusCode(), response.body());
        }
    }

    private int sendWithRetry(HttpRequest request, SemanticModelCallContext context)
            throws IOException, InterruptedException {
        int attempt = 0;
        while (true) {
            try {
                TransportResponse response = transport.send(request);
                try (InputStream body = response.body()) {
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        SemanticAtomicFiles.replace(
                                context.responsePath(),
                                temporary -> copyBounded(
                                        body, temporary, context.responseEnvelopeByteLimit()));
                        return attempt + 1;
                    }
                    if (!retryable(response.statusCode()) || attempt >= maxTransportRetries) {
                        throw new IllegalArgumentException(
                                "OpenAI Responses API failed with HTTP " + response.statusCode());
                    }
                }
            } catch (IOException failure) {
                if (attempt >= maxTransportRetries) {
                    throw failure;
                }
            }
            retrySleeper.sleep(backoffMillis(attempt));
            attempt++;
        }
    }

    private void copyBounded(InputStream input, Path target, long maxBytes) throws IOException {
        long bytes = 0;
        try (OutputStream output = Files.newOutputStream(
                target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            byte[] buffer = new byte[COPY_BUFFER_BYTES];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                bytes = Math.addExact(bytes, read);
                if (bytes > maxBytes) {
                    throw new IOException("OpenAI response envelope exceeds configured byte limit");
                }
                output.write(buffer, 0, read);
            }
        }
    }

    private void writeJson(Path target, JsonNode value) throws IOException {
        SemanticAtomicFiles.replace(target, temporary -> JSON.writeValue(temporary.toFile(), value));
    }

    private void writeOutput(SemanticModelCallContext context, String value) throws IOException {
        SemanticAtomicFiles.replace(
                context.outputPath(),
                temporary -> Files.writeString(
                        temporary,
                        value,
                        StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE));
    }

    private SemanticArtifactRef reference(Path path) throws IOException {
        SemanticFileDigest.Digest digest = SemanticFileDigest.computeNoFollow(path);
        return new SemanticArtifactRef(path, digest.bytes(), digest.sha256());
    }

    private int usage(ObjectNode envelope, String field) {
        JsonNode value = envelope.path("usage").path(field);
        if (value.isMissingNode() || value.isNull()) {
            return 0;
        }
        if (!value.isIntegralNumber() || !value.canConvertToInt() || value.intValue() < 0) {
            throw new SemanticExtractionValidationException(
                    "OpenAI response usage is invalid");
        }
        return value.intValue();
    }

    private int responseTokenLimit(int maxOutputTokens) {
        long result = Math.addExact((long) maxOutputTokens, 262_144L);
        return result > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) result;
    }

    private boolean retryable(int statusCode) {
        return statusCode == 429 || statusCode >= 500 && statusCode <= 599;
    }

    private long backoffMillis(int attempt) {
        return Math.min(2000L, 250L << Math.min(attempt, 3));
    }

    private ObjectNode request(SemanticExtractionPrompt prompt, int maxOutputTokens) {
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
        ObjectNode textNode = message.putArray("content").addObject();
        textNode.put("type", "input_text");
        textNode.put("text", text);
        return message;
    }

    private URI responsesEndpoint(String baseUrl) {
        String resolved = baseUrl == null || baseUrl.isBlank()
                ? "https://api.openai.com/v1"
                : baseUrl;
        String trimmed = resolved.endsWith("/")
                ? resolved.substring(0, resolved.length() - 1)
                : resolved;
        return URI.create(trimmed.endsWith("/responses") ? trimmed : trimmed + "/responses");
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
