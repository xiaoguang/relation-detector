package com.relationdetector.sqlserver.fullgrammer.common;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.core.fullgrammer.FullGrammerTypedSqlEventSink;

/**
 * Shared SQL Server parse-tree collector.
 *
 * <p>CN: 这个 collector 只消费 ANTLR parse-tree rule context，不按 token span、
 * regex 或名字白名单判断 SQL 结构。五个 full-grammer 版本共用这层语义映射，
 * token-event 仍由自己的 grammar/visitor 独立产生事件。</p>
 */
public final class SqlServerParseTreeEventCollector extends SqlServerParseTreeSupport {
    private final SqlServerExpressionAnalyzer expressionAnalyzer;
    private final FullGrammerTypedSqlEventSink sqlSink;
    private final SqlServerDdlEventCollector ddlCollector;
    private final boolean ddlOnly;
    private final Map<String, String> rowsetOwners = new LinkedHashMap<>();
    private final Set<String> projectionOwners = new LinkedHashSet<>();
    private final Map<ProjectionKey, Boolean> directProjections = new LinkedHashMap<>();

    public SqlServerParseTreeEventCollector(
            Parser parser,
            SqlStatementRecord statement,
            boolean ddlOnly,
            com.relationdetector.core.fullgrammer.FullGrammerParseTreeAdapter parseTreeAdapter
    ) {
        super(parser);
        this.expressionAnalyzer = new SqlServerExpressionAnalyzer(parseTreeAdapter);
        this.sqlSink = new FullGrammerTypedSqlEventSink(statement, expressionAnalyzer);
        this.ddlCollector = new SqlServerDdlEventCollector(parser, statement.sourceName(), this::visit);
        this.ddlOnly = ddlOnly;
    }

    public List<StructuredSqlEvent> collect(ParserRuleContext root) {
        visit(root);
        List<StructuredSqlEvent> events = new ArrayList<>();
        if (!ddlOnly) {
            events.addAll(sqlSink.events());
        }
        events.addAll(ddlCollector.events());
        return events;
    }

    private void visit(ParseTree tree) {
        if (tree == null) {
            return;
        }
        if (!(tree instanceof ParserRuleContext ctx)) {
            return;
        }
        String rule = ruleName(ctx);
        switch (rule) {
            case "sql_clauses" -> visitSqlClause(ctx);
            case "common_table_expression" -> visitCommonTableExpression(ctx);
            case "query_specification" -> visitQuerySpecification(ctx);
            case "table_source_item" -> visitTableSourceItem(ctx);
            case "join_on" -> visitJoinOn(ctx);
            case "cross_join", "apply_" -> visitChildren(ctx);
            case "predicate" -> visitPredicate(ctx);
            case "insert_statement" -> visitInsert(ctx);
            case "update_statement" -> visitUpdate(ctx);
            case "merge_statement" -> visitMerge(ctx);
            case "create_or_alter_dml_trigger" -> visitDmlTrigger(ctx);
            case "create_table" -> ddlCollector.visitCreateTable(ctx);
            case "alter_table" -> ddlCollector.visitAlterTable(ctx);
            case "create_index" -> ddlCollector.visitCreateIndex(ctx);
            default -> visitChildren(ctx);
        }
    }

    private void visitSqlClause(ParserRuleContext ctx) {
        rowsetOwners.clear();
        projectionOwners.clear();
        directProjections.clear();
        sqlSink.withStatementScope(() -> visitChildren(ctx));
    }

    private void visitChildren(ParseTree tree) {
        for (int index = 0; index < tree.getChildCount(); index++) {
            visit(tree.getChild(index));
        }
    }

    private void visitCommonTableExpression(ParserRuleContext ctx) {
        String name = firstDirectText(ctx, "id_").orElse("");
        projectionOwners.add(normalize(name));
        sqlSink.cte(ctx, name);
        Optional<ParserRuleContext> select = firstDirect(ctx, "select_statement");
        if (select.isPresent()) {
            sqlSink.withProjectionOwner(name, () -> visit(select.get()));
        } else {
            visitChildren(ctx);
        }
    }

