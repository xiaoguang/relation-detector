package com.relationdetector.mysql.fullgrammar.common;

import com.relationdetector.contracts.Enums.LineageTransformType;
import com.relationdetector.core.fullgrammar.FullGrammarExpressionAnalysis;
import com.relationdetector.core.lineage.LineageTransformClassifier;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * CN: 统一 MySQL 5.7/8.0 expression 的 transform 分类优先级，输入 typed expression flags，输出稳定 LineageTransformType；不遍历 parse tree 或持有状态。
 * EN: Defines transform-classification precedence shared by MySQL 5.7 and 8.0. It maps typed expression flags to stable LineageTransformType values without traversing trees or retaining state.
 */
public final class MySqlTransformSemantics {
    private MySqlTransformSemantics() {
    }

    public static String valueTransform(String currentTransform, boolean containsArithmetic) {
        LineageTransformType current = LineageTransformType.valueOf(currentTransform);
        return containsArithmetic
                ? LineageTransformClassifier.dominant(current, LineageTransformType.ARITHMETIC).name()
                : current.name();
    }

    public static FullGrammarExpressionAnalysis mergeSameRole(
            List<FullGrammarExpressionAnalysis> analyses,
            String transformType,
            String flowKind
    ) {
        List<String> aliases = new ArrayList<>();
        List<String> columns = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (FullGrammarExpressionAnalysis analysis : analyses) {
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
        return new FullGrammarExpressionAnalysis(aliases, columns, transformType, flowKind);
    }
}
