package com.relationdetector.sqlserver.fullgrammar.common;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.relationdetector.contracts.Enums.LineageTransformType;
import com.relationdetector.core.fullgrammar.FullGrammarExpressionAnalysis;

final class SqlServerTransformState {
    boolean aggregate;
    boolean arithmetic;
    boolean coalesce;
    boolean concatFormat;
    boolean function;
}

final class SqlServerCaseAccumulator {
    private final List<String> valueAliases = new ArrayList<>();
    private final List<String> valueColumns = new ArrayList<>();
    private final Set<String> valueKeys = new LinkedHashSet<>();
    private final List<String> controlAliases = new ArrayList<>();
    private final List<String> controlColumns = new ArrayList<>();
    private final Set<String> controlKeys = new LinkedHashSet<>();

    void addValue(FullGrammarExpressionAnalysis analysis) {
        append(valueAliases, valueColumns, valueKeys, analysis);
    }

    void addControl(FullGrammarExpressionAnalysis analysis) {
        append(controlAliases, controlColumns, controlKeys, analysis);
    }

    boolean hasValues() {
        return !valueColumns.isEmpty();
    }

    boolean hasControls() {
        return !controlColumns.isEmpty();
    }

    FullGrammarExpressionAnalysis value(LineageTransformType transform) {
        return new FullGrammarExpressionAnalysis(valueAliases, valueColumns, transform.name(), "VALUE");
    }

    FullGrammarExpressionAnalysis control() {
        return new FullGrammarExpressionAnalysis(controlAliases, controlColumns, "CASE_WHEN", "CONTROL");
    }

    private static void append(
            List<String> aliases,
            List<String> columns,
            Set<String> seen,
            FullGrammarExpressionAnalysis analysis
    ) {
        if (analysis == null) {
            return;
        }
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
