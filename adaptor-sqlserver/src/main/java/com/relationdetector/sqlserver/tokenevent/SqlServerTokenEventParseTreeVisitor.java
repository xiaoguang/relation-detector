package com.relationdetector.sqlserver.tokenevent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.antlr.v4.runtime.ParserRuleContext;

import com.relationdetector.contracts.Enums.LineageFlowKind;
import com.relationdetector.contracts.Enums.LineageTransformType;
import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredSqlEvent;

/**
 * SQL Server token-event parse-tree visitor.
 *
 * <p>CN: 本 visitor 只消费 {@code SqlServerRelationSql} token-event typed
 * context，不复用 SQL Server full-grammer collector，也不通过 regex/token span
 * 判断 SQL 结构。</p>
 */
public final class SqlServerTokenEventParseTreeVisitor extends SqlServerRelationSqlBaseVisitor<Void> {
    private final SqlStatementRecord statement;
    private final boolean ddlOnly;
    private final List<StructuredSqlEvent> events = new ArrayList<>();
    private final ArrayDeque<String> projectionOwners = new ArrayDeque<>();
    private final ArrayDeque<String> ddlTables = new ArrayDeque<>();
    private final ArrayDeque<String> joinKinds = new ArrayDeque<>();
    private int existsDepth;

    public SqlServerTokenEventParseTreeVisitor(SqlStatementRecord statement) {
        this(statement, false);
    }

    public SqlServerTokenEventParseTreeVisitor(SqlStatementRecord statement, boolean ddlOnly) {
        this.statement = statement;
        this.ddlOnly = ddlOnly;
    }

    public List<StructuredSqlEvent> collect(SqlServerRelationSqlParser.Tsql_fileContext root) {
        visit(root);
        return List.copyOf(events);
    }

    @Override
    public Void visitCommon_table_expression(SqlServerRelationSqlParser.Common_table_expressionContext ctx) {
        String name = clean(ctx.id_().getText());
        if (!name.isBlank()) {
            Map<String, Object> attrs = attrs();
            attrs.put("name", name);
            attrs.put("table", name);
            attrs.put("qualifiedTable", name);
            add(StructuredParseEventType.CTE_DECLARATION, ctx, attrs);
            add(StructuredParseEventType.IGNORED_ROWSET, ctx, attrs);
            projectionOwners.push(name);
            visit(ctx.select_statement());
            projectionOwners.pop();
            return null;
        }
        return visitChildren(ctx);
    }

    @Override
    public Void visitQuery_specification(SqlServerRelationSqlParser.Query_specificationContext ctx) {
        if (ctx.table_sources() != null) {
            visit(ctx.table_sources());
        }
        if (ctx.search_condition_clause() != null) {
            visit(ctx.search_condition_clause());
        }
        if (!projectionOwners.isEmpty()) {
            emitProjectionItems(ctx.select_list(), projectionOwners.peek(), singleProjectionQualifier(ctx.table_sources()));
        }
        return null;
    }

    @Override
    public Void visitTable_source(SqlServerRelationSqlParser.Table_sourceContext ctx) {
        visit(ctx.table_source_item());
        for (SqlServerRelationSqlParser.Table_source_suffixContext suffix : ctx.table_source_suffix()) {
            visit(suffix);
        }
        return null;
    }

    @Override
    public Void visitJoin_on(SqlServerRelationSqlParser.Join_onContext ctx) {
        visit(ctx.table_source());
        joinKinds.push(joinKind(ctx.join_type()));
        visit(ctx.search_condition());
        joinKinds.pop();
        return null;
    }

    @Override
    public Void visitCross_join(SqlServerRelationSqlParser.Cross_joinContext ctx) {
        visit(ctx.table_source());
        return null;
    }

    @Override
    public Void visitApply_(SqlServerRelationSqlParser.Apply_Context ctx) {
        visit(ctx.table_source());
        return null;
    }

