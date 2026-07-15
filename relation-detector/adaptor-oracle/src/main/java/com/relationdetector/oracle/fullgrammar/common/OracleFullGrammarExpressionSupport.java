package com.relationdetector.oracle.fullgrammar.common;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import com.relationdetector.contracts.Enums.LineageFlowKind;
import com.relationdetector.contracts.Enums.LineageTransformType;
import com.relationdetector.oracle.fullgrammar.common.OracleFullGrammarParseTreeAdapter.Role;
import com.relationdetector.oracle.routine.OracleRoutineScope;

/** Per-parse Oracle VALUE/CONTROL and scalar-subquery expression analysis. */
final class OracleFullGrammarExpressionSupport extends OracleFullGrammarParseTreeSupport {
    private final OracleExpressionTransformSupport transforms;
    private final OracleColumnReadCollector columns;

    OracleFullGrammarExpressionSupport(
            OracleSqlEventVisitorCore core,
            OracleFullGrammarParseTreeAdapter adapter,
            Supplier<String> defaultAlias,
            OracleRoutineScope routineScope
    ) {
        super(core, adapter);
        this.transforms = new OracleExpressionTransformSupport(core, adapter);
        this.columns = new OracleColumnReadCollector(core, adapter, defaultAlias, routineScope);
    }

    OracleColumnRead singleSelectColumn(ParserRuleContext subquery) {
        List<ParserRuleContext> items = selectItems(subquery);
        if (items.size() != 1 || child(items.get(0), Role.EXPRESSION) == null) {
            return null;
        }
        ParserRuleContext expression = child(items.get(0), Role.EXPRESSION);
        ParserRuleContext general = first(expression, Role.GENERAL_ELEMENT);
        if (general != null && name(general).equals(name(expression)) && !name(general).contains(".")) {
            Set<String> aliases = new LinkedHashSet<>();
            collectPhysicalRowsetAliases(subquery, subquery, aliases);
            return aliases.size() == 1 ? new OracleColumnRead(aliases.iterator().next(), name(general)) : null;
        }
        return singleColumn(expression);
    }

    List<ParserRuleContext> selectItems(ParseTree ctx) {
        ParserRuleContext query = first(ctx, Role.QUERY_BLOCK);
        ParserRuleContext selectedList = child(query, Role.SELECTED_LIST);
        return selectedList == null ? List.of() : children(selectedList, Role.SELECT_LIST_ELEMENT);
    }

    OracleColumnRead singleColumn(ParseTree ctx) {
        List<OracleColumnRead> columns = columnReads(ctx);
        return columns.size() == 1 ? columns.get(0) : null;
    }

    OracleColumnRead singleDirectColumn(ParseTree ctx) {
        return containsFunctionCall(ctx) ? null : singleColumn(ctx);
    }

