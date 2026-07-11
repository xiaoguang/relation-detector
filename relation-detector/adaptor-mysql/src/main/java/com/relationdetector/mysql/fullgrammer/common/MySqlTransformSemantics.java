package com.relationdetector.mysql.fullgrammer.common;

import com.relationdetector.contracts.Enums.LineageTransformType;
import com.relationdetector.core.fullgrammer.FullGrammerExpressionAnalysis;
import com.relationdetector.core.lineage.LineageTransformClassifier;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Stateless transform policy shared by version-specific MySQL context adapters. */
public final class MySqlTransformSemantics {
    private MySqlTransformSemantics() {
    }

    public static String valueTransform(String currentTransform, boolean containsArithmetic) {
        LineageTransformType current = LineageTransformType.valueOf(currentTransform);
        return containsArithmetic
                ? LineageTransformClassifier.dominant(current, LineageTransformType.ARITHMETIC).name()
                : current.name();
    }

    public static FullGrammerExpressionAnalysis mergeSameRole(
            List<FullGrammerExpressionAnalysis> analyses,
            String transformType,
            String flowKind
    ) {
        List<String> aliases = new ArrayList<>();
        List<String> columns = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (FullGrammerExpressionAnalysis analysis : analyses) {
            int size = Math.min(analysis.sourceAliases().size(), analysis.sourceColumns().size());
            for (int index = 0; index < size; index++) {
                String alias = analysis.sourceAliases().get(index);
                String column = analysis.sourceColumns().get(index);
                if (seen.add(alias + "\u0000" + column)) {
                    aliases.add(alias);
                    columns.add(column);
                }
            }
        }
        return new FullGrammerExpressionAnalysis(aliases, columns, transformType, flowKind);
    }
}