    @Override
    public Void visitTable_source_item(SqlServerRelationSqlParser.Table_source_itemContext ctx) {
        if (ctx.full_table_name() != null) {
            String qualified = qualifiedName(ctx.full_table_name());
            String table = baseName(qualified);
            String alias = ctx.as_table_alias() == null ? "" : clean(ctx.as_table_alias().table_alias().getText());
            if (isTemp(table)) {
                Map<String, Object> attrs = attrs();
                attrs.put("table", table);
                attrs.put("qualifiedTable", qualified);
                add(StructuredParseEventType.LOCAL_TEMP_TABLE_DECLARATION, ctx, attrs);
                return null;
            }
            Map<String, Object> attrs = attrs();
            attrs.put("keyword", "FROM");
            attrs.put("qualifiedTable", qualified);
            attrs.put("table", table);
            if (!alias.isBlank()) {
                attrs.put("alias", alias);
            }
            add(StructuredParseEventType.ROWSET_REFERENCE, ctx, attrs);
            return null;
        }
        if (ctx.derived_table() != null) {
            String alias = ctx.as_table_alias() == null ? "" : clean(ctx.as_table_alias().table_alias().getText());
            if (!alias.isBlank()) {
                Map<String, Object> attrs = attrs();
                attrs.put("name", alias);
                attrs.put("table", alias);
                attrs.put("qualifiedTable", alias);
                add(StructuredParseEventType.IGNORED_ROWSET, ctx, attrs);
                projectionOwners.push(alias);
                visit(ctx.derived_table());
                projectionOwners.pop();
            } else {
                visit(ctx.derived_table());
            }
            return null;
        }
        if (ctx.function_call() != null || ctx.LOCAL_ID() != null) {
            Map<String, Object> attrs = attrs();
            String alias = ctx.as_table_alias() == null ? ctx.getText() : clean(ctx.as_table_alias().table_alias().getText());
            attrs.put("name", alias);
            attrs.put("table", alias);
            attrs.put("qualifiedTable", alias);
            attrs.put("reason", "FUNCTION_ROWSET");
            add(StructuredParseEventType.IGNORED_ROWSET, ctx, attrs);
            return null;
        }
        return visitChildren(ctx);
    }

    @Override
    public Void visitPredicate(SqlServerRelationSqlParser.PredicateContext ctx) {
        if (ctx.EXISTS() != null) {
            existsDepth++;
            visit(ctx.subquery());
            existsDepth--;
            return null;
        }
        List<SqlServerRelationSqlParser.ExpressionContext> expressions = ctx.expression();
        if (expressions.size() >= 2 && ctx.comparison_operator() != null && "=".equals(ctx.comparison_operator().getText())) {
            ColumnRead left = singleColumn(expressions.get(0));
            ColumnRead right = singleColumn(expressions.get(1));
            if (left != null && right != null) {
                Map<String, Object> attrs = attrs();
                attrs.put("leftAlias", left.alias());
                attrs.put("leftColumn", left.column());
                attrs.put("rightAlias", right.alias());
                attrs.put("rightColumn", right.column());
                attrs.put("joinKind", existsDepth > 0 ? "EXISTS" : currentJoinKind());
                add(existsDepth > 0 ? StructuredParseEventType.EXISTS_PREDICATE : StructuredParseEventType.PREDICATE_EQUALITY,
                        ctx,
                        attrs);
                return null;
            }
        }
        if (!expressions.isEmpty() && ctx.IN() != null && ctx.subquery() != null) {
            ColumnRead outer = singleColumn(expressions.get(0));
            SqlServerRelationSqlParser.Select_statementContext subquerySelect = ctx.subquery().select_statement();
            ColumnRead inner = singleSelectColumn(subquerySelect);
            visit(ctx.subquery());
            if (outer != null && inner != null) {
                String innerTable = tableForAlias(
                        subquerySelect.query_expression().query_specification().table_sources(),
                        inner.alias());
                Map<String, Object> attrs = attrs();
                attrs.put("outerAlias", outer.alias());
                attrs.put("outerColumn", outer.column());
                attrs.put("innerAlias", inner.alias());
                attrs.put("innerColumn", inner.column());
                attrs.put("innerTable", baseName(innerTable));
                attrs.put("innerTableAlias", inner.alias());
                attrs.put("verifiedColumnSubquery", true);
                add(StructuredParseEventType.IN_SUBQUERY_PREDICATE, ctx, attrs);
            }
            return null;
        }
        return visitChildren(ctx);
    }

