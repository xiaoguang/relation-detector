package com.relationdetector.core.lineage.model;

import com.relationdetector.contracts.model.Endpoint;

/**
 * CN: 承载 INSERT/UPDATE/MERGE lineage 的 normalized target-to-expression source mapping 及 mapping kind。
 * EN: Carries normalized target-to-expression-source mapping and mapping kind for INSERT, UPDATE, and MERGE lineage.
 */
public record AssignmentMapping(
        Endpoint target,
        ExpressionSourceSet expressionSources,
        String mappingKind
) {
}