    private void visitQuerySpecification(ParserRuleContext ctx) {
        sqlSink.withSelectScope(() -> {
            firstDirect(ctx, "table_sources").ifPresent(this::visit);
            firstDirect(ctx, "search_condition").ifPresent(this::visit);
            emitProjectionItems(ctx);
            for (ParserRuleContext child : directChildren(ctx)) {
                String rule = ruleName(child);
                if (!rule.equals("table_sources") && !rule.equals("search_condition") && !rule.equals("select_list")) {
                    visit(child);
                }
            }
        });
    }

    private void emitProjectionItems(ParserRuleContext querySpecification) {
        String owner = sqlSink.currentProjectionOwner();
        if (owner.isBlank()) {
            return;
        }
        firstDirect(querySpecification, "select_list").ifPresent(selectList -> {
            for (ParserRuleContext item : directChildren(selectList, "select_list_elem")) {
                Optional<ParserRuleContext> expression = firstDescendant(item, "expression");
                if (expression.isEmpty()) {
                    continue;
                }
                String output = aliasForProjection(item).orElseGet(() -> lastIdentifier(expression.get().getText()));
                directProjections.put(new ProjectionKey(owner, output),
                        directColumnExpression(expression.get()).isPresent());
                sqlSink.projection(item, owner, output, expression.get());
            }
        });
    }

    private void visitTableSourceItem(ParserRuleContext ctx) {
        Optional<ParserRuleContext> fullTableName = firstDirect(ctx, "full_table_name");
        if (fullTableName.isPresent()) {
            String table = clean(fullTableName.get().getText());
            String alias = firstDirect(ctx, "as_table_alias").flatMap(this::lastIdText).orElse("");
            rowsetOwners.put(normalize(alias.isBlank() ? baseName(table) : alias), normalize(table));
            if (isLocalTemp(table)) {
                sqlSink.localTempTable(ctx, table);
            } else {
                sqlSink.rowset(ctx, "FROM", table, alias);
            }
            return;
        }
        Optional<ParserRuleContext> derived = firstDirect(ctx, "derived_table");
        if (derived.isPresent()) {
            String alias = firstDirect(ctx, "as_table_alias").flatMap(this::lastIdText).orElse("");
            if (!alias.isBlank()) {
                projectionOwners.add(normalize(alias));
                sqlSink.ignoredRowset(ctx, alias, "DERIVED_TABLE");
                sqlSink.withProjectionOwner(alias, () -> visit(derived.get()));
            } else {
                visit(derived.get());
            }
            return;
        }
        if (firstDirect(ctx, "rowset_function").isPresent()
                || firstDirect(ctx, "function_call").isPresent()
                || firstDirect(ctx, "open_xml").isPresent()
                || firstDirect(ctx, "open_json").isPresent()
                || firstDirect(ctx, "change_table").isPresent()
                || firstDirect(ctx, "nodes_method").isPresent()
                || firstDirect(ctx, "LOCAL_ID").isPresent()) {
            String alias = firstDirect(ctx, "as_table_alias").flatMap(this::lastIdText).orElse(ctx.getText());
            sqlSink.ignoredRowset(ctx, alias, "FUNCTION_ROWSET");
            return;
        }
        visitChildren(ctx);
    }

    private void visitJoinOn(ParserRuleContext ctx) {
        firstDirect(ctx, "table_source").ifPresent(this::visit);
        firstDirect(ctx, "search_condition").ifPresent(condition ->
                emitPredicateColumnEqualities(condition, "JOIN"));
    }

