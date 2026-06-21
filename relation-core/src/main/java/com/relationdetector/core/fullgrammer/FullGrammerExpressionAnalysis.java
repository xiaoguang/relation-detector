package com.relationdetector.core.fullgrammer;

import java.util.List;

public record FullGrammerExpressionAnalysis(
        List<String> sourceAliases,
        List<String> sourceColumns,
        String transformType,
        String flowKind
) {
    public boolean hasSources() {
        return !sourceColumns.isEmpty();
    }

    public FullGrammerExpressionAnalysis firstSourceOnly() {
        if (!hasSources()) {
            return this;
        }
        return new FullGrammerExpressionAnalysis(
                List.of(sourceAliases.get(0)),
                List.of(sourceColumns.get(0)),
                transformType,
                flowKind);
    }

    public FullGrammerExpressionAnalysis withTransform(String transformType, String flowKind) {
        return new FullGrammerExpressionAnalysis(sourceAliases, sourceColumns, transformType, flowKind);
    }
}