    List<OracleExpressionAnalysis> writeAnalyses(ParseTree expression) {
        ParserRuleContext scalarSubquery = first(expression, Role.SUBQUERY);
        if (scalarSubquery != null) {
            List<OracleExpressionAnalysis> projections = scalarProjectionAnalyses(scalarSubquery);
            OracleExpressionAnalysis control = scalarControlAnalysis(scalarSubquery);
            List<OracleExpressionAnalysis> result = new ArrayList<>(2);
            for (OracleExpressionAnalysis projection : projections) {
                if (projection.flowKind() == LineageFlowKind.VALUE && !projection.sources().isEmpty()) {
                    result.add(new OracleExpressionAnalysis(
                            projection.sources(),
                            transforms.dominantValueTransform(
                                    transforms.transformFor(expression), projection.transform()),
                            LineageFlowKind.VALUE));
                } else if (projection.flowKind() == LineageFlowKind.CONTROL) {
                    control = OracleExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                            LineageFlowKind.CONTROL, control, projection);
                }
            }
            if (!control.sources().isEmpty()) {
                result.add(new OracleExpressionAnalysis(control.sources(), control.transform(),
                        LineageFlowKind.CONTROL));
            }
            return List.copyOf(result);
        }
        return expressionAnalyses(expression, columnReads(expression));
    }

    OracleExpressionAnalysis directControlAnalysis(ParseTree expression) {
        return new OracleExpressionAnalysis(columnReads(expression), LineageTransformType.DIRECT,
                LineageFlowKind.CONTROL);
    }

    OracleExpressionAnalysis groupingControlAnalysis(ParseTree query) {
        ParserRuleContext queryBlock = first(query, Role.QUERY_BLOCK);
        if (queryBlock == null) {
            return OracleExpressionAnalysis.empty();
        }
        Map<String, OracleColumnRead> reads = new LinkedHashMap<>();
        collectDirectScopeColumns(queryBlock, Role.GROUP_BY_ELEMENT, reads);
        return new OracleExpressionAnalysis(new ArrayList<>(reads.values()), LineageTransformType.AGGREGATE,
                LineageFlowKind.CONTROL);
    }

    boolean isAggregateExpression(ParseTree expression) {
        return transforms.transformFor(expression) == LineageTransformType.AGGREGATE;
    }

    private void collectPhysicalRowsetAliases(ParseTree root, ParseTree tree, Set<String> aliases) {
        if (tree == null) {
            return;
        }
        if (tree != root && hasRole(tree, Role.SUBQUERY)) {
            return;
        }
        if (hasRole(tree, Role.TABLE_REF_AUX) && tree instanceof ParserRuleContext tableRef) {
            ParserRuleContext internal = child(tableRef, Role.TABLE_REF_INTERNAL_WRAPPER);
            String table = tableFrom(internal);
            if (!table.isBlank()) {
                aliases.add(child(tableRef, Role.TABLE_ALIAS) == null
                        ? core.baseName(table)
                        : name(child(tableRef, Role.TABLE_ALIAS)));
            }
            return;
        }
        for (ParseTree child : typedChildren(tree)) {
            collectPhysicalRowsetAliases(root, child, aliases);
        }
    }

    private boolean containsFunctionCall(ParseTree tree) {
        if (tree == null) {
            return false;
        }
        if (hasRole(tree, Role.FUNCTION_EXPRESSION)) {
            return true;
        }
        if (hasRole(tree, Role.GENERAL_ELEMENT)) {
            for (ParserRuleContext part : children(tree, Role.GENERAL_ELEMENT_PART)) {
                if (!children(part, Role.FUNCTION_ARGUMENT).isEmpty()) {
                    return true;
                }
            }
        }
        for (ParseTree child : typedChildren(tree)) {
            if (containsFunctionCall(child)) {
                return true;
            }
        }
        return false;
    }

    private OracleExpressionAnalysis analyze(ParseTree ctx) {
        List<OracleColumnRead> columns = columnReads(ctx);
        if (columns.isEmpty()) {
            return OracleExpressionAnalysis.empty();
        }
        LineageTransformType transform = transforms.transformFor(ctx);
        LineageFlowKind flow = transform == LineageTransformType.CASE_WHEN
                ? LineageFlowKind.CONTROL
                : LineageFlowKind.VALUE;
        return new OracleExpressionAnalysis(columns, transform, flow);
    }

    private List<OracleExpressionAnalysis> expressionAnalyses(
            ParseTree expression,
            List<OracleColumnRead> projectionSources
    ) {
        Set<OracleColumnRead> windowControls = windowControlSources(expression);
        List<OracleColumnRead> effectiveProjectionSources = projectionSources.stream()
                .filter(source -> !windowControls.contains(source))
                .toList();
        ParserRuleContext caseExpression = first(expression, Role.CASE_EXPRESSION);
        if (caseExpression == null) {
            OracleExpressionAnalysis analysis = new OracleExpressionAnalysis(
                    effectiveProjectionSources, transforms.transformFor(expression), LineageFlowKind.VALUE);
            List<OracleExpressionAnalysis> result = new ArrayList<>(2);
            if (!analysis.sources().isEmpty()) {
                result.add(analysis);
            }
            addWindowControl(result, windowControls);
            return List.copyOf(result);
        }
        List<OracleExpressionAnalysis> caseRoles = caseRoleAnalyses(caseExpression);
        Set<OracleColumnRead> caseValues = new LinkedHashSet<>();
        Set<OracleColumnRead> caseControls = new LinkedHashSet<>();
        for (OracleExpressionAnalysis role : caseRoles) {
            if (role.flowKind() == LineageFlowKind.VALUE) {
                caseValues.addAll(role.sources());
            } else {
                caseControls.addAll(role.sources());
            }
        }
        List<OracleColumnRead> values = effectiveProjectionSources.stream()
                .filter(source -> !caseControls.contains(source) || caseValues.contains(source))
                .distinct().toList();
        List<OracleExpressionAnalysis> result = new ArrayList<>(2);
        if (!values.isEmpty()) {
            LineageTransformType outer = transforms.transformFor(expression);
            LineageTransformType transform = isTopLevelCaseExpression(expression)
                    && outer != LineageTransformType.AGGREGATE
                    && outer != LineageTransformType.CUMULATIVE
                    && outer != LineageTransformType.WINDOW_DERIVED
                    ? LineageTransformType.CASE_WHEN
                    : transforms.dominantValueTransform(outer, LineageTransformType.CASE_WHEN);
            result.add(new OracleExpressionAnalysis(values, transform, LineageFlowKind.VALUE));
        }
        if (!caseControls.isEmpty()) {
            result.add(new OracleExpressionAnalysis(List.copyOf(caseControls), LineageTransformType.CASE_WHEN,
                    LineageFlowKind.CONTROL));
        }
        addWindowControl(result, windowControls);
        return List.copyOf(result);
    }

    private Set<OracleColumnRead> windowControlSources(ParseTree expression) {
        ParserRuleContext window = first(expression, Role.WINDOW_CLAUSE);
        return window == null ? Set.of() : new LinkedHashSet<>(columnReads(window));
    }

    private void addWindowControl(
            List<OracleExpressionAnalysis> result,
            Set<OracleColumnRead> windowControls
    ) {
        if (!windowControls.isEmpty()) {
            result.add(new OracleExpressionAnalysis(List.copyOf(windowControls),
                    LineageTransformType.WINDOW_DERIVED, LineageFlowKind.CONTROL));
        }
    }

    private boolean isTopLevelCaseExpression(ParseTree tree) {
        ParseTree current = tree;
        while (current != null) {
            if (hasRole(current, Role.CASE_EXPRESSION)) {
                return true;
            }
            if (operatorSemantic(current) != OracleFullGrammarParseTreeAdapter.OperatorSemantic.NONE
                    || hasRole(current, Role.FUNCTION_EXPRESSION)
                    || functionName(current).filter(name -> !name.isBlank()).isPresent()) {
                return false;
            }
            List<ParseTree> children = typedChildren(current);
            if (children.size() != 1) {
                return false;
            }
            current = children.get(0);
        }
        return false;
    }

    private List<OracleExpressionAnalysis> caseRoleAnalyses(ParserRuleContext caseExpression) {
        ParserRuleContext caseBody = child(caseExpression, Role.SIMPLE_CASE_EXPRESSION);
        boolean simple = caseBody != null;
        if (caseBody == null) {
            caseBody = child(caseExpression, Role.SEARCHED_CASE_EXPRESSION);
        }
        if (caseBody == null) {
            return List.of();
        }
        OracleExpressionAnalysis value = OracleExpressionAnalysis.empty();
        OracleExpressionAnalysis control = OracleExpressionAnalysis.empty();
        if (simple) {
            ParserRuleContext selector = child(caseBody, Role.EXPRESSION);
            if (selector != null) {
                control = OracleExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                        LineageFlowKind.CONTROL, control, analyze(selector));
            }
        }
        for (ParserRuleContext whenPart : children(caseBody, Role.CASE_WHEN_PART)) {
            List<ParserRuleContext> expressions = children(whenPart, Role.EXPRESSION);
            if (!expressions.isEmpty()) {
                control = OracleExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                        LineageFlowKind.CONTROL, control, analyze(expressions.get(0)));
            }
            if (expressions.size() > 1) {
                value = OracleExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                        LineageFlowKind.VALUE, value, analyze(expressions.get(1)));
            }
        }
        ParserRuleContext elsePart = child(caseBody, Role.CASE_ELSE_PART);
        if (elsePart != null && child(elsePart, Role.EXPRESSION) != null) {
            value = OracleExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                    LineageFlowKind.VALUE, value, analyze(child(elsePart, Role.EXPRESSION)));
        }
        List<OracleExpressionAnalysis> result = new ArrayList<>(2);
        if (!value.sources().isEmpty()) {
            result.add(new OracleExpressionAnalysis(value.sources(), LineageTransformType.CASE_WHEN,
                    LineageFlowKind.VALUE));
        }
        if (!control.sources().isEmpty()) {
            result.add(new OracleExpressionAnalysis(control.sources(), LineageTransformType.CASE_WHEN,
                    LineageFlowKind.CONTROL));
        }
        return List.copyOf(result);
    }

    private List<OracleExpressionAnalysis> scalarProjectionAnalyses(ParserRuleContext subquery) {
        List<ParserRuleContext> items = selectItems(subquery);
        if (items.size() != 1 || child(items.get(0), Role.EXPRESSION) == null) {
            return List.of();
        }
        ParseTree expression = child(items.get(0), Role.EXPRESSION);
        Map<String, OracleColumnRead> reads = new LinkedHashMap<>();
        collectProjectionColumnReads(expression, reads);
        return expressionAnalyses(expression, new ArrayList<>(reads.values()));
    }

    private OracleExpressionAnalysis scalarControlAnalysis(ParserRuleContext subquery) {
        ParserRuleContext query = first(subquery, Role.QUERY_BLOCK);
        if (query == null) {
            return OracleExpressionAnalysis.empty();
        }
        Map<String, OracleColumnRead> reads = new LinkedHashMap<>();
        collectDirectScopeColumns(query, Role.JOIN_ON, reads);
        collectDirectScopeColumns(query, Role.WHERE_CLAUSE, reads);
        collectDirectScopeColumns(query, Role.GROUP_BY_ELEMENT, reads);
        collectDirectScopeColumns(query, Role.HAVING_CLAUSE, reads);
        for (ParserRuleContext item : selectItems(subquery)) {
            ParserRuleContext expression = child(item, Role.EXPRESSION);
            if (expression == null) {
                continue;
            }
            for (ParserRuleContext nested : directScalarSubqueries(expression)) {
                for (OracleColumnRead source : scalarControlAnalysis(nested).sources()) {
                    reads.putIfAbsent(source.alias() + "." + source.column(), source);
                }
            }
        }
        return new OracleExpressionAnalysis(new ArrayList<>(reads.values()), LineageTransformType.DIRECT,
                LineageFlowKind.CONTROL);
    }

    private void collectProjectionColumnReads(ParseTree tree, Map<String, OracleColumnRead> reads) {
        if (tree == null) {
            return;
        }
        if (hasRole(tree, Role.SUBQUERY) && tree instanceof ParserRuleContext subquery) {
            for (OracleExpressionAnalysis analysis : scalarProjectionAnalyses(subquery)) {
                if (analysis.flowKind() == LineageFlowKind.VALUE) {
                    analysis.sources().forEach(source ->
                            reads.putIfAbsent(source.alias() + "." + source.column(), source));
                }
            }
            return;
        }
        if (hasRole(tree, Role.COLUMN_REFERENCE)) {
            columns.add(name(tree), reads);
            return;
        }
        if (hasRole(tree, Role.GENERAL_ELEMENT)) {
            String text = name(tree);
            if (!text.contains("(") && text.contains(".")) {
                columns.add(text, reads);
                return;
            }
        }
        for (ParseTree child : typedChildren(tree)) {
            collectProjectionColumnReads(child, reads);
        }
    }

    private List<ParserRuleContext> directScalarSubqueries(ParseTree tree) {
        List<ParserRuleContext> result = new ArrayList<>();
        collectDirectScalarSubqueries(tree, result);
        return result;
    }

    private void collectDirectScalarSubqueries(ParseTree tree, List<ParserRuleContext> result) {
        if (tree == null) {
            return;
        }
        if (hasRole(tree, Role.SUBQUERY) && tree instanceof ParserRuleContext subquery) {
            result.add(subquery);
            return;
        }
        for (ParseTree child : typedChildren(tree)) {
            collectDirectScalarSubqueries(child, result);
        }
    }

    private void collectDirectScopeColumns(
            ParseTree tree,
            Role targetRole,
            Map<String, OracleColumnRead> reads
    ) {
        if (tree == null || hasRole(tree, Role.SUBQUERY)) {
            return;
        }
        if (hasRole(tree, targetRole)) {
            columnReads(tree).forEach(source ->
                    reads.putIfAbsent(source.alias() + "." + source.column(), source));
            return;
        }
        for (ParseTree child : typedChildren(tree)) {
            collectDirectScopeColumns(child, targetRole, reads);
        }
    }

    private List<OracleColumnRead> columnReads(ParseTree tree) {
        return columns.reads(tree);
    }
}
