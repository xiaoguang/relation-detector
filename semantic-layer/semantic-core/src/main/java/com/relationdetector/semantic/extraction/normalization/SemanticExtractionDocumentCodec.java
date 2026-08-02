package com.relationdetector.semantic.extraction.normalization;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relationdetector.semantic.extraction.model.SemanticExtractionDocument;

/**
 * CN: 作为模型JSON与typed semantic document之间唯一的Jackson转换边界；输入是已通过大小门限的有界JsonNode，
 * 输出是section初始化完成的typed文档或规范ObjectNode。上游是bounded reader，下游是normalizer/graph assembler；
 * 本类不校验owner/evidence closure，也不读取文件。
 *
 * <p>EN: Sole Jackson conversion boundary between bounded model JSON and the typed semantic document. It returns a
 * typed document with initialized sections or a normalized ObjectNode. Bounded readers are upstream and normalization
 * plus graph assembly are downstream; it does not validate ownership/evidence closure or read files.
 */
public final class SemanticExtractionDocumentCodec {
    private static final ObjectMapper JSON = new ObjectMapper();

    public SemanticExtractionDocument read(JsonNode source) {
        if (source == null || !source.isObject()) {
            throw new IllegalArgumentException("semantic extraction document must be a JSON object");
        }
        try {
            SemanticExtractionDocument document = JSON.treeToValue(source, SemanticExtractionDocument.class);
            document.ensureSections();
            return document;
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("failed to read semantic extraction document", e);
        }
    }

    public ObjectNode write(SemanticExtractionDocument document) {
        return JSON.valueToTree(document);
    }
}