    @Override
    public Void visitInsert_statement(SqlServerRelationSqlParser.Insert_statementContext ctx) {
        String targetTable = baseName(qualifiedName(ctx.ddl_object().full_table_name()));
        List<String> targetColumns = identifiers(ctx.insert_column_name_list().column_name_list());
        visit(ctx.insert_statement_value());
        SqlServerRelationSqlParser.Select_listContext selectList = ctx.insert_statement_value().select_statement() == null
                ? null
                : ctx.insert_statement_value().select_statement().query_expression().query_specification().select_list();
        if (selectList == null) {
            return null;
        }
        List<SqlServerRelationSqlParser.Select_list_elemContext> items = selectList.select_list_elem();
        int count = Math.min(targetColumns.size(), items.size());
        for (int index = 0; index < count; index++) {
            SqlServerRelationSqlParser.ExpressionContext expression = items.get(index).expression();
            if (expression == null) {
                continue;
            }
            ExpressionAnalysis source = analyze(expression);
            if (source.sources().isEmpty()) {
                continue;
            }
            Map<String, Object> attrs = attrs();
            attrs.put("targetTable", targetTable);
            attrs.put("targetColumn", targetColumns.get(index));
            attrs.put("sourceAliases", source.aliases());
            attrs.put("sourceColumns", source.columns());
            attrs.put("transformType", source.transform().name());
            attrs.put("flowKind", source.flowKind().name());
            attrs.put("mappingKind", "INSERT_SELECT");
            add(StructuredParseEventType.INSERT_SELECT_MAPPING, items.get(index), attrs);
        }
        return null;
    }

    @Override
    public Void visitUpdate_statement(SqlServerRelationSqlParser.Update_statementContext ctx) {
        String targetAlias = clean(ctx.ddl_object().getText());
        String targetTable = targetAlias;
        String qualifiedTargetTable = targetAlias;
        if (ctx.table_sources() != null) {
            visit(ctx.table_sources());
            qualifiedTargetTable = tableForAlias(ctx.table_sources(), targetAlias);
            targetTable = baseName(qualifiedTargetTable);
        }
        Map<String, Object> writeTarget = attrs();
        writeTarget.put("qualifiedTable", qualifiedTargetTable);
        writeTarget.put("table", baseName(targetTable));
        writeTarget.put("alias", targetAlias);
        add(StructuredParseEventType.WRITE_TARGET, ctx, writeTarget);
        for (SqlServerRelationSqlParser.Update_elemContext elem : ctx.update_elem()) {
            emitUpdateMapping(elem, targetTable, false);
        }
        if (ctx.search_condition_clause() != null) {
            visit(ctx.search_condition_clause());
        }
        return null;
    }

    @Override
    public Void visitMerge_statement(SqlServerRelationSqlParser.Merge_statementContext ctx) {
        String targetTable = baseName(qualifiedName(ctx.ddl_object().full_table_name()));
        String targetAlias = ctx.as_table_alias() == null ? "" : clean(ctx.as_table_alias().table_alias().getText());
        Map<String, Object> writeTarget = attrs();
        writeTarget.put("qualifiedTable", targetTable);
        writeTarget.put("table", baseName(targetTable));
        if (!targetAlias.isBlank()) {
            writeTarget.put("alias", targetAlias);
        }
        add(StructuredParseEventType.WRITE_TARGET, ctx, writeTarget);
        visit(ctx.table_sources());
        joinKinds.push("MERGE_ON");
        visit(ctx.search_condition());
        joinKinds.pop();
        for (SqlServerRelationSqlParser.Merge_when_clauseContext clause : ctx.merge_when_clause()) {
            for (SqlServerRelationSqlParser.Update_elem_mergeContext elem : clause.update_elem_merge()) {
                emitMergeUpdateMapping(elem, targetTable);
            }
            if (clause.merge_not_matched() != null) {
                emitMergeInsertMappings(clause.merge_not_matched(), targetTable);
            }
        }
        return null;
    }

    @Override
    public Void visitCreate_or_alter_trigger(SqlServerRelationSqlParser.Create_or_alter_triggerContext ctx) {
        List<SqlServerRelationSqlParser.Full_table_nameContext> names = ctx.full_table_name();
        String targetTable = names.size() < 2 ? "" : qualifiedName(names.get(1));
        emitTriggerPseudoRowset(ctx, "inserted", targetTable);
        emitTriggerPseudoRowset(ctx, "deleted", targetTable);
        return visitChildren(ctx);
    }

