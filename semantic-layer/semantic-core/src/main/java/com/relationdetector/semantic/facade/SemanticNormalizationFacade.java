package com.relationdetector.semantic.facade;

import java.io.IOException;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.relationdetector.semantic.extraction.normalization.SemanticBoundedJsonReader;
import com.relationdetector.semantic.extraction.normalization.SemanticExtractionDocumentNormalizer;
import com.relationdetector.semantic.extraction.shard.SemanticEvidenceBundleSliceReader;
import com.relationdetector.semantic.internal.io.SemanticAtomicFiles;

/**
 * CN: 统一 standalone semantic normalization 入口，在模型结果与 evidence slice 均通过预算和 owner 校验后
 * 原子发布规范文档。输入是两个文件和既有token门限，输出是单个正式JSON；上游是CLI，下游是bounded reader
 * 与normalizer。本类不调用模型、不读取完整scan，也不在失败时留下部分文件。
 *
 * <p>EN: Facade for standalone semantic normalization. It atomically publishes a normalized document only after
 * the model result and evidence slice pass budget and ownership checks. CLI is upstream and bounded readers plus the
 * normalizer are downstream; this facade never calls a model, reads a whole scan, or leaves a partial output.
 */
public final class SemanticNormalizationFacade {
    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public void normalize(
            Path rawResult,
            Path evidenceBundle,
            Path output,
            int maxOutputTokens,
            int maxInputTokens
    ) {
        try {
            JsonNode raw = new SemanticBoundedJsonReader().readObject(
                    rawResult, maxOutputTokens, "semantic model result");
            JsonNode evidence = new SemanticEvidenceBundleSliceReader()
                    .read(evidenceBundle, raw, maxInputTokens);
            JsonNode normalized = new SemanticExtractionDocumentNormalizer()
                    .normalizeOwnedShard(raw, evidence);
            SemanticAtomicFiles.replace(output.toAbsolutePath().normalize(), temporary ->
                    JSON.writeValue(temporary.toFile(), normalized));
        } catch (IOException error) {
            throw new IllegalArgumentException("failed to normalize semantic extraction result", error);
        }
    }
}
