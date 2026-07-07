package com.relationdetector.semantic.extract;

import com.fasterxml.jackson.databind.JsonNode;

/** Result of an LLM semantic extraction request. */
public record SemanticExtractionResult(
        String requestJson,
        String responseJson,
        String outputText,
        JsonNode response
) {
    public SemanticExtractionResult {
        if (requestJson == null || requestJson.isBlank()) {
            throw new IllegalArgumentException("requestJson is required");
        }
        if (responseJson == null || responseJson.isBlank()) {
            throw new IllegalArgumentException("responseJson is required");
        }
        if (outputText == null) {
            outputText = "";
        }
        if (response == null || response.isNull()) {
            throw new IllegalArgumentException("response is required");
        }
    }
}
