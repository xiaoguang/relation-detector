package com.relationdetector.core.fullgrammar;

import java.util.List;

/**
 * full-grammar expression visitor 产生的轻量分析结果。
 *
 * <p>CN: 它把表达式 visitor 识别到的 source alias/column、transform 和 flow kind
 * 传给结构事件；真正的 Data Lineage 候选、置信度和去重仍由 core lineage 层完成。</p>
 *
 * <p>EN: This is the small payload produced by a full-grammar expression
 * visitor. Candidate creation, confidence, and deduplication remain in the core
 * lineage layer.</p>
 */
public record FullGrammarExpressionAnalysis(
        List<String> sourceAliases,
        List<String> sourceColumns,
        String transformType,
        String flowKind
) {
    /**
     *
     * Returns whether the expression contains at least one physical-looking source column.
     *
     * <p>CN: 只说明 visitor 找到了字段来源；不代表该字段一定会进入最终 lineage。</p>
     */
    public boolean hasSources() {
        return !sourceColumns.isEmpty();
    }

    /**
     *
     * Keeps the first source for expression forms whose v1 semantics are intentionally conservative.
     *
     * <p>CN: 某些 COALESCE/derived 场景 v1 只接受第一个可解析物理来源，避免未经审核的额外血缘。</p>
     */
    public FullGrammarExpressionAnalysis firstSourceOnly() {
        if (!hasSources()) {
            return this;
        }
        return new FullGrammarExpressionAnalysis(
                List.of(sourceAliases.get(0)),
                List.of(sourceColumns.get(0)),
                transformType,
                flowKind);
    }

    public FullGrammarExpressionAnalysis withTransform(String transformType, String flowKind) {
        return new FullGrammarExpressionAnalysis(sourceAliases, sourceColumns, transformType, flowKind);
    }
}
