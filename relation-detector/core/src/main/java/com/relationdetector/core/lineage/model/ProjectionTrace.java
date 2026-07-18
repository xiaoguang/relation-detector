package com.relationdetector.core.lineage.model;

import java.util.List;

import com.relationdetector.contracts.Enums.LineageFlowKind;
import com.relationdetector.contracts.Enums.LineageTransformType;
import com.relationdetector.contracts.model.Endpoint;

/**
 * CN: 承载 CTE/derived-table lineage 使用的 projection endpoint、物理 sources、flow 与 transform。
 * EN: Carries projection endpoint, physical sources, flow, and transform for CTE and derived-table lineage.
 */
public record ProjectionTrace(
        Endpoint projection,
        List<Endpoint> sources,
        LineageTransformType transform,
        LineageFlowKind flowKind
) {
}
