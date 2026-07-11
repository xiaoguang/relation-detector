package com.relationdetector.oracle.fullgrammer.common;

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
import com.relationdetector.oracle.fullgrammer.common.OracleFullGrammerParseTreeAdapter.Role;

/** Per-parse Oracle VALUE/CONTROL and scalar-subquery expression analysis. */
final class OracleFullGrammerExpressionSupport extends OracleFullGrammerParseTreeSupport {
    private final OracleExpressionTransformSupport transforms;
    private final Supplier<String> defaultAlias;

    OracleFullGrammerExpressionSupport(
            OracleSqlEventVisitorCore core,
            OracleFullGrammerParseTreeAdapter adapter,
            Supplier<String> defaultAlias
    ) {
        super(core, adapter);
        this.transforms = new OracleExpressionTransformSupport(core, adapter);
        this.defaultAlias = defaultAlias;
    }

    OracleColumnRead singleSelectColumn(ParserRuleContext subquery) {
        List<ParserRuleContext> items = selectItems(subquery);
        if (items.size() != 1 || child(items.get(0), "expression") == null) {
            return null;
        }
        ParserRuleContext expression = child(items.get(0), "expression");
        ParserRuleContext general = first(expression, Role.GENERAL_ELEMENT);
        if (general != null && name(general).equals(name(expression)) && !name(general).contains(".")) {
            Set<String> aliases = new LinkedHashSet<>();
            collectPhysicalRowsetAliases(subquery, aliases);
            return aliases.size() == 1 ? new OracleColumnRead(aliases.iterator().next(), name(general)) : null;
        }
        return singleColumn(expression);
    }

