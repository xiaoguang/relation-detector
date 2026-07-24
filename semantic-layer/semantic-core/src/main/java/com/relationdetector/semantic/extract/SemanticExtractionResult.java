package com.relationdetector.semantic.extract;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * CN: 保存一次成功 LLM extraction 的 request JSON、response JSON、output text 和 parsed response，供 artifact writer 审计；失败请求不构造该对象。
 * EN: Carries request JSON, response JSON, output text, and parsed response from one successful LLM extraction for artifact auditing. Failed requests do not construct this value.
 */
public record SemanticExtractionResult(
        String requestJson,
        String responseJson,
        String outputText,
        JsonNode response,
        int transportAttempts
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
        if (transportAttempts <= 0) {
            transportAttempts = 1;
        }
        response = response.deepCopy();
    }

    public SemanticExtractionResult(
            String requestJson,
            String responseJson,
            String outputText,
            JsonNode response
    ) {
        this(requestJson, responseJson, outputText, response, 1);
    }

    public int inputTokens() {
        return response.path("usage").path("input_tokens").asInt(0);
    }

    public int outputTokens() {
        return response.path("usage").path("output_tokens").asInt(0);
    }

    @Override
    public JsonNode response() {
        return response.deepCopy();
    }

    JsonNode trustedResponse() {
        return response;
    }
}
