package com.relationdetector.core.lineage.model;

import com.relationdetector.contracts.model.Endpoint;

/**
 * Normalized target-to-expression mapping for INSERT/UPDATE/MERGE lineage.
 */
public record AssignmentMapping(
        Endpoint target,
        ExpressionSourceSet expressionSources,
        String mappingKind
) {
}
