package com.relationdetector.semantic.extract;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Writes prompt, evidence bundle, request, response, and extracted semantic document artifacts. */
public final class SemanticExtractionArtifactWriter {
    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private final SemanticExtractionDocumentNormalizer normalizer = new SemanticExtractionDocumentNormalizer();

    public void writeRequestOnly(Path outputDirectory, SemanticExtractionPrompt prompt, String requestJson) {
        if (requestJson == null || requestJson.isBlank()) {
            throw new IllegalArgumentException("requestJson is required");
        }
        createDirectory(outputDirectory);
        write(outputDirectory.resolve("semantic-extraction-evidence-bundle.json"), pretty(prompt.evidenceBundle()));
        write(outputDirectory.resolve("semantic-extraction-prompt.md"), promptMarkdown(prompt));
        write(outputDirectory.resolve("semantic-extraction-request.json"), requestJson);
    }

    public void writeCodexSessionRequest(Path outputDirectory, SemanticExtractionPrompt prompt) {
        createDirectory(outputDirectory);
        write(outputDirectory.resolve("semantic-extraction-evidence-bundle.json"), pretty(prompt.evidenceBundle()));
        write(outputDirectory.resolve("semantic-extraction-prompt.md"), promptMarkdown(prompt));
        write(outputDirectory.resolve("semantic-extraction-codex-session.md"), codexSessionMarkdown(outputDirectory));
    }

    public void writeResult(Path outputDirectory, SemanticExtractionPrompt prompt, SemanticExtractionResult result) {
        createDirectory(outputDirectory);
        writeRequestOnly(outputDirectory, prompt, result.requestJson());
        write(outputDirectory.resolve("semantic-extraction-response.json"), result.responseJson());
        write(outputDirectory.resolve("semantic-extraction-result-raw.json"), result.outputText());
        write(outputDirectory.resolve("semantic-extraction-result.json"),
                pretty(normalizeResult(result.outputText(), prompt.evidenceBundle())));
    }

    public ObjectNode normalizeResult(String outputText, JsonNode evidenceBundle) {
        try {
            JsonNode raw = JSON.readTree(outputText);
            return normalizer.normalize(raw, evidenceBundle);
        } catch (IOException e) {
            throw new IllegalArgumentException("semantic extraction result must be valid JSON", e);
        }
    }

    private String promptMarkdown(SemanticExtractionPrompt prompt) {
        return """
                # Semantic Extraction Prompt

                ## Developer Prompt

                ```text
                %s
                ```

                ## User Prompt

                ```text
                %s
                ```
                """.formatted(prompt.developerPrompt(), prompt.userPrompt());
    }

    private String codexSessionMarkdown(Path outputDirectory) {
        return """
                # Codex Session Semantic Extraction

                This artifact is for no-API Codex-session testing.

                It does not call an external LLM provider and does not require `OPENAI_API_KEY`.
                Paste or provide `semantic-extraction-prompt.md` to the current Codex session, then save the generated
                JSON result as:

                `%s`

                Expected output sections:

                - `entities`
                - `events`
                - `relations`
                - `lineage`
                - `metrics`
                - `dimensions`
                - `triplets`
                - `reviewItems`
                - `semanticGraph`
                - `validation`
                """.formatted(outputDirectory.resolve("semantic-extraction-result.json"));
    }

    private String pretty(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (IOException e) {
            throw new IllegalArgumentException("failed to serialize semantic extraction artifact", e);
        }
    }

    private void createDirectory(Path outputDirectory) {
        if (outputDirectory == null) {
            throw new IllegalArgumentException("output directory is required");
        }
        try {
            Files.createDirectories(outputDirectory);
        } catch (IOException e) {
            throw new IllegalArgumentException("failed to create output directory: " + outputDirectory, e);
        }
    }

    private void write(Path path, String content) {
        try {
            Files.writeString(path, content == null ? "" : content);
        } catch (IOException e) {
            throw new IllegalArgumentException("failed to write semantic extraction artifact: " + path, e);
        }
    }
}
