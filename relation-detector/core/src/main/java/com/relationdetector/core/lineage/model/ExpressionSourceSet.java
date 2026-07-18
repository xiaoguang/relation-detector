package com.relationdetector.core.lineage.model;

import java.util.List;

import com.relationdetector.contracts.model.Endpoint;

/**
 * CN: 这是写入表达式中已规范化的物理 source columns 与 transform 中间模型。
 * 当前先作为公共结构落地，不改变现有 lineage 规则。
 * EN: Carries normalized physical source columns and transform classification found inside a write expression.
 */
public record ExpressionSourceSet(
        List<Endpoint> sources,
        String transformType
) {
}
