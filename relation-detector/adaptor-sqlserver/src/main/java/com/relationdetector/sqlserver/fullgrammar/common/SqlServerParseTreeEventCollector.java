package com.relationdetector.sqlserver.fullgrammar.common;

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
import com.relationdetector.core.fullgrammar.FullGrammarEventFacade;
import com.relationdetector.core.fullgrammar.FullGrammarParseTreeAdapter;
import com.relationdetector.core.fullgrammar.FullGrammarParseTreeAdapter.Role;

/**
 * Shared SQL Server parse-tree collector.
 *
 * <p>CN: 这个 collector 只消费 ANTLR parse-tree rule context，不按 token span、
 * regex 或名字白名单判断 SQL 结构。五个 full-grammar 版本共用这层语义映射，
 * token-event 仍由自己的 grammar/visitor 独立产生事件。</p>
 */
public final class SqlServerParseTreeEventCollector extends SqlServerParseTreeSupport {
    private final SqlServerExpressionAnalyzer expressionAnalyzer;
    private final AbstractSqlServerParseTreeAdapter sqlServerAdapter;
    private final FullGrammarEventFacade sqlSink;
    private final SqlServerDdlEventCollector ddlCollector;
    private final boolean ddlOnly;
    private final Map<String, String> rowsetOwners = new LinkedHashMap<>();
    private final Set<String> projectionOwners = new LinkedHashSet<>();
    private final Map<SqlServerProjectionKey, Boolean> directProjections = new LinkedHashMap<>();
    private int existsDepth;

    public SqlServerParseTreeEventCollector(
            SqlStatementRecord statement,
            boolean ddlOnly,
            FullGrammarParseTreeAdapter parseTreeAdapter
    ) {
        super(parseTreeAdapter);
        if (!(parseTreeAdapter instanceof AbstractSqlServerParseTreeAdapter typedAdapter)) {
            throw new IllegalArgumentException("SQL Server collector requires a typed SQL Server context adapter");
        }
        this.sqlServerAdapter = typedAdapter;
        this.expressionAnalyzer = new SqlServerExpressionAnalyzer(parseTreeAdapter);
        this.sqlSink = new FullGrammarEventFacade(statement, expressionAnalyzer);
        this.ddlCollector = new SqlServerDdlEventCollector(parseTreeAdapter, statement.sourceName(), this::visit);
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
        if (hasRole(ctx, Role.SQL_CLAUSE)) {
            visitSqlClause(ctx);
        } else if (hasRole(ctx, Role.COMMON_TABLE_EXPRESSION)) {
            visitCommonTableExpression(ctx);
        } else if (hasRole(ctx, Role.QUERY_SPECIFICATION)) {
            visitQuerySpecification(ctx);
        } else if (hasRole(ctx, Role.TABLE_SOURCE_ITEM)) {
            visitTableSourceItem(ctx);
        } else if (hasRole(ctx, Role.JOIN_ON)) {
            visitJoinOn(ctx);
        } else if (hasRole(ctx, Role.CROSS_JOIN) || hasRole(ctx, Role.APPLY)) {
            visitChildren(ctx);
        } else if (hasRole(ctx, Role.PREDICATE)) {
            visitPredicate(ctx);
        } else if (hasRole(ctx, Role.INSERT_STATEMENT)) {
            visitInsert(ctx);
        } else if (hasRole(ctx, Role.UPDATE_STATEMENT)) {
            visitUpdate(ctx);
        } else if (hasRole(ctx, Role.MERGE_STATEMENT)) {
            visitMerge(ctx);
        } else if (hasRole(ctx, Role.DML_TRIGGER)) {
            visitDmlTrigger(ctx);
        } else if (hasRole(ctx, Role.CREATE_TABLE)) {
            ddlCollector.visitCreateTable(ctx);
        } else if (hasRole(ctx, Role.ALTER_TABLE)) {
            ddlCollector.visitAlterTable(ctx);
        } else if (hasRole(ctx, Role.CREATE_INDEX)) {
            ddlCollector.visitCreateIndex(ctx);
        } else {
            visitChildren(ctx);
        }
    }

    private void visitSqlClause(ParserRuleContext ctx) {
        rowsetOwners.clear();
        projectionOwners.clear();
        directProjections.clear();
        sqlSink.withStatementScope(() -> visitChildren(ctx));
    }

    private void visitChildren(ParseTree tree) {
        for (ParseTree child : typedChildren(tree)) {
            visit(child);
        }
    }

