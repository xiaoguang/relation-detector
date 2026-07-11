package com.relationdetector.postgres.fullgrammer.common;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.antlr.v4.runtime.tree.ParseTree;

import com.relationdetector.core.fullgrammer.FullGrammerExpressionAnalysis;
import com.relationdetector.core.fullgrammer.FullGrammerExpressionAnalyzer;

/**
 * Shared PostgreSQL full-grammer expression analyzer.
 *
 * <p>CN: 当前 PostgreSQL v16/v17/v18 表达式规则共用 core 的 parse-tree analyzer。
 * 如果某个 PostgreSQL major 需要特殊函数、operator 或 window 语义，应在对应版本包中
 * 新增子类或 hook，而不是复制整套 analyzer。
 *
 * <p>EN: Shared PostgreSQL full-grammer expression analyzer. PostgreSQL
 * v16/v17/v18 currently share the core parse-tree analyzer. If one major
 * version needs special function, operator, or window semantics, add a
 * version-specific subclass or hook instead of copying the whole analyzer.
 */
public class PostgresExpressionAnalyzer extends FullGrammerExpressionAnalyzer {
    @Override
    public boolean prefersDialectWriteAnalyses(ParseTree expression) {
        return scalarSubquery(expression) != null;
    }

    @Override
    public List<FullGrammerExpressionAnalysis> writeAnalyses(ParseTree expression, String defaultQualifier) {
        ParseTree scalar = scalarSubquery(expression);
        if (scalar == null) {
            return super.writeAnalyses(expression, defaultQualifier);
        }
        FullGrammerExpressionAnalysis value = scalarProjection(scalar, defaultQualifier);
        FullGrammerExpressionAnalysis control = scalarControl(scalar, defaultQualifier);
        List<FullGrammerExpressionAnalysis> result = new ArrayList<>(2);
        if (value.hasSources()) {
            result.add(value.withTransform(value.transformType(), "VALUE"));
        }
        if (control.hasSources()) {
            result.add(control.withTransform("CASE_WHEN", "CONTROL"));
        }
        return List.copyOf(result);
    }

    private FullGrammerExpressionAnalysis scalarProjection(ParseTree scalar, String defaultQualifier) {
        ParseTree target = firstDescendant(scalar, "Target_labelContext");
        ParseTree projection = target == null ? null : firstExpressionDescendant(target);
        if (projection == null) {
            return empty("VALUE");
        }
        FullGrammerExpressionAnalysis analysis = analyze(projection, defaultQualifier);
        return analysis.withTransform(analysis.transformType(), "VALUE");
    }

    private FullGrammerExpressionAnalysis scalarControl(ParseTree scalar, String defaultQualifier) {
        List<String> aliases = new ArrayList<>();
        List<String> columns = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String context : List.of(
                "Join_qualContext", "Where_clauseContext", "Having_clauseContext", "Group_clauseContext")) {
            List<ParseTree> controls = new ArrayList<>();
            collectDirectScopeContexts(scalar, scalar, context, controls);
            for (ParseTree control : controls) {
                append(aliases, columns, seen, analyze(control, defaultQualifier));
            }
        }
        return new FullGrammerExpressionAnalysis(aliases, columns, "CASE_WHEN", "CONTROL");
    }

    private void collectDirectScopeContexts(
            ParseTree root,
            ParseTree tree,
            String expected,
            List<ParseTree> result
    ) {
        if (tree == null) {
            return;
        }
        if (tree != root && isScalarBoundary(tree)) {
            return;
        }
        if (tree.getClass().getSimpleName().equals(expected)) {
            result.add(tree);
            return;
        }
        for (int index = 0; index < tree.getChildCount(); index++) {
            collectDirectScopeContexts(root, tree.getChild(index), expected, result);
        }
    }

    private ParseTree scalarSubquery(ParseTree tree) {
        if (tree == null) {
            return null;
        }
        if (isScalarBoundary(tree)) {
            return tree;
        }
        for (int index = 0; index < tree.getChildCount(); index++) {
            ParseTree found = scalarSubquery(tree.getChild(index));
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private boolean isScalarBoundary(ParseTree tree) {
        String name = tree.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        return name.equals("select_with_parenscontext") || name.contains("scalarsubquery");
    }

    private ParseTree firstDescendant(ParseTree tree, String expectedSimpleName) {
        if (tree == null) {
            return null;
        }
        if (tree.getClass().getSimpleName().equals(expectedSimpleName)) {
            return tree;
        }
        for (int index = 0; index < tree.getChildCount(); index++) {
            ParseTree found = firstDescendant(tree.getChild(index), expectedSimpleName);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private ParseTree firstExpressionDescendant(ParseTree tree) {
        if (tree == null) {
            return null;
        }
        String name = tree.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        if (name.startsWith("a_expr") || name.startsWith("b_expr") || name.startsWith("c_expr")) {
            return tree;
        }
        for (int index = 0; index < tree.getChildCount(); index++) {
            ParseTree found = firstExpressionDescendant(tree.getChild(index));
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private void append(
            List<String> aliases,
            List<String> columns,
            Set<String> seen,
            FullGrammerExpressionAnalysis analysis
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

    private FullGrammerExpressionAnalysis empty(String flowKind) {
        return new FullGrammerExpressionAnalysis(List.of(), List.of(), "UNKNOWN_EXPRESSION", flowKind);
    }
}
