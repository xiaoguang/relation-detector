package com.relationdetector.sqlserver.fullgrammer.common;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.core.ddl.DdlEventBuilder;
import com.relationdetector.core.fullgrammer.FullGrammerTypedSqlEventSink;

/**
 * Shared SQL Server parse-tree collector.
 *
 * <p>CN: 这个 collector 只消费 ANTLR parse-tree rule context，不按 token span、
 * regex 或名字白名单判断 SQL 结构。五个 full-grammer 版本和 token-event 可以共用
 * 这层语义映射，差异仍由各自 generated parser 负责。</p>
 */
public final class SqlServerParseTreeEventCollector {
    private final Parser parser;
    private final FullGrammerTypedSqlEventSink sqlSink;
    private final DdlEventBuilder ddlBuilder;
    private final boolean ddlOnly;

    public SqlServerParseTreeEventCollector(Parser parser, SqlStatementRecord statement, boolean ddlOnly) {
        this.parser = parser;
        this.sqlSink = new FullGrammerTypedSqlEventSink(statement, new SqlServerExpressionAnalyzer());
        this.ddlBuilder = new DdlEventBuilder(statement.sourceName());
        this.ddlOnly = ddlOnly;
    }

    public List<StructuredSqlEvent> collect(ParserRuleContext root) {
        visit(root);
        List<StructuredSqlEvent> events = new ArrayList<>();
        if (!ddlOnly) {
            events.addAll(sqlSink.events());
        }
        events.addAll(ddlBuilder.events());
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
            case "create_table" -> visitCreateTable(ctx);
            case "alter_table" -> visitAlterTable(ctx);
            case "create_index" -> visitCreateIndex(ctx);
            default -> visitChildren(ctx);
        }
    }

    private void visitChildren(ParseTree tree) {
        for (int index = 0; index < tree.getChildCount(); index++) {
            visit(tree.getChild(index));
        }
    }

    private void visitCommonTableExpression(ParserRuleContext ctx) {
        String name = firstDirectText(ctx, "id_").orElse("");
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
                sqlSink.projection(item, owner, output, expression.get());
            }
        });
    }

    private void visitTableSourceItem(ParserRuleContext ctx) {
        Optional<ParserRuleContext> fullTableName = firstDirect(ctx, "full_table_name");
        if (fullTableName.isPresent()) {
            String table = clean(fullTableName.get().getText());
            String alias = firstDirect(ctx, "as_table_alias").flatMap(this::lastIdText).orElse("");
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
        if (expressions.size() >= 2 && firstDirect(ctx, "comparison_operator").isPresent()) {
            Optional<ColumnEndpoint> left = singleColumnEndpoint(expressions.get(0));
            Optional<ColumnEndpoint> right = singleColumnEndpoint(expressions.get(1));
            if (left.isPresent() && right.isPresent()) {
                sqlSink.predicateEqualityColumns(ctx,
                        left.get().qualifier(), left.get().column(),
                        right.get().qualifier(), right.get().column(),
                        "WHERE_OR_UNKNOWN");
            } else {
                sqlSink.predicateEquality(ctx, expressions.get(0), expressions.get(1), "WHERE_OR_UNKNOWN");
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
        if (merge) {
            sqlSink.mergeUpdate(element, "", qualifiedTable(targetTable), targetColumn, expression);
        } else {
            sqlSink.updateAssignment(element, "", qualifiedTable(targetTable), targetColumn, expression);
        }
    }

    private void visitCreateTable(ParserRuleContext ctx) {
        String table = firstDirectText(ctx, "table_name").orElse("");
        if (table.isBlank() || isLocalTemp(table)) {
            return;
        }
        for (ParserRuleContext column : descendants(ctx, "column_definition")) {
            String columnName = firstDirectText(column, "id_").orElse("");
            if (columnName.isBlank()) {
                continue;
            }
            ddlBuilder.addColumn(qualifiedTable(table), columnName, line(column));
            if (containsDirectKeyword(column, "PRIMARY") || containsDirectKeyword(column, "UNIQUE")) {
                ddlBuilder.addIndex(qualifiedTable(table), columnName, "TARGET_UNIQUE", "INLINE_CONSTRAINT", line(column));
            }
            firstDescendant(column, "foreign_key_options").ifPresent(fk -> {
                String targetTable = firstDirectText(fk, "table_name").orElse("");
                List<String> targetColumns = firstDirect(fk, "column_name_list").map(this::identifierList).orElse(List.of());
                if (!targetTable.isBlank() && !targetColumns.isEmpty()) {
                    ddlBuilder.addForeignKey(qualifiedTable(table), List.of(columnName), qualifiedTable(targetTable), targetColumns, line(fk));
                    ddlBuilder.addIndex(qualifiedTable(table), columnName, "SOURCE_INDEX", "IMPLICIT_FK_SOURCE", line(fk));
                    targetColumns.forEach(targetColumn ->
                            ddlBuilder.addIndex(qualifiedTable(targetTable), targetColumn, "TARGET_UNIQUE", "REFERENCED_KEY", line(fk)));
                }
            });
        }
        for (ParserRuleContext constraint : descendants(ctx, "table_constraint")) {
            if (containsDirectKeyword(constraint, "FOREIGN")) {
                List<ParserRuleContext> lists = directChildren(constraint, "column_name_list");
                Optional<ParserRuleContext> fkOptions = firstDirect(constraint, "foreign_key_options");
                String targetTable = fkOptions.flatMap(fk -> firstDirectText(fk, "table_name")).orElse("");
                List<String> sourceColumns = lists.isEmpty() ? List.of() : identifierList(lists.get(0));
                List<String> targetColumns = fkOptions.flatMap(fk -> firstDirect(fk, "column_name_list"))
                        .map(this::identifierList)
                        .orElse(List.of());
                if (!targetTable.isBlank() && !sourceColumns.isEmpty() && !targetColumns.isEmpty()) {
                    ddlBuilder.addForeignKey(qualifiedTable(table), sourceColumns, qualifiedTable(targetTable), targetColumns, line(constraint));
                    sourceColumns.forEach(sourceColumn ->
                            ddlBuilder.addIndex(qualifiedTable(table), sourceColumn, "SOURCE_INDEX", "FK_SOURCE", line(constraint)));
                    targetColumns.forEach(targetColumn ->
                            ddlBuilder.addIndex(qualifiedTable(targetTable), targetColumn, "TARGET_UNIQUE", "REFERENCED_KEY", line(constraint)));
                }
            } else if (containsDirectKeyword(constraint, "PRIMARY") || containsDirectKeyword(constraint, "UNIQUE")) {
                firstDirect(constraint, "column_name_list_with_order").map(this::identifierList).orElse(List.of())
                        .forEach(column -> ddlBuilder.addIndex(qualifiedTable(table), column, "TARGET_UNIQUE", "TABLE_CONSTRAINT", line(constraint)));
            }
        }
    }

    private void visitAlterTable(ParserRuleContext ctx) {
        String table = firstDirectText(ctx, "table_name").orElse("");
        if (table.isBlank() || isLocalTemp(table)) {
            visitChildren(ctx);
            return;
        }
        for (ParserRuleContext column : descendants(ctx, "column_definition")) {
            String columnName = firstDirectText(column, "id_").orElse("");
            if (!columnName.isBlank()) {
                ddlBuilder.addColumn(qualifiedTable(table), columnName, line(column));
            }
        }
        visitChildren(ctx);
    }

    private void visitCreateIndex(ParserRuleContext ctx) {
        String table = firstDirectText(ctx, "table_name").orElse("");
        if (table.isBlank()) {
            return;
        }
        String role = containsDirectKeyword(ctx, "UNIQUE") ? "TARGET_UNIQUE" : "SOURCE_INDEX";
        firstDirect(ctx, "column_name_list_with_order").map(this::identifierList).orElse(List.of())
                .forEach(column -> ddlBuilder.addIndex(qualifiedTable(table), column, role, "CREATE_INDEX", line(ctx)));
    }

    private Optional<String> aliasForProjection(ParserRuleContext item) {
        return firstDescendant(item, "as_column_alias").flatMap(this::lastIdText);
    }

    private Optional<String> tableForAlias(ParseTree tree, String aliasOrTable) {
        String target = clean(aliasOrTable);
        if (target.isBlank()) {
            return Optional.empty();
        }
        for (ParserRuleContext item : descendants(tree, "table_source_item")) {
            Optional<ParserRuleContext> tableName = firstDirect(item, "full_table_name");
            if (tableName.isEmpty()) {
                continue;
            }
            String alias = firstDirect(item, "as_table_alias").flatMap(this::lastIdText).orElse("");
            String table = clean(tableName.get().getText());
            if (target.equalsIgnoreCase(alias) || target.equalsIgnoreCase(baseName(table))) {
                return Optional.of(table);
            }
        }
        return Optional.empty();
    }

    private void emitPredicateColumnEqualities(ParseTree tree, String joinKind) {
        if (tree instanceof ParserRuleContext ctx && ruleName(ctx).equals("predicate")) {
            List<ParserRuleContext> expressions = directChildren(ctx, "expression");
            if (expressions.size() >= 2 && firstDirect(ctx, "comparison_operator").isPresent()) {
                Optional<ColumnEndpoint> left = singleColumnEndpoint(expressions.get(0));
                Optional<ColumnEndpoint> right = singleColumnEndpoint(expressions.get(1));
                if (left.isPresent() && right.isPresent()) {
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

    private Optional<ColumnEndpoint> singleColumnEndpoint(ParseTree expression) {
        List<ParserRuleContext> columns = descendants(expression, "full_column_name");
        if (columns.size() != 1) {
            return Optional.empty();
        }
        List<String> parts = splitQualified(clean(columns.get(0).getText())).stream()
                .map(this::cleanOne)
                .filter(part -> !part.isBlank())
                .toList();
        if (parts.size() < 2) {
            return Optional.empty();
        }
        return Optional.of(new ColumnEndpoint(parts.get(parts.size() - 2), parts.get(parts.size() - 1)));
    }

    private Optional<String> lastIdText(ParserRuleContext ctx) {
        List<ParserRuleContext> ids = descendants(ctx, "id_");
        if (ids.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(clean(ids.get(ids.size() - 1).getText()));
    }

    private Optional<ParserRuleContext> firstDirect(ParserRuleContext ctx, String ruleName) {
        for (ParserRuleContext child : directChildren(ctx)) {
            if (ruleName(child).equals(ruleName)) {
                return Optional.of(child);
            }
        }
        return Optional.empty();
    }

    private Optional<String> firstDirectText(ParserRuleContext ctx, String ruleName) {
        return firstDirect(ctx, ruleName).map(child -> clean(child.getText()));
    }

    private Optional<ParserRuleContext> firstDescendant(ParseTree tree, String ruleName) {
        if (tree instanceof ParserRuleContext ctx && ruleName(ctx).equals(ruleName)) {
            return Optional.of(ctx);
        }
        for (int index = 0; index < tree.getChildCount(); index++) {
            Optional<ParserRuleContext> match = firstDescendant(tree.getChild(index), ruleName);
            if (match.isPresent()) {
                return match;
            }
        }
        return Optional.empty();
    }

    private List<ParserRuleContext> descendants(ParseTree tree, String ruleName) {
        List<ParserRuleContext> result = new ArrayList<>();
        collectDescendants(tree, ruleName, result);
        return result;
    }

    private void collectDescendants(ParseTree tree, String ruleName, List<ParserRuleContext> result) {
        if (tree instanceof ParserRuleContext ctx && ruleName(ctx).equals(ruleName)) {
            result.add(ctx);
        }
        for (int index = 0; index < tree.getChildCount(); index++) {
            collectDescendants(tree.getChild(index), ruleName, result);
        }
    }

    private List<ParserRuleContext> directChildren(ParserRuleContext ctx) {
        List<ParserRuleContext> result = new ArrayList<>();
        for (int index = 0; index < ctx.getChildCount(); index++) {
            if (ctx.getChild(index) instanceof ParserRuleContext child) {
                result.add(child);
            }
        }
        return result;
    }

    private List<ParserRuleContext> directChildren(ParserRuleContext ctx, String ruleName) {
        return directChildren(ctx).stream().filter(child -> ruleName(child).equals(ruleName)).toList();
    }

    private List<String> identifierList(ParserRuleContext ctx) {
        List<String> values = new ArrayList<>();
        for (ParserRuleContext id : descendants(ctx, "id_")) {
            String value = clean(id.getText());
            if (!value.isBlank()) {
                values.add(value);
            }
        }
        if (!values.isEmpty()) {
            return values;
        }
        return splitTopLevelIdentifiers(ctx.getText());
    }

    private List<String> splitTopLevelIdentifiers(String text) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        for (int index = 0; index < text.length(); index++) {
            char ch = text.charAt(index);
            if (ch == '(') {
                depth++;
            } else if (ch == ')' && depth > 0) {
                depth--;
            }
            if (ch == ',' && depth == 0) {
                addIdentifier(values, current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        addIdentifier(values, current.toString());
        return values;
    }

    private void addIdentifier(List<String> values, String raw) {
        String value = lastIdentifier(raw);
        if (!value.isBlank()) {
            values.add(value);
        }
    }

    private boolean hasDirectTerminal(ParserRuleContext ctx, String text) {
        return directTerminalTexts(ctx).stream().anyMatch(token -> token.equalsIgnoreCase(text));
    }

    private boolean containsDirectKeyword(ParserRuleContext ctx, String text) {
        return hasDirectTerminal(ctx, text);
    }

    private List<String> directTerminalTexts(ParserRuleContext ctx) {
        List<String> result = new ArrayList<>();
        for (int index = 0; index < ctx.getChildCount(); index++) {
            if (ctx.getChild(index) instanceof TerminalNode node) {
                result.add(node.getText());
            }
        }
        return result;
    }

    private String ruleName(ParserRuleContext ctx) {
        int index = ctx.getRuleIndex();
        String[] names = parser.getRuleNames();
        if (index < 0 || index >= names.length) {
            return "";
        }
        return names[index];
    }

    private long line(ParserRuleContext ctx) {
        return ctx.getStart() == null ? 1 : ctx.getStart().getLine();
    }

    private boolean isLocalTemp(String table) {
        return clean(table).startsWith("#");
    }

    private String baseName(String raw) {
        String value = clean(raw);
        int dot = value.lastIndexOf('.');
        return dot >= 0 ? value.substring(dot + 1) : value;
    }

    private String qualifiedTable(String raw) {
        return clean(raw);
    }

    private String lastIdentifier(String raw) {
        return baseName(raw);
    }

    private String clean(String value) {
        if (value == null) {
            return "";
        }
        String clean = value.strip();
        if (clean.isBlank()) {
            return "";
        }
        clean = removeWhitespace(clean);
        List<String> parts = splitQualified(clean);
        if (parts.size() > 1) {
            return parts.stream().map(this::cleanOne).filter(part -> !part.isBlank())
                    .reduce((left, right) -> left + "." + right).orElse("");
        }
        return cleanOne(clean);
    }

    private String cleanOne(String value) {
        String clean = value.strip();
        while ((clean.startsWith("[") && clean.endsWith("]"))
                || (clean.startsWith("\"") && clean.endsWith("\""))
                || (clean.startsWith("`") && clean.endsWith("`"))) {
            clean = clean.substring(1, clean.length() - 1);
        }
        return clean;
    }

    private List<String> splitQualified(String text) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int bracketDepth = 0;
        for (int index = 0; index < text.length(); index++) {
            char ch = text.charAt(index);
            if (ch == '[') {
                bracketDepth++;
            } else if (ch == ']' && bracketDepth > 0) {
                bracketDepth--;
            }
            if (ch == '.' && bracketDepth == 0) {
                parts.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        parts.add(current.toString());
        return parts;
    }

    private String removeWhitespace(String value) {
        StringBuilder builder = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            if (!Character.isWhitespace(ch)) {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    private record ColumnEndpoint(String qualifier, String column) {
    }
}