    @Override
    public Void visitCreate_table(SqlServerRelationSqlParser.Create_tableContext ctx) {
        String table = baseName(qualifiedName(ctx.table_name().full_table_name()));
        ddlTables.push(table);
        for (SqlServerRelationSqlParser.Table_elementContext element : ctx.table_element()) {
            visit(element);
        }
        ddlTables.pop();
        return null;
    }

    @Override
    public Void visitColumn_definition(SqlServerRelationSqlParser.Column_definitionContext ctx) {
        String column = clean(ctx.id_().getText());
        String table = currentDdlTable();
        boolean primary = false;
        boolean unique = false;
        for (SqlServerRelationSqlParser.Column_attributeContext attr : ctx.column_attribute()) {
            if (attr.PRIMARY() != null) {
                primary = true;
            }
            if (attr.UNIQUE() != null) {
                unique = true;
            }
            if (attr.foreign_key_options() != null) {
                addForeignKeyEvents(attr, table, List.of(column),
                        baseName(qualifiedName(attr.foreign_key_options().table_name().full_table_name())),
                        identifiers(attr.foreign_key_options().column_name_list()));
            }
        }
        if (primary || unique) {
            addIndexEvent(table, column, "TARGET_UNIQUE", primary ? "INLINE_PRIMARY_KEY" : "INLINE_UNIQUE", ctx);
        }
        return null;
    }

    @Override
    public Void visitTable_constraint(SqlServerRelationSqlParser.Table_constraintContext ctx) {
        if (ctx.FOREIGN() != null && ctx.foreign_key_options() != null) {
            addForeignKeyEvents(ctx, currentDdlTable(), identifiers(ctx.column_name_list()),
                    baseName(qualifiedName(ctx.foreign_key_options().table_name().full_table_name())),
                    identifiers(ctx.foreign_key_options().column_name_list()));
            return null;
        }
        if (ctx.PRIMARY() != null || ctx.UNIQUE() != null) {
            String role = "TARGET_UNIQUE";
            String kind = ctx.PRIMARY() != null ? "PRIMARY_KEY" : "UNIQUE_CONSTRAINT";
            for (String column : identifiers(ctx.column_name_list_with_order())) {
                addIndexEvent(currentDdlTable(), column, role, kind, ctx);
            }
        }
        return null;
    }

    @Override
    public Void visitCreate_index(SqlServerRelationSqlParser.Create_indexContext ctx) {
        String role = ctx.UNIQUE() == null ? "SOURCE_INDEX" : "TARGET_UNIQUE";
        String kind = ctx.UNIQUE() == null ? "CREATE_INDEX" : "CREATE_UNIQUE_INDEX";
        String table = baseName(qualifiedName(ctx.table_name().full_table_name()));
        for (String column : identifiers(ctx.column_name_list_with_order())) {
            addIndexEvent(table, column, role, kind, ctx);
        }
        return null;
    }

    private void emitProjectionItems(
            SqlServerRelationSqlParser.Select_listContext selectList,
            String owner,
            String defaultQualifier
    ) {
        List<SqlServerRelationSqlParser.Select_list_elemContext> items = selectList.select_list_elem();
        for (SqlServerRelationSqlParser.Select_list_elemContext item : items) {
            SqlServerRelationSqlParser.ExpressionContext expression = item.expression();
            if (expression == null) {
                continue;
            }
            ExpressionAnalysis source = analyze(expression, defaultQualifier);
            if (source.sources().isEmpty()) {
                continue;
            }
            String outputColumn = outputColumn(item);
            if (outputColumn.isBlank()) {
                continue;
            }
            Map<String, Object> attrs = attrs();
            attrs.put("outputAlias", owner);
            attrs.put("outputColumn", outputColumn);
            attrs.put("sourceAliases", source.aliases());
            attrs.put("sourceColumns", source.columns());
            attrs.put("transformType", source.transform().name());
            attrs.put("flowKind", source.flowKind().name());
            add(StructuredParseEventType.PROJECTION_ITEM, item, attrs);
        }
    }

