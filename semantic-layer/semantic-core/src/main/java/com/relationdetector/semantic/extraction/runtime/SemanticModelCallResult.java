package com.relationdetector.semantic.extraction.runtime;

import com.relationdetector.semantic.extraction.artifact.SemanticArtifactRef;

/**
 * CN: 仅通过文件引用与整数 usage 描述一次模型调用结果；不跨模型边界携带 JSON tree 或完整字符串。
 * EN: Describes one model call using file references and integer usage only. No JSON tree or full request, response,
 * or output string crosses the model boundary.
 */
public record SemanticModelCallResult(
        SemanticArtifactRef request,
        SemanticArtifactRef response,
        SemanticArtifactRef output,
        int inputTokens,
        int outputTokens,
        int transportAttempts
) {
    public SemanticModelCallResult {
        if (request == null || response == null || output == null) {
            throw new IllegalArgumentException("semantic model call artifacts are required");
        }
    }
}
