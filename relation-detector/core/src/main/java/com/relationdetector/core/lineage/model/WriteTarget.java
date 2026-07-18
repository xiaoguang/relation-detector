package com.relationdetector.core.lineage.model;

import com.relationdetector.contracts.model.Endpoint;

/**
 * CN: 承载已规范化的物理 write-target endpoint 及其 statement kind。
 * EN: Carries a normalized physical write-target endpoint and its statement kind.
 */
public record WriteTarget(
        Endpoint endpoint,
        String statementKind
) {
}