    private void visitPredicate(ParserRuleContext ctx) {
        if (hasDirectTerminal(ctx, "EXISTS")) {
            sqlSink.existsPredicateEqualities(ctx, ctx);
            visitChildren(ctx);
            return;
        }
        List<ParserRuleContext> expressions = directChildren(ctx, "expression");
        if (expressions.size() >= 2 && isEqualityComparison(ctx)) {
            Optional<ColumnEndpoint> left = singleColumnEndpoint(expressions.get(0));
            Optional<ColumnEndpoint> right = singleColumnEndpoint(expressions.get(1));
            if (left.isPresent() && right.isPresent()
                    && isDirectRelationshipEndpoint(left.get())
                    && isDirectRelationshipEndpoint(right.get())) {
                sqlSink.predicateEqualityColumns(ctx,
                        left.get().qualifier(), left.get().column(),
                        right.get().qualifier(), right.get().column(),
                        "WHERE_OR_UNKNOWN");
            } else {
                // Transformed projection aliases are lineage traces, not direct
                // physical-column equality endpoints.
            }
        }
        if (!expressions.isEmpty() && hasDirectTerminal(ctx, "IN")) {
            firstDirect(ctx, "subquery").ifPresent(subquery ->
                    sqlSink.inSubqueryPredicate(ctx, expressions.get(0), subquery));
        }
        visitChildren(ctx);
    }

    private void visitInsert(ParserRuleContext ctx) {
        String targetTable = firstDirectText(ctx, "ddl_object").orElse("");
        if (targetTable.isBlank() || isLocalTemp(targetTable)) {
            visitChildren(ctx);
            return;
        }
        sqlSink.writeTarget(ctx, qualifiedTable(targetTable), "");
        List<String> columns = firstDirect(ctx, "insert_column_name_list")
                .map(this::identifierList)
                .orElse(List.of());
        if (columns.isEmpty()) {
            visitChildren(ctx);
            return;
        }
        Optional<ParserRuleContext> value = firstDirect(ctx, "insert_statement_value");
        Optional<ParserRuleContext> selectList = value.flatMap(v -> firstDescendant(v, "select_list"));
        if (selectList.isEmpty()) {
            visitChildren(ctx);
            return;
        }
        firstDirect(ctx, "with_expression").ifPresent(this::visit);
        value.ifPresent(this::visit);
        List<ParserRuleContext> items = directChildren(selectList.get(), "select_list_elem");
        int count = Math.min(columns.size(), items.size());
        for (int i = 0; i < count; i++) {
            ParserRuleContext item = items.get(i);
            String column = columns.get(i);
            String target = qualifiedTable(targetTable);
            firstDescendant(item, "expression").ifPresent(expression ->
                    sqlSink.insertSelect(item, "", target, column, expression));
        }
    }

    private void visitUpdate(ParserRuleContext ctx) {
        String targetTable = firstDirectText(ctx, "ddl_object").orElse("");
        if (targetTable.isBlank() || isLocalTemp(targetTable)) {
            visitChildren(ctx);
            return;
        }
        Optional<ParserRuleContext> tableSources = firstDirect(ctx, "table_sources");
        String resolvedTarget = tableSources
                .flatMap(sources -> tableForAlias(sources, targetTable))
                .orElse(targetTable);
        String qualifiedTarget = qualifiedTable(resolvedTarget);
        sqlSink.writeTarget(ctx, qualifiedTarget, "");
        sqlSink.withWriteTarget(qualifiedTarget, () -> {
            tableSources.ifPresent(this::visit);
            firstDirect(ctx, "search_condition").ifPresent(this::visit);
            for (ParserRuleContext element : directChildren(ctx, "update_elem")) {
                emitUpdateElement(element, qualifiedTarget, false);
            }
        });
    }

    private void visitMerge(ParserRuleContext ctx) {
        String targetTable = firstDirectText(ctx, "ddl_object").orElse("");
        String targetAlias = firstDirect(ctx, "as_table_alias").flatMap(this::lastIdText).orElse("");
        if (!targetTable.isBlank() && !isLocalTemp(targetTable)) {
            sqlSink.writeTarget(ctx, qualifiedTable(targetTable), targetAlias);
        }
        firstDirect(ctx, "table_sources").ifPresent(this::visit);
        firstDirect(ctx, "search_condition").ifPresent(condition ->
                emitPredicateColumnEqualities(condition, "MERGE_ON"));
        for (ParserRuleContext element : descendants(ctx, "update_elem_merge")) {
            emitUpdateElement(element, targetTable, true);
        }
        for (ParserRuleContext notMatched : descendants(ctx, "merge_not_matched")) {
            List<String> columns = firstDirect(notMatched, "column_name_list").map(this::identifierList).orElse(List.of());
            List<ParserRuleContext> values = firstDescendant(notMatched, "expression_list_")
                    .map(exprList -> directChildren(exprList, "expression"))
                    .orElse(List.of());
            int count = Math.min(columns.size(), values.size());
            for (int i = 0; i < count; i++) {
                sqlSink.mergeInsert(notMatched, targetAlias, qualifiedTable(targetTable), columns.get(i), values.get(i));
            }
        }
        visitChildren(ctx);
    }

