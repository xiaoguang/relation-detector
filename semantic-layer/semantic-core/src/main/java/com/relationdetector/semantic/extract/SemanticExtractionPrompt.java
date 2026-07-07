package com.relationdetector.semantic.extract;

import com.fasterxml.jackson.databind.JsonNode;

/** Prompt package for LLM-assisted semantic extraction from a compact evidence bundle. */
public record SemanticExtractionPrompt(
        String developerPrompt,
        String userPrompt,
        JsonNode evidenceBundle
) {
    public SemanticExtractionPrompt {
        if (developerPrompt == null || developerPrompt.isBlank()) {
            throw new IllegalArgumentException("developerPrompt is required");
        }
        if (userPrompt == null || userPrompt.isBlank()) {
            throw new IllegalArgumentException("userPrompt is required");
        }
        if (evidenceBundle == null || evidenceBundle.isMissingNode() || evidenceBundle.isNull()) {
            throw new IllegalArgumentException("evidenceBundle is required");
        }
    }
}
