package com.relationdetector.semantic.extract.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * CN: 定位一个 semantic section 字段的 validation failure，保留 owner id、值、期望引用类型和安全原因；不携带完整模型 prompt。
 * EN: Locates one validation failure at a semantic section field with owner id, value, expected reference kind, and safe reason, without carrying the full model prompt.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SemanticValidationIssue(
        String section,
        String id,
        String field,
        String value,
        String expectedRefKind,
        String reason
) {
}
