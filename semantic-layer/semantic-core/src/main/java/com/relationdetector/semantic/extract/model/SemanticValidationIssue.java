package com.relationdetector.semantic.extract.model;

import com.fasterxml.jackson.annotation.JsonInclude;

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
