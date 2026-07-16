package com.relationdetector.core.lineage.model;

import java.util.List;

import com.relationdetector.contracts.model.Endpoint;

/**
 *
 * Normalized source columns found inside a write expression.
 *
 * <p>CN: 这是后续收敛 token-event/full-grammar expression analyzer 的小型中间模型。
 * 当前先作为公共结构落地，不改变现有 lineage 规则。
 */
public record ExpressionSourceSet(
        List<Endpoint> sources,
        String transformType
) {
}
