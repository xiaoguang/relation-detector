package com.relationdetector.semantic.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.relationdetector.semantic.extract.SemanticBoundedJsonReader;
import com.relationdetector.semantic.extract.SemanticEvidenceBundleSliceReader;
import com.relationdetector.semantic.extract.SemanticExtractionDocumentNormalizer;

/**
 * CN: 执行 normalize-extraction 命令并原子组装正式语义文档；输入是模型结果和 evidence bundle，输出规范 JSON，禁止在缺少证据时补造事实。
 * EN: Executes normalize-extraction from a model result and evidence bundle into formal JSON; it must never invent facts when evidence is absent.
 */
final class SemanticNormalizeExtractionCommandHandler {
    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    SemanticCliExitCode execute(SemanticCommandArguments arguments) {
        Path temporary = null;
        try {
            JsonNode raw = new SemanticBoundedJsonReader().readObject(
                    arguments.inputs().get(0),
                    arguments.maxOutputTokens(),
                    "semantic model result");
            JsonNode evidenceBundle = new SemanticEvidenceBundleSliceReader()
                    .read(arguments.evidenceBundle(), raw, arguments.sharding().maxInputTokens());
            JsonNode normalized = new SemanticExtractionDocumentNormalizer()
                    .normalizeOwnedShard(raw, evidenceBundle);
            Path output = arguments.output().toAbsolutePath().normalize();
            Path parent = output.getParent();
            Files.createDirectories(parent);
            temporary = Files.createTempFile(parent, "." + output.getFileName() + ".", ".tmp");
            JSON.writeValue(temporary.toFile(), normalized);
            Files.move(
                    temporary,
                    output,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            temporary = null;
            return SemanticCliExitCode.SUCCESS;
        } catch (IOException e) {
            throw new IllegalArgumentException("failed to normalize semantic extraction result", e);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // The command already failed; no partially published output is exposed.
                }
            }
        }
    }
}