    private void emitUpdateMapping(SqlServerRelationSqlParser.Update_elemContext elem, String targetTable, boolean merge) {
        SqlServerRelationSqlParser.ExpressionContext expression = elem.expression();
        if (expression == null) {
            return;
        }
        String targetColumn = elem.full_column_name() == null
                ? clean(elem.id_().getText())
                : lastPart(elem.full_column_name().getText());
        ExpressionAnalysis source = analyze(expression);
        if (source.sources().isEmpty()) {
            return;
        }
        Map<String, Object> attrs = attrs();
        attrs.put("targetTable", targetTable);
        attrs.put("targetColumn", targetColumn);
        attrs.put("sourceAliases", source.aliases());
        attrs.put("sourceColumns", source.columns());
        attrs.put("transformType", source.transform().name());
        attrs.put("flowKind", source.flowKind().name());
        attrs.put("mappingKind", merge ? "MERGE_UPDATE" : "UPDATE_SET");
        add(merge ? StructuredParseEventType.MERGE_WRITE_MAPPING : StructuredParseEventType.UPDATE_ASSIGNMENT, elem, attrs);
    }

    private void emitMergeUpdateMapping(SqlServerRelationSqlParser.Update_elem_mergeContext elem, String targetTable) {
        SqlServerRelationSqlParser.ExpressionContext expression = elem.expression();
        if (expression == null) {
            return;
        }
        String targetColumn = elem.full_column_name() == null
                ? clean(elem.id_().getText())
                : lastPart(elem.full_column_name().getText());
        ExpressionAnalysis source = analyze(expression);
        if (source.sources().isEmpty()) {
            return;
        }
        Map<String, Object> attrs = attrs();
        attrs.put("targetTable", targetTable);
        attrs.put("targetColumn", targetColumn);
        attrs.put("sourceAliases", source.aliases());
        attrs.put("sourceColumns", source.columns());
        attrs.put("transformType", source.transform().name());
        attrs.put("flowKind", source.flowKind().name());
        attrs.put("mappingKind", "MERGE_UPDATE");
        add(StructuredParseEventType.MERGE_WRITE_MAPPING, elem, attrs);
    }

    private void emitMergeInsertMappings(SqlServerRelationSqlParser.Merge_not_matchedContext ctx, String targetTable) {
        List<String> columns = identifiers(ctx.column_name_list());
        List<SqlServerRelationSqlParser.ExpressionContext> values = ctx.values_clause().expression_list_().expression();
        int count = Math.min(columns.size(), values.size());
        for (int index = 0; index < count; index++) {
            ExpressionAnalysis source = analyze(values.get(index));
            if (source.sources().isEmpty()) {
                continue;
            }
            Map<String, Object> attrs = attrs();
            attrs.put("targetTable", targetTable);
            attrs.put("targetColumn", columns.get(index));
            attrs.put("sourceAliases", source.aliases());
            attrs.put("sourceColumns", source.columns());
            attrs.put("transformType", source.transform().name());
            attrs.put("flowKind", source.flowKind().name());
            attrs.put("mappingKind", "MERGE_INSERT");
            add(StructuredParseEventType.MERGE_WRITE_MAPPING, values.get(index), attrs);
        }
    }

    private void emitTriggerPseudoRowset(ParserRuleContext ctx, String name, String targetTable) {
        Map<String, Object> attrs = attrs();
        attrs.put("name", name);
        attrs.put("targetTable", clean(targetTable));
        add(StructuredParseEventType.TRIGGER_PSEUDO_ROWSET, ctx, attrs);
    }

    private ColumnRead singleSelectColumn(SqlServerRelationSqlParser.Select_statementContext select) {
        List<ColumnRead> columns = selectColumns(select);
        return columns.size() == 1 ? columns.get(0) : null;
    }

    private List<ColumnRead> selectColumns(SqlServerRelationSqlParser.Select_statementContext select) {
        List<ColumnRead> columns = new ArrayList<>();
        for (SqlServerRelationSqlParser.Select_list_elemContext item :
                select.query_expression().query_specification().select_list().select_list_elem()) {
            if (item.expression() == null) {
                return List.of();
            }
            ColumnRead column = singleColumn(item.expression(), singleProjectionQualifier(
                    select.query_expression().query_specification().table_sources()));
            if (column == null) {
                return List.of();
            }
            columns.add(column);
        }
        return columns;
    }

    private ColumnRead singleColumn(SqlServerRelationSqlParser.ExpressionContext expression) {
        return singleColumn(expression, "");
    }

