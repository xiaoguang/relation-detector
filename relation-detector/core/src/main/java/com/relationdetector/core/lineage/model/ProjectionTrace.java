package com.relationdetector.core.lineage.model;

import java.util.List;

import com.relationdetector.contracts.Enums.LineageFlowKind;
import com.relationdetector.contracts.Enums.LineageTransformType;
import com.relationdetector.contracts.model.Endpoint;

/**
 *
 * Projection alias/source trace used by CTE and derived-table lineage.
 */
public record ProjectionTrace(
        Endpoint projection,
        List<Endpoint> sources,
        LineageTransformType transform,
        LineageFlowKind flowKind
) {
}
