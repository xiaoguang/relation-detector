package com.relationdetector.semantic.extract;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * CN: 将 developer/user prompts 与不可缺失的 evidence-closed bundle 绑定为一个请求值；构造器拒绝空 prompt 或无 bundle 的正式抽取。
 * EN: Binds developer and user prompts to a required evidence-closed bundle as one request value. Construction rejects empty prompts and bundle-free formal extraction.
 */
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
        evidenceBundle = evidenceBundle.deepCopy();
    }

    @Override
    public JsonNode evidenceBundle() {
        return evidenceBundle.deepCopy();
    }

    JsonNode trustedEvidenceBundle() {
        return evidenceBundle;
    }
}