    private ColumnRead singleColumn(SqlServerRelationSqlParser.ExpressionContext expression, String defaultQualifier) {
        if (expression.expression_atom().size() != 1 || !expression.binary_operator().isEmpty()) {
            return null;
        }
        return singleColumn(expression.expression_atom(0), defaultQualifier);
    }

    private ColumnRead singleColumn(SqlServerRelationSqlParser.Expression_atomContext atom, String defaultQualifier) {
        if (atom.full_column_name() != null) {
            List<String> parts = parts(atom.full_column_name().getText());
            if (parts.size() == 1) {
                return defaultQualifier.isBlank() ? null : new ColumnRead(defaultQualifier, parts.get(0));
            }
            return new ColumnRead(parts.get(parts.size() - 2), parts.get(parts.size() - 1));
        }
        if (atom.expression() != null) {
            return singleColumn(atom.expression(), defaultQualifier);
        }
        return null;
    }

    private ExpressionAnalysis analyze(SqlServerRelationSqlParser.ExpressionContext expression) {
        return analyze(expression, "");
    }

    private ExpressionAnalysis analyze(SqlServerRelationSqlParser.ExpressionContext expression, String defaultQualifier) {
        ExpressionAnalysis result = ExpressionAnalysis.empty();
        for (SqlServerRelationSqlParser.Expression_atomContext atom : expression.expression_atom()) {
            result = ExpressionAnalysis.combine(
                    expression.binary_operator().isEmpty() ? result.transform() : LineageTransformType.ARITHMETIC,
                    LineageFlowKind.VALUE,
                    result,
                    analyze(atom, defaultQualifier));
        }
        return result;
    }