    private void visitDmlTrigger(ParserRuleContext ctx) {
        String targetTable = firstDirectText(ctx, "table_name").orElse("");
        sqlSink.triggerPseudoRowset(ctx, "inserted", targetTable);
        sqlSink.triggerPseudoRowset(ctx, "deleted", targetTable);
        visitChildren(ctx);
    }

    private void emitUpdateElement(ParserRuleContext element, String targetTable, boolean merge) {
        List<ParserRuleContext> columns = directChildren(element, "full_column_name");
        List<ParserRuleContext> expressions = directChildren(element, "expression");
        if (columns.isEmpty() || expressions.isEmpty()) {
            return;
        }
        String targetColumn = lastIdentifier(columns.get(0).getText());
        ParserRuleContext expression = expressions.get(expressions.size() - 1);
        // Scalar subqueries carry their own rowsets and predicates. Visit only
        // those typed subquery contexts; traversing the whole assignment would
        // incorrectly promote CASE predicates to structural relationships.
        visitScalarSubqueries(expression);
        if (merge) {
            sqlSink.mergeUpdate(element, "", qualifiedTable(targetTable), targetColumn, expression);
        } else {
            sqlSink.updateAssignment(element, "", qualifiedTable(targetTable), targetColumn, expression);
        }
    }

    private void visitScalarSubqueries(ParseTree tree) {
        if (tree instanceof ParserRuleContext ctx && ruleName(ctx).equals("subquery")) {
            visit(ctx);
            return;
        }
        for (int index = 0; index < tree.getChildCount(); index++) {
            visitScalarSubqueries(tree.getChild(index));
        }
    }

    private Optional<String> aliasForProjection(ParserRuleContext item) {
        return firstDescendant(item, "as_column_alias").flatMap(this::lastIdText);
    }

    private void emitPredicateColumnEqualities(ParseTree tree, String joinKind) {
        if (tree instanceof ParserRuleContext ctx && ruleName(ctx).equals("predicate")) {
            List<ParserRuleContext> expressions = directChildren(ctx, "expression");
            if (expressions.size() >= 2 && isEqualityComparison(ctx)) {
                Optional<ColumnEndpoint> left = singleColumnEndpoint(expressions.get(0));
                Optional<ColumnEndpoint> right = singleColumnEndpoint(expressions.get(1));
                if (left.isPresent() && right.isPresent()
                        && isDirectRelationshipEndpoint(left.get())
                        && isDirectRelationshipEndpoint(right.get())) {
                    sqlSink.predicateEqualityColumns(ctx,
                            left.get().qualifier(), left.get().column(),
                            right.get().qualifier(), right.get().column(),
                            joinKind);
                }
            }
        }
        for (int index = 0; index < tree.getChildCount(); index++) {
            emitPredicateColumnEqualities(tree.getChild(index), joinKind);
        }
    }

    private boolean isDirectRelationshipEndpoint(ColumnEndpoint endpoint) {
        String qualifier = normalize(endpoint.qualifier());
        String owner = rowsetOwners.getOrDefault(qualifier, qualifier);
        if (!projectionOwners.contains(owner)) {
            return true;
        }
        return Boolean.TRUE.equals(directProjections.get(new ProjectionKey(owner, endpoint.column())));
    }

    private String normalize(String value) {
        return clean(value).toLowerCase(Locale.ROOT);
    }

    private record ProjectionKey(String owner, String column) {
        private ProjectionKey {
            owner = owner == null ? "" : owner.toLowerCase(Locale.ROOT);
            column = column == null ? "" : column.toLowerCase(Locale.ROOT);
        }
    }
}
