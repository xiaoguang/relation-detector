package com.relationdetector.semantic.extract;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

/** Loads semantic extraction config from YAML or JSON. */
public final class SemanticExtractionConfigLoader {
    private static final YAMLMapper YAML = new YAMLMapper();

    public SemanticExtractionConfig load(Path path) {
        if (path == null || !Files.isRegularFile(path)) {
            throw new IllegalArgumentException("semantic extraction config file does not exist: " + path);
        }
        try {
            JsonNode root = YAML.readTree(path.toFile());
            return from(root);
        } catch (IOException e) {
            throw new IllegalArgumentException("failed to read semantic extraction config: " + path, e);
        }
    }

    public SemanticExtractionConfig from(JsonNode root) {
        if (root == null || !root.isObject()) {
            return SemanticExtractionConfig.defaults();
        }
        JsonNode extract = root.has("semanticExtraction") ? root.path("semanticExtraction") : root;
        List<Path> inputs = new ArrayList<>();
        JsonNode inputNode = extract.path("inputs");
        if (inputNode.isArray()) {
            inputNode.forEach(item -> addPath(inputs, item.asText("")));
        }
        addPath(inputs, extract.path("input").asText(""));
        Path output = pathOrNull(extract.path("output").asText(""));
        return new SemanticExtractionConfig(
                extract.path("provider").asText("codex-session"),
                inputs,
                output,
                extract.path("focus").asText(""),
                extract.path("model").asText("gpt-5.5"),
                extract.path("reasoningEffort").asText(extract.path("reasoning-effort").asText("high")),
                extract.path("maxOutputTokens").asInt(extract.path("max-output-tokens").asInt(12000)),
                extract.path("baseUrl").asText(extract.path("base-url").asText("https://api.openai.com/v1")),
                extract.path("apiKeyEnv").asText(extract.path("api-key-env").asText("OPENAI_API_KEY")),
                extract.path("maxRelationships").asInt(extract.path("max-relationships").asInt(0)),
                extract.path("maxLineage").asInt(extract.path("max-lineage").asInt(0)),
                extract.path("maxNamingEvidence").asInt(extract.path("max-naming").asInt(0)),
                extract.path("requestOnly").asBoolean(extract.path("request-only").asBoolean(false)));
    }

    private void addPath(List<Path> paths, String value) {
        if (value != null && !value.isBlank()) {
            paths.add(Path.of(value));
        }
    }

    private Path pathOrNull(String value) {
        return value == null || value.isBlank() ? null : Path.of(value);
    }
}
