package com.relationdetector.semantic.extract;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relationdetector.semantic.extract.model.SemanticExtractionDocument;

/** Jackson boundary between LLM JSON and the typed normalization model. */
final class SemanticExtractionDocumentCodec {
    private static final ObjectMapper JSON = new ObjectMapper();

    SemanticExtractionDocument read(JsonNode source) {
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

    ObjectNode write(SemanticExtractionDocument document) {
        return JSON.valueToTree(document);
    }
}
