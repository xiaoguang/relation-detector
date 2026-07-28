package com.relationdetector.semantic.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.relationdetector.semantic.extract.SemanticEvidenceBundleSliceReader;
import com.relationdetector.semantic.extract.SemanticExtractionDocumentNormalizer;

/**
 * CN: 执行 normalize-extraction 命令并原子组装正式语义文档；输入是模型结果和 evidence bundle，输出规范 JSON，禁止在缺少证据时补造事实。
 * EN: Executes normalize-extraction from a model result and evidence bundle into formal JSON; it must never invent facts when evidence is absent.
 */
final class SemanticNormalizeExtractionCommandHandler {
    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    SemanticCliExitCode execute(SemanticCommandArguments arguments) {
        try {
            JsonNode raw = JSON.readTree(arguments.inputs().get(0).toFile());
            JsonNode evidenceBundle = new SemanticEvidenceBundleSliceReader()
                    .read(arguments.evidenceBundle(), raw);
            JsonNode normalized = new SemanticExtractionDocumentNormalizer()
                    .normalizeOwnedShard(raw, evidenceBundle);
            Path parent = arguments.output().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            JSON.writeValue(arguments.output().toFile(), normalized);
            return SemanticCliExitCode.SUCCESS;
        } catch (IOException e) {
            throw new IllegalArgumentException("failed to normalize semantic extraction result", e);
        }
    }
}
