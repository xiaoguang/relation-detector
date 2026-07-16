package com.relationdetector.core.lineage.model;

import com.relationdetector.contracts.model.Endpoint;

/**
 *
 * Normalized write target endpoint plus statement kind.
 */
public record WriteTarget(
        Endpoint endpoint,
        String statementKind
) {
}
