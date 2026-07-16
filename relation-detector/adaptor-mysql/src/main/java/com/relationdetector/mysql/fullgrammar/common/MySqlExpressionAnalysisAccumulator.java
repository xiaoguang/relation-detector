package com.relationdetector.mysql.fullgrammar.common;

import java.util.List;
import java.util.Set;

import com.relationdetector.core.fullgrammar.FullGrammarExpressionAnalysis;

/**
 *
 * Stable de-duplication for MySQL expression source analyses.
 */
final class MySqlExpressionAnalysisAccumulator {
    private MySqlExpressionAnalysisAccumulator() {
    }

    static void append(
            List<String> aliases,
            List<String> columns,
            Set<String> seen,
            FullGrammarExpressionAnalysis analysis
    ) {
        int count = Math.min(analysis.sourceAliases().size(), analysis.sourceColumns().size());
        for (int index = 0; index < count; index++) {
            String alias = analysis.sourceAliases().get(index);
            String column = analysis.sourceColumns().get(index);
            if (seen.add(alias + "\u0000" + column)) {
                aliases.add(alias);
                columns.add(column);
            }
        }
    }
}
