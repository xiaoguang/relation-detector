package com.relationdetector.semantic.extract;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * CN: 按固定文件名写出单次模型请求所需的 evidence bundle、prompt 和 transport request；输入来自已验证
 * extraction prompt，输出供 run writer 或独立 E2E 命令使用，不负责模型调用、响应归一化或发布事务。
 * EN: Writes the evidence bundle, prompt, and transport request for one model request under fixed filenames. It
 * consumes a validated extraction prompt for run writers or standalone E2E commands and does not call models,
 * normalize responses, or publish runs.
 */
public final class SemanticRequestArtifactWriter {
    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private final SemanticModelOutputSchema outputSchema = new SemanticModelOutputSchema();

    public void writeRequestOnly(Path outputDirectory, SemanticExtractionPrompt prompt, String requestJson) {
        if (requestJson == null || requestJson.isBlank()) {
            throw new IllegalArgumentException("requestJson is required");
        }
        createDirectory(outputDirectory);
        writeJson(outputDirectory.resolve("semantic-extraction-evidence-bundle.json"),
                prompt.trustedEvidenceBundle());
        write(outputDirectory.resolve("semantic-extraction-prompt.md"), promptMarkdown(prompt));
        write(outputDirectory.resolve("semantic-extraction-request.json"), requestJson);
    }

    public void writeCodexSessionRequest(Path outputDirectory, SemanticExtractionPrompt prompt) {
        writeCodexSessionRequest(
                outputDirectory,
                prompt,
                "semantic-extraction-result.json",
                SemanticModelOutputSchema.EXTRACTION_FILE,
                outputSchema.extraction());
    }

    void writeCodexSessionReconciliationRequest(
            Path outputDirectory,
            SemanticExtractionPrompt prompt,
            String resultFileName
    ) {
        writeCodexSessionRequest(
                outputDirectory,
                prompt,
                resultFileName,
                SemanticModelOutputSchema.RECONCILIATION_FILE,
                outputSchema.reconciliation());
    }

    private void writeCodexSessionRequest(
            Path outputDirectory,
            SemanticExtractionPrompt prompt,
            String resultFileName,
            String schemaFileName,
            ObjectNode schema
    ) {
        if (resultFileName == null || resultFileName.isBlank()
                || !Path.of(resultFileName).getFileName().toString().equals(resultFileName)) {
            throw new IllegalArgumentException("Codex session result file name is invalid");
        }
        createDirectory(outputDirectory);
        writeJson(outputDirectory.resolve("semantic-extraction-evidence-bundle.json"),
                prompt.trustedEvidenceBundle());
        write(outputDirectory.resolve("semantic-extraction-prompt.md"), promptMarkdown(prompt));
        writeJson(outputDirectory.resolve(schemaFileName), schema);
        write(outputDirectory.resolve("semantic-extraction-codex-session.md"),
                codexSessionMarkdown(outputDirectory, resultFileName, schemaFileName));
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

    private String codexSessionMarkdown(Path outputDirectory, String resultFileName, String schemaFileName) {
        String responsePath = responsePathHint(outputDirectory, resultFileName);
        return """
                # Codex Session Semantic Extraction

                This artifact is for no-API Codex-session testing.

                It does not call an external LLM provider and does not require `OPENAI_API_KEY`.
                Keep this request run immutable. Paste or provide `semantic-extraction-prompt.md` to the current Codex
                session, then save the generated JSON in a separate response directory as:

                `%s`

                The response must conform exactly to `%s`. When invoking Codex CLI from this request artifact
                directory, pass the schema with
                `--output-schema %s`.

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
                """.formatted(
                responsePath,
                schemaFileName,
                schemaFileName);
    }

    private String responsePathHint(Path outputDirectory, String resultFileName) {
        Path name = outputDirectory.getFileName();
        if (name != null && name.toString().startsWith("shard-")) {
            return "responses/shards/" + name + "/" + resultFileName;
        }
        Path parent = outputDirectory.getParent();
        if (name != null && "template".equals(name.toString())
                && parent != null && parent.getFileName() != null
                && "reconciliation".equals(parent.getFileName().toString())) {
            return "responses/reconciliation/" + resultFileName;
        }
        return "responses/" + resultFileName;
    }

    private void writeJson(Path path, Object value) {
        try {
            JSON.writeValue(path.toFile(), value);
        } catch (IOException e) {
            throw new IllegalArgumentException("failed to write semantic extraction JSON artifact: " + path, e);
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