    private void visitCommonTableExpression(ParserRuleContext ctx) {
        String name = firstDirectText(ctx, Role.IDENTIFIER).orElse("");
        projectionOwners.add(normalize(name));
        sqlSink.cte(ctx, name);
        Optional<ParserRuleContext> select = firstDirect(ctx, Role.SELECT_STATEMENT);
        if (select.isPresent()) {
            sqlSink.withProjectionOwner(name, () -> visit(select.get()));
        } else {
            visitChildren(ctx);
        }
    }

    private void visitQuerySpecification(ParserRuleContext ctx) {
        sqlSink.withSelectScope(() -> {
            firstDirect(ctx, Role.TABLE_SOURCES).ifPresent(this::visit);
            firstDirect(ctx, Role.SEARCH_CONDITION).ifPresent(this::visit);
            emitProjectionItems(ctx);
            for (ParserRuleContext child : directChildren(ctx)) {
                if (!hasRole(child, Role.TABLE_SOURCES)
                        && !hasRole(child, Role.SEARCH_CONDITION)
                        && !hasRole(child, Role.SELECT_LIST)) {
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
        firstDirect(querySpecification, Role.SELECT_LIST).ifPresent(selectList -> {
            for (ParserRuleContext item : directChildren(selectList, Role.SELECT_TARGET_ITEM)) {
                Optional<ParserRuleContext> expression = firstDescendant(item, Role.EXPRESSION);
                if (expression.isEmpty()) {
                    continue;
                }
                String output = aliasForProjection(item).orElseGet(() -> directColumnExpression(expression.get())
                        .map(com.relationdetector.core.fullgrammar.FullGrammarColumnReference::column)
                        .orElse(""));
                if (output.isBlank()) {
                    continue;
                }
                directProjections.put(new SqlServerProjectionKey(owner, output),
                        directColumnExpression(expression.get()).isPresent());
                sqlSink.projection(item, owner, output, expression.get());
            }
        });
    }

    private void visitTableSourceItem(ParserRuleContext ctx) {
        Optional<ParserRuleContext> fullTableName = firstDirect(ctx, Role.FULL_TABLE_NAME);
        if (fullTableName.isPresent()) {
            String table = clean(fullTableName.get().getText());
            String alias = firstDirect(ctx, Role.TABLE_ALIAS).flatMap(this::lastIdText).orElse("");
            rowsetOwners.put(normalize(alias.isBlank() ? baseName(table) : alias), normalize(table));
            if (isLocalTemp(table)) {
                sqlSink.localTempTable(ctx, table);
            } else {
                sqlSink.rowset(ctx, "FROM", table, alias);
            }
            return;
        }
        Optional<ParserRuleContext> derived = firstDirect(ctx, Role.DERIVED_TABLE);
        if (derived.isPresent()) {
            String alias = firstDirect(ctx, Role.TABLE_ALIAS).flatMap(this::lastIdText).orElse("");
            if (!alias.isBlank()) {
                projectionOwners.add(normalize(alias));
                sqlSink.ignoredRowset(ctx, alias, "DERIVED_TABLE");
                sqlSink.withProjectionOwner(alias, () -> visit(derived.get()));
            } else {
                visit(derived.get());
            }
            return;
        }
        if (firstDirect(ctx, Role.ROWSET_FUNCTION).isPresent()
                || firstDirect(ctx, Role.FUNCTION_CALL).isPresent()
                || firstDirect(ctx, Role.OPEN_XML).isPresent()
                || firstDirect(ctx, Role.OPEN_JSON).isPresent()
                || firstDirect(ctx, Role.CHANGE_TABLE).isPresent()
                || firstDirect(ctx, Role.NODES_METHOD).isPresent()) {
            String alias = firstDirect(ctx, Role.TABLE_ALIAS).flatMap(this::lastIdText).orElse("");
            sqlSink.ignoredRowset(ctx, alias, "FUNCTION_ROWSET");
            return;
        }
        visitChildren(ctx);
    }

    private void visitJoinOn(ParserRuleContext ctx) {
        firstDirect(ctx, Role.TABLE_SOURCE).ifPresent(this::visit);
        firstDirect(ctx, Role.SEARCH_CONDITION).ifPresent(condition ->
                emitPredicateColumnEqualities(condition, sqlServerAdapter.joinKind(ctx)));
    }

    private void visitPredicate(ParserRuleContext ctx) {
        if (isExistsPredicate(ctx)) {
            existsDepth++;
            visitChildren(ctx);
            existsDepth--;
            return;
        }
        List<ParserRuleContext> expressions = directChildren(ctx, Role.EXPRESSION);
        for (FullGrammarParseTreeAdapter.EqualityOperands equality : directEqualities(ctx)) {
            Optional<ColumnEndpoint> left = singleColumnEndpoint(equality.left());
            Optional<ColumnEndpoint> right = singleColumnEndpoint(equality.right());
            if (left.isPresent() && right.isPresent()
                    && isDirectRelationshipEndpoint(left.get())
                    && isDirectRelationshipEndpoint(right.get())) {
                emitEquality(ctx, left.get(), right.get(), "WHERE_OR_UNKNOWN");
            }
        }
        if (!expressions.isEmpty() && isInPredicate(ctx)) {
            firstDirect(ctx, Role.SCALAR_SUBQUERY).ifPresent(subquery ->
                    sqlSink.inSubqueryPredicate(ctx, expressions.get(0), subquery));
        }
        visitChildren(ctx);
    }

    private void visitInsert(ParserRuleContext ctx) {
        String targetTable = firstDirectText(ctx, Role.DDL_OBJECT).orElse("");
        if (targetTable.isBlank() || isLocalTemp(targetTable)) {
            visitChildren(ctx);
            return;
        }
        sqlSink.writeTarget(ctx, qualifiedTable(targetTable), "");
        List<String> columns = firstDirect(ctx, Role.INSERT_COLUMN_LIST)
                .map(this::identifierList)
                .orElse(List.of());
        if (columns.isEmpty()) {
            visitChildren(ctx);
            return;
        }
        Optional<ParserRuleContext> value = firstDirect(ctx, Role.INSERT_VALUE);
        Optional<ParserRuleContext> selectList = value.flatMap(v -> firstDescendant(v, Role.SELECT_LIST));
        if (selectList.isEmpty()) {
            visitChildren(ctx);
            return;
        }
        firstDirect(ctx, Role.WITH_EXPRESSION).ifPresent(this::visit);
        value.ifPresent(this::visit);
        List<ParserRuleContext> items = directChildren(selectList.get(), Role.SELECT_TARGET_ITEM);
        int count = Math.min(columns.size(), items.size());
        for (int i = 0; i < count; i++) {
            ParserRuleContext item = items.get(i);
            String column = columns.get(i);
            String target = qualifiedTable(targetTable);
            firstDescendant(item, Role.EXPRESSION).ifPresent(expression ->
                    sqlSink.insertSelect(item, "", target, column, expression));
        }
    }

    private void visitUpdate(ParserRuleContext ctx) {
        String targetTable = firstDirectText(ctx, Role.DDL_OBJECT).orElse("");
        if (targetTable.isBlank() || isLocalTemp(targetTable)) {
            visitChildren(ctx);
            return;
        }
        Optional<ParserRuleContext> tableSources = firstDirect(ctx, Role.TABLE_SOURCES);
        String resolvedTarget = tableSources
                .flatMap(sources -> tableForAlias(sources, targetTable))
                .orElse(targetTable);
        String qualifiedTarget = qualifiedTable(resolvedTarget);
        sqlSink.writeTarget(ctx, qualifiedTarget, "");
        sqlSink.withWriteTarget(qualifiedTarget, () -> {
            tableSources.ifPresent(this::visit);
            firstDirect(ctx, Role.SEARCH_CONDITION).ifPresent(this::visit);
            for (ParserRuleContext element : directChildren(ctx, Role.UPDATE_ELEMENT)) {
                emitUpdateElement(element, qualifiedTarget, false);
            }
        });
    }

    private void visitMerge(ParserRuleContext ctx) {
        String targetTable = firstDirectText(ctx, Role.DDL_OBJECT).orElse("");
        String targetAlias = firstDirect(ctx, Role.TABLE_ALIAS).flatMap(this::lastIdText).orElse("");
        if (!targetTable.isBlank() && !isLocalTemp(targetTable)) {
            sqlSink.writeTarget(ctx, qualifiedTable(targetTable), targetAlias);
        }
        firstDirect(ctx, Role.TABLE_SOURCES).ifPresent(this::visit);
        firstDirect(ctx, Role.SEARCH_CONDITION).ifPresent(condition ->
                emitPredicateColumnEqualities(condition, "MERGE_ON"));
        for (ParserRuleContext element : descendants(ctx, Role.MERGE_UPDATE_ELEMENT)) {
            emitUpdateElement(element, targetTable, true);
        }
        for (ParserRuleContext notMatched : descendants(ctx, Role.MERGE_NOT_MATCHED)) {
            List<String> columns = firstDirect(notMatched, Role.COLUMN_LIST).map(this::identifierList).orElse(List.of());
            List<ParserRuleContext> values = firstDescendant(notMatched, Role.EXPRESSION_LIST)
                    .map(exprList -> directChildren(exprList, Role.EXPRESSION))
                    .orElse(List.of());
            int count = Math.min(columns.size(), values.size());
            for (int i = 0; i < count; i++) {
                sqlSink.mergeInsert(values.get(i), targetAlias, qualifiedTable(targetTable), columns.get(i), values.get(i));
            }
        }
        visitChildren(ctx);
    }

    private void visitDmlTrigger(ParserRuleContext ctx) {
        String targetTable = firstDirectText(ctx, Role.TABLE_NAME).orElse("");
        sqlSink.triggerPseudoRowset(ctx, "inserted", targetTable);
        sqlSink.triggerPseudoRowset(ctx, "deleted", targetTable);
        visitChildren(ctx);
    }

    private void emitUpdateElement(ParserRuleContext element, String targetTable, boolean merge) {
        List<ParserRuleContext> columns = directChildren(element, Role.COLUMN_REFERENCE);
        List<ParserRuleContext> expressions = directChildren(element, Role.EXPRESSION);
        if (columns.isEmpty() || expressions.isEmpty()) {
            return;
        }
        String targetColumn = lastIdentifier(columns.get(0).getText());
        ParserRuleContext expression = expressions.get(expressions.size() - 1);
        // Scalar subqueries carry their own rowsets and predicates. Visit only
        // those typed subquery contexts; traversing the whole assignment would
        // incorrectly promote CASE predicates to structural relationships.
        SqlServerTypedSubqueryWalker.visit(expression, sqlServerAdapter,
                candidate -> hasRole(candidate, Role.SCALAR_SUBQUERY), this::visit);
        if (merge) {
            sqlSink.mergeUpdate(element, "", qualifiedTable(targetTable), targetColumn, expression);
        } else {
            sqlSink.updateAssignment(element, "", qualifiedTable(targetTable), targetColumn, expression);
        }
    }

    private Optional<String> aliasForProjection(ParserRuleContext item) {
        return firstDescendant(item, Role.COLUMN_ALIAS).flatMap(this::lastIdText);
    }

    private void emitPredicateColumnEqualities(ParseTree tree, String joinKind) {
        if (tree instanceof ParserRuleContext ctx && hasRole(ctx, Role.PREDICATE)) {
            if (isExistsPredicate(ctx)) {
                sqlSink.existsPredicateEqualities(ctx, ctx);
                return;
            }
            for (FullGrammarParseTreeAdapter.EqualityOperands equality : directEqualities(ctx)) {
                Optional<ColumnEndpoint> left = singleColumnEndpoint(equality.left());
                Optional<ColumnEndpoint> right = singleColumnEndpoint(equality.right());
                if (left.isPresent() && right.isPresent()
                        && isDirectRelationshipEndpoint(left.get())
                        && isDirectRelationshipEndpoint(right.get())) {
                    emitEquality(ctx, left.get(), right.get(), joinKind);
                }
            }
        }
        for (ParseTree child : typedChildren(tree)) {
            emitPredicateColumnEqualities(child, joinKind);
        }
    }

    private void emitEquality(
            ParserRuleContext ctx,
            ColumnEndpoint left,
            ColumnEndpoint right,
            String joinKind
    ) {
        if (existsDepth > 0) {
            sqlSink.existsPredicateEqualityColumns(ctx,
                    left.qualifier(), left.column(), right.qualifier(), right.column());
        } else {
            sqlSink.predicateEqualityColumns(ctx,
                    left.qualifier(), left.column(), right.qualifier(), right.column(), joinKind);
        }
    }

    private boolean isDirectRelationshipEndpoint(ColumnEndpoint endpoint) {
        String qualifier = normalize(endpoint.qualifier());
        String owner = rowsetOwners.getOrDefault(qualifier, qualifier);
        if (!projectionOwners.contains(owner)) {
            return true;
        }
        return Boolean.TRUE.equals(directProjections.get(new SqlServerProjectionKey(owner, endpoint.column())));
    }

    private String normalize(String value) {
        return clean(value).toLowerCase(Locale.ROOT);
    }

}