    private ExpressionAnalysis analyze(SqlServerRelationSqlParser.Expression_atomContext atom, String defaultQualifier) {
        ColumnRead column = singleColumn(atom, defaultQualifier);
        if (column != null) {
            return ExpressionAnalysis.of(column, LineageTransformType.DIRECT, LineageFlowKind.VALUE);
        }
        if (atom.function_call() != null) {
            ExpressionAnalysis args = ExpressionAnalysis.empty();
            if (atom.function_call().expression_list_() != null) {
                for (SqlServerRelationSqlParser.ExpressionContext argument : atom.function_call().expression_list_().expression()) {
                    args = ExpressionAnalysis.combine(args.transform(), args.flowKind(), args, analyze(argument, defaultQualifier));
                }
            }
            String name = baseName(atom.function_call().function_name().getText()).toLowerCase(Locale.ROOT);
            LineageTransformType transform = switch (name) {
                case "sum", "avg", "count", "min", "max" -> LineageTransformType.AGGREGATE;
                case "coalesce", "isnull" -> LineageTransformType.COALESCE;
                default -> LineageTransformType.FUNCTION_CALL;
            };
            return new ExpressionAnalysis(args.sources(), ExpressionAnalysis.dominant(transform, args.transform()),
                    LineageFlowKind.VALUE);
        }
        if (atom.case_expression() != null) {
            ExpressionAnalysis combined = ExpressionAnalysis.empty();
            for (SqlServerRelationSqlParser.ExpressionContext expression : atom.case_expression().expression()) {
                combined = ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                        LineageFlowKind.CONTROL,
                        combined,
                        analyze(expression, defaultQualifier));
            }
            return new ExpressionAnalysis(combined.sources(), LineageTransformType.CASE_WHEN, LineageFlowKind.CONTROL);
        }
        if (atom.expression() != null) {
            return analyze(atom.expression(), defaultQualifier);
        }
        return ExpressionAnalysis.empty();
    }

    private String outputColumn(SqlServerRelationSqlParser.Select_list_elemContext item) {
        if (item.as_column_alias() != null) {
            return clean(item.as_column_alias().id_().getText());
        }
        ColumnRead column = singleColumn(item.expression());
        return column == null ? "" : column.column();
    }

    private String singleProjectionQualifier(SqlServerRelationSqlParser.Table_sourcesContext tableSources) {
        if (tableSources == null || tableSources.table_source().size() != 1) {
            return "";
        }
        SqlServerRelationSqlParser.Table_sourceContext source = tableSources.table_source(0);
        if (!source.table_source_suffix().isEmpty()) {
            return "";
        }
        return rowsetAlias(source.table_source_item());
    }

    private String rowsetAlias(SqlServerRelationSqlParser.Table_source_itemContext item) {
        if (item.as_table_alias() != null) {
            return clean(item.as_table_alias().table_alias().getText());
        }
        if (item.full_table_name() != null) {
            return baseName(qualifiedName(item.full_table_name()));
        }
        return "";
    }

    private String tableForAlias(SqlServerRelationSqlParser.Table_sourcesContext sources, String aliasOrTable) {
        String target = clean(aliasOrTable);
        for (SqlServerRelationSqlParser.Table_source_itemContext item : tableSourceItems(sources)) {
            if (item.full_table_name() == null) {
                continue;
            }
            String table = qualifiedName(item.full_table_name());
            String alias = item.as_table_alias() == null ? "" : clean(item.as_table_alias().table_alias().getText());
            if (target.equalsIgnoreCase(alias) || target.equalsIgnoreCase(baseName(table))) {
                return table;
            }
        }
        return aliasOrTable;
    }

    private List<SqlServerRelationSqlParser.Table_source_itemContext> tableSourceItems(SqlServerRelationSqlParser.Table_sourcesContext sources) {
        List<SqlServerRelationSqlParser.Table_source_itemContext> items = new ArrayList<>();
        for (SqlServerRelationSqlParser.Table_sourceContext source : sources.table_source()) {
            collectTableSourceItems(source, items);
        }
        return items;
    }

    private void collectTableSourceItems(
            SqlServerRelationSqlParser.Table_sourceContext source,
            List<SqlServerRelationSqlParser.Table_source_itemContext> items
    ) {
        items.add(source.table_source_item());
        for (SqlServerRelationSqlParser.Table_source_suffixContext suffix : source.table_source_suffix()) {
            if (suffix.join_on() != null) {
                collectTableSourceItems(suffix.join_on().table_source(), items);
            } else if (suffix.cross_join() != null) {
                collectTableSourceItems(suffix.cross_join().table_source(), items);
            } else if (suffix.apply_() != null) {
                collectTableSourceItems(suffix.apply_().table_source(), items);
            }
        }
    }

    private void addForeignKeyEvents(
            ParserRuleContext ctx,
            String sourceTable,
            List<String> sourceColumns,
            String targetTable,
            List<String> targetColumns
    ) {
        int count = Math.min(sourceColumns.size(), targetColumns.size());
        for (int index = 0; index < count; index++) {
            Map<String, Object> attrs = attrs();
            attrs.put("sourceTable", sourceTable);
            attrs.put("sourceColumn", sourceColumns.get(index));
            attrs.put("targetTable", targetTable);
            attrs.put("targetColumn", targetColumns.get(index));
            add(StructuredParseEventType.DDL_FOREIGN_KEY, ctx, attrs);
            addIndexEvent(sourceTable, sourceColumns.get(index), "SOURCE_INDEX", "FK_SOURCE", ctx);
            addIndexEvent(targetTable, targetColumns.get(index), "TARGET_UNIQUE", "REFERENCED_KEY", ctx);
        }
    }

    private void addIndexEvent(String table, String column, String role, String kind, ParserRuleContext ctx) {
        if (table.isBlank() || column.isBlank()) {
            return;
        }
        Map<String, Object> attrs = attrs();
        attrs.put("table", table);
        attrs.put("column", column);
        attrs.put("role", role);
        attrs.put("kind", kind);
        add(StructuredParseEventType.DDL_INDEX, ctx, attrs);
    }

    private Map<String, Object> attrs() {
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("tokenEventNative", true);
        return attrs;
    }

    private void add(StructuredParseEventType type, ParserRuleContext ctx, Map<String, Object> attrs) {
        if (ddlOnly && type != StructuredParseEventType.DDL_FOREIGN_KEY && type != StructuredParseEventType.DDL_INDEX) {
            return;
        }
        events.add(new StructuredSqlEvent(type, statement.sourceName(), line(ctx), attrs));
    }

    private long line(ParserRuleContext ctx) {
        if (ctx == null || ctx.getStart() == null) {
            return statement.startLine();
        }
        return statement.startLine() + Math.max(0, ctx.getStart().getLine() - 1);
    }

    private String currentDdlTable() {
        return ddlTables.isEmpty() ? "" : ddlTables.peek();
    }

    private String currentJoinKind() {
        return joinKinds.isEmpty() ? "WHERE_OR_UNKNOWN" : joinKinds.peek();
    }

    private String joinKind(SqlServerRelationSqlParser.Join_typeContext joinType) {
        if (joinType == null) {
            return "JOIN";
        }
        String text = joinType.getText().toUpperCase(Locale.ROOT);
        if (text.startsWith("LEFT")) {
            return "LEFT_JOIN";
        }
        if (text.startsWith("RIGHT")) {
            return "RIGHT_JOIN";
        }
        if (text.startsWith("FULL")) {
            return "FULL_JOIN";
        }
        return "JOIN";
    }

    private List<String> identifiers(SqlServerRelationSqlParser.Column_name_listContext ctx) {
        return ctx.id_().stream().map(id -> clean(id.getText())).toList();
    }

    private List<String> identifiers(SqlServerRelationSqlParser.Column_name_list_with_orderContext ctx) {
        return ctx.id_().stream().map(id -> clean(id.getText())).toList();
    }

    private String qualifiedName(SqlServerRelationSqlParser.Full_table_nameContext ctx) {
        return String.join(".", ctx.id_().stream().map(id -> clean(id.getText())).toList());
    }

    private List<String> parts(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.isBlank()) {
            return List.of();
        }
        return List.of(text.split("\\.")).stream()
                .map(this::clean)
                .filter(part -> !part.isBlank())
                .toList();
    }

    private String lastPart(String raw) {
        List<String> parts = parts(raw);
        return parts.isEmpty() ? "" : parts.get(parts.size() - 1);
    }

    private String clean(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.trim();
        while ((text.startsWith("[") && text.endsWith("]"))
                || (text.startsWith("\"") && text.endsWith("\""))) {
            text = text.substring(1, text.length() - 1).trim();
        }
        return text;
    }

    private String baseName(String qualified) {
        if (qualified == null) {
            return "";
        }
        int dot = qualified.lastIndexOf('.');
        return dot < 0 ? clean(qualified) : clean(qualified.substring(dot + 1));
    }

    private boolean isTemp(String table) {
        return clean(table).startsWith("#");
    }

    private record ColumnRead(String alias, String column) {
    }

    private record ExpressionAnalysis(
            List<ColumnRead> sources,
            LineageTransformType transform,
            LineageFlowKind flowKind
    ) {
        static ExpressionAnalysis empty() {
            return new ExpressionAnalysis(List.of(), LineageTransformType.DIRECT, LineageFlowKind.VALUE);
        }

        static ExpressionAnalysis of(ColumnRead column, LineageTransformType transform, LineageFlowKind flowKind) {
            return new ExpressionAnalysis(List.of(column), transform, flowKind);
        }

        static ExpressionAnalysis combine(
                LineageTransformType transform,
                LineageFlowKind flowKind,
                ExpressionAnalysis left,
                ExpressionAnalysis right
        ) {
            List<ColumnRead> sources = new ArrayList<>();
            sources.addAll(left.sources());
            sources.addAll(right.sources());
            return new ExpressionAnalysis(sources.stream().distinct().toList(),
                    dominant(transform, left.transform(), right.transform()),
                    flowKind);
        }

        static LineageTransformType dominant(LineageTransformType... transforms) {
            LineageTransformType dominant = LineageTransformType.DIRECT;
            for (LineageTransformType transform : transforms) {
                if (priority(transform) > priority(dominant)) {
                    dominant = transform;
                }
            }
            return dominant;
        }

        private static int priority(LineageTransformType transform) {
            return switch (transform) {
                case CASE_WHEN -> 8;
                case CUMULATIVE -> 7;
                case AGGREGATE -> 6;
                case WINDOW_DERIVED -> 5;
                case COALESCE -> 4;
                case CONCAT_FORMAT -> 3;
                case ARITHMETIC -> 2;
                case FUNCTION_CALL -> 1;
                default -> 0;
            };
        }

        List<String> aliases() {
            return sources.stream().map(ColumnRead::alias).toList();
        }

        List<String> columns() {
            return sources.stream().map(ColumnRead::column).toList();
        }
    }
}