    List<ParserRuleContext> selectItems(ParseTree ctx) {
        ParserRuleContext query = first(ctx, Role.QUERY_BLOCK);
        ParserRuleContext selectedList = child(query, "selected_list");
        return selectedList == null ? List.of() : children(selectedList, "select_list_elements");
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
                result.add(new OracleExpressionAnalysis(control.sources(), LineageTransformType.CASE_WHEN,
                        LineageFlowKind.CONTROL));
            }
            return List.copyOf(result);
        }
        return expressionAnalyses(expression, columnReads(expression));
    }

    private void collectPhysicalRowsetAliases(ParseTree tree, Set<String> aliases) {
        if (tree == null) {
            return;
        }
        if (hasRole(tree, Role.TABLE_REF_AUX) && tree instanceof ParserRuleContext tableRef) {
            ParserRuleContext internal = child(tableRef, "table_ref_aux_internal");
            String table = tableFrom(internal);
            if (!table.isBlank()) {
                aliases.add(child(tableRef, "table_alias") == null
                        ? core.baseName(table)
                        : name(child(tableRef, "table_alias")));
            }
            return;
        }
        for (int index = 0; index < tree.getChildCount(); index++) {
            collectPhysicalRowsetAliases(tree.getChild(index), aliases);
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
            for (ParserRuleContext part : children(tree, "general_element_part")) {
                if (!children(part, "function_argument").isEmpty()) {
                    return true;
                }
            }
        }
        for (int index = 0; index < tree.getChildCount(); index++) {
            if (containsFunctionCall(tree.getChild(index))) {
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
        ParserRuleContext caseExpression = first(expression, Role.CASE_EXPRESSION);
        if (caseExpression == null) {
            OracleExpressionAnalysis analysis = new OracleExpressionAnalysis(
                    projectionSources, transforms.transformFor(expression), LineageFlowKind.VALUE);
            return analysis.sources().isEmpty() ? List.of() : List.of(analysis);
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
        List<OracleColumnRead> values = projectionSources.stream()
                .filter(source -> !caseControls.contains(source) || caseValues.contains(source))
                .distinct().toList();
        List<OracleExpressionAnalysis> result = new ArrayList<>(2);
        if (!values.isEmpty()) {
            LineageTransformType outer = transforms.transformFor(expression);
            LineageTransformType transform = outer == LineageTransformType.AGGREGATE
                    || outer == LineageTransformType.CUMULATIVE
                    ? outer : LineageTransformType.CASE_WHEN;
            result.add(new OracleExpressionAnalysis(values, transform, LineageFlowKind.VALUE));
        }
        if (!caseControls.isEmpty()) {
            result.add(new OracleExpressionAnalysis(List.copyOf(caseControls), LineageTransformType.CASE_WHEN,
                    LineageFlowKind.CONTROL));
        }
        return List.copyOf(result);
    }

    private List<OracleExpressionAnalysis> caseRoleAnalyses(ParserRuleContext caseExpression) {
        ParserRuleContext caseBody = child(caseExpression, "simple_case_expression");
        boolean simple = caseBody != null;
        if (caseBody == null) {
            caseBody = child(caseExpression, "searched_case_expression");
        }
        if (caseBody == null) {
            return List.of();
        }
        OracleExpressionAnalysis value = OracleExpressionAnalysis.empty();
        OracleExpressionAnalysis control = OracleExpressionAnalysis.empty();
        if (simple) {
            ParserRuleContext selector = child(caseBody, "expression");
            if (selector != null) {
                control = OracleExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                        LineageFlowKind.CONTROL, control, analyze(selector));
            }
        }
        for (ParserRuleContext whenPart : children(caseBody, "case_when_part_expression")) {
            List<ParserRuleContext> expressions = children(whenPart, "expression");
            if (!expressions.isEmpty()) {
                control = OracleExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                        LineageFlowKind.CONTROL, control, analyze(expressions.get(0)));
            }
            if (expressions.size() > 1) {
                value = OracleExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                        LineageFlowKind.VALUE, value, analyze(expressions.get(1)));
            }
        }
        ParserRuleContext elsePart = child(caseBody, "case_else_part_expression");
        if (elsePart != null && child(elsePart, "expression") != null) {
            value = OracleExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                    LineageFlowKind.VALUE, value, analyze(child(elsePart, "expression")));
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
        if (items.size() != 1 || child(items.get(0), "expression") == null) {
            return List.of();
        }
        ParseTree expression = child(items.get(0), "expression");
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
            ParserRuleContext expression = child(item, "expression");
            if (expression == null) {
                continue;
            }
            for (ParserRuleContext nested : directScalarSubqueries(expression)) {
                for (OracleColumnRead source : scalarControlAnalysis(nested).sources()) {
                    reads.putIfAbsent(source.alias() + "." + source.column(), source);
                }
            }
        }
        return new OracleExpressionAnalysis(new ArrayList<>(reads.values()), LineageTransformType.CASE_WHEN,
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
            addColumnRead(name(tree), reads);
            return;
        }
        if (hasRole(tree, Role.GENERAL_ELEMENT)) {
            String text = name(tree);
            if (!text.contains("(") && text.contains(".")) {
                addColumnRead(text, reads);
                return;
            }
        }
        for (int index = 0; index < tree.getChildCount(); index++) {
            collectProjectionColumnReads(tree.getChild(index), reads);
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
        for (int index = 0; index < tree.getChildCount(); index++) {
            collectDirectScalarSubqueries(tree.getChild(index), result);
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
        for (int index = 0; index < tree.getChildCount(); index++) {
            collectDirectScopeColumns(tree.getChild(index), targetRole, reads);
        }
    }

    private List<OracleColumnRead> columnReads(ParseTree tree) {
        Map<String, OracleColumnRead> reads = new LinkedHashMap<>();
        collectColumnReads(tree, reads);
        return new ArrayList<>(reads.values());
    }

    private void collectColumnReads(ParseTree tree, Map<String, OracleColumnRead> reads) {
        if (tree == null) {
            return;
        }
        if (hasRole(tree, Role.COLUMN_REFERENCE)) {
            addColumnRead(name(tree), reads);
            return;
        }
        if (hasRole(tree, Role.GENERAL_ELEMENT)) {
            String text = name(tree);
            if (!text.contains("(") && text.contains(".")) {
                addColumnRead(text, reads);
                return;
            }
            List<ParserRuleContext> parts = children(tree, "general_element_part");
            if (!text.contains("(") && parts.size() == 1
                    && children(parts.get(0), "function_argument").isEmpty()) {
                addColumnRead(text, reads);
                return;
            }
        }
        for (int index = 0; index < tree.getChildCount(); index++) {
            collectColumnReads(tree.getChild(index), reads);
        }
    }

    private void addColumnRead(String raw, Map<String, OracleColumnRead> reads) {
        String value = core.clean(raw);
        int dot = value.lastIndexOf('.');
        if (dot < 0) {
            String alias = defaultAlias.get();
            String column = core.clean(value);
            if (!alias.isBlank() && !column.isBlank()) {
                reads.putIfAbsent(alias + "." + column, new OracleColumnRead(alias, column));
            }
            return;
        }
        if (dot == 0 || dot == value.length() - 1) {
            return;
        }
        String alias = core.clean(value.substring(0, dot));
        String column = core.clean(value.substring(dot + 1));
        if (!alias.isBlank() && !column.isBlank()) {
            reads.putIfAbsent(alias + "." + column, new OracleColumnRead(alias, column));
        }
    }
}
