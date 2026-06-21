package com.relationdetector.core.fullgrammer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredSqlEvent;

/**
 * Event sink used by full-grammer typed visitors.
 *
 * <p>This class does not classify SQL by token span splitting. Dialect visitors
 * call it from concrete grammar contexts; the sink only normalizes identifiers,
 * records source locations, and emits the existing token-event event shape.
 */
public final class FullGrammerTypedSqlEventSink {
    private final SqlStatementRecord statement;
    private final FullGrammerExpressionAnalyzer expressionAnalyzer;
    private final List<StructuredSqlEvent> events = new ArrayList<>();
    private final ArrayDeque<String> projectionOwners = new ArrayDeque<>();
    private final ArrayDeque<Integer> projectionRowsetBases = new ArrayDeque<>();
    private final ArrayDeque<Integer> selectRowsetBases = new ArrayDeque<>();
    private final ArrayDeque<String> writeTargets = new ArrayDeque<>();
    private final Set<String> eventKeys = new LinkedHashSet<>();
    private final Set<String> ignoredNames = new LinkedHashSet<>();
    private final Set<String> functionRowsetNames = new LinkedHashSet<>();
    private final Map<String, String> aliasToTable = new LinkedHashMap<>();
    private final List<String> rowsetTables = new ArrayList<>();

    public FullGrammerTypedSqlEventSink(SqlStatementRecord statement, FullGrammerExpressionAnalyzer expressionAnalyzer) {
        this.statement = statement;
        this.expressionAnalyzer = expressionAnalyzer;
    }

    public List<StructuredSqlEvent> events() {
        return events;
    }

    public void withProjectionOwner(String owner, Runnable visitor) {
        if (owner == null || owner.isBlank()) {
            visitor.run();
            return;
        }
        projectionOwners.push(clean(owner));
        projectionRowsetBases.push(rowsetTables.size());
        try {
            visitor.run();
        } finally {
            projectionRowsetBases.pop();
            projectionOwners.pop();
        }
    }

    public void withWriteTarget(String tableOrAlias, Runnable visitor) {
        if (tableOrAlias == null || tableOrAlias.isBlank()) {
            visitor.run();
            return;
        }
        writeTargets.push(clean(tableOrAlias));
        try {
            visitor.run();
        } finally {
            writeTargets.pop();
        }
    }

    public void withSelectScope(Runnable visitor) {
        selectRowsetBases.push(rowsetTables.size());
        try {
            visitor.run();
        } finally {
            selectRowsetBases.pop();
        }
    }

    public String currentProjectionOwner() {
        return projectionOwners.isEmpty() ? "" : projectionOwners.peek();
    }

    public String currentWriteTarget() {
        return writeTargets.isEmpty() ? "" : writeTargets.peek();
    }

    public void rowset(ParserRuleContext ctx, String keyword, String qualifiedTable, String alias) {
        String table = baseName(qualifiedTable);
        if (table.isBlank()) {
            return;
        }
        Map<String, Object> attributes = nativeAttributes();
        attributes.put("keyword", blankTo(keyword, "FROM"));
        attributes.put("qualifiedTable", clean(qualifiedTable));
        attributes.put("table", table);
        attributes.put("alias", clean(alias));
        add(ctx, StructuredParseEventType.ROWSET_REFERENCE, attributes);
        aliasToTable.put(baseName(qualifiedTable).toLowerCase(Locale.ROOT), baseName(qualifiedTable));
        rowsetTables.add(baseName(qualifiedTable));
        if (!clean(alias).isBlank()) {
            aliasToTable.put(clean(alias).toLowerCase(Locale.ROOT), baseName(qualifiedTable));
        }
    }

    public void ignoredRowset(ParserRuleContext ctx, String name, String reason) {
        String cleanName = clean(name);
        if (cleanName.isBlank()) {
            return;
        }
        ignoredNames.add(cleanName.toLowerCase(Locale.ROOT));
        ignoredNames.add(baseName(cleanName).toLowerCase(Locale.ROOT));
        if ("FUNCTION_ROWSET".equals(reason)) {
            functionRowsetNames.add(cleanName.toLowerCase(Locale.ROOT));
            functionRowsetNames.add(baseName(cleanName).toLowerCase(Locale.ROOT));
        }
        Map<String, Object> attributes = nativeAttributes();
        attributes.put("name", cleanName);
        attributes.put("table", cleanName);
        attributes.put("reason", reason);
        add(ctx, StructuredParseEventType.IGNORED_ROWSET, attributes);
    }

    public void cte(ParserRuleContext ctx, String name) {
        String cleanName = clean(name);
        if (cleanName.isBlank()) {
            return;
        }
        Map<String, Object> attributes = nativeAttributes();
        attributes.put("name", cleanName);
        attributes.put("table", cleanName);
        add(ctx, StructuredParseEventType.CTE_DECLARATION, attributes);
        ignoredRowset(ctx, cleanName, "CTE_DECLARATION");
    }

    public void localTempTable(ParserRuleContext ctx, String table) {
        String cleanTable = clean(table);
        if (cleanTable.isBlank()) {
            return;
        }
        Map<String, Object> attributes = nativeAttributes();
        attributes.put("table", baseName(cleanTable));
        attributes.put("qualifiedTable", cleanTable);
        add(ctx, StructuredParseEventType.LOCAL_TEMP_TABLE_DECLARATION, attributes);
    }

    public void nonColumnIdentifier(String identifier) {
        expressionAnalyzer.ignoreIdentifier(identifier);
    }

    public void triggerTarget(ParserRuleContext ctx, String table) {
        String cleanTable = clean(table);
        if (cleanTable.isBlank()) {
            return;
        }
        Map<String, Object> attributes = nativeAttributes();
        attributes.put("table", baseName(cleanTable));
        attributes.put("qualifiedTable", cleanTable);
        add(ctx, StructuredParseEventType.TRIGGER_TARGET_TABLE, attributes);
        triggerPseudoRowset(ctx, "NEW", cleanTable);
        triggerPseudoRowset(ctx, "OLD", cleanTable);
    }

    public void triggerPseudoRowset(ParserRuleContext ctx, String name, String targetTable) {
        Map<String, Object> attributes = nativeAttributes();
        attributes.put("name", name);
        attributes.put("targetTable", clean(targetTable));
        add(ctx, StructuredParseEventType.TRIGGER_PSEUDO_ROWSET, attributes);
    }

    public void writeTarget(ParserRuleContext ctx, String table, String alias) {
        String cleanTable = clean(table);
        if (cleanTable.isBlank()) {
            return;
        }
        Map<String, Object> attributes = nativeAttributes();
        attributes.put("table", baseName(cleanTable));
        attributes.put("qualifiedTable", cleanTable);
        attributes.put("alias", clean(alias));
        add(ctx, StructuredParseEventType.WRITE_TARGET, attributes);
    }

    public void updateAssignment(ParserRuleContext ctx, String targetAlias, String targetTable, String targetColumn, ParseTree expression) {
        addWriteMapping(ctx, StructuredParseEventType.UPDATE_ASSIGNMENT, "UPDATE_SET",
                targetAlias, targetTable, targetColumn, expression);
    }

    public void mergeUpdate(ParserRuleContext ctx, String targetAlias, String targetTable, String targetColumn, ParseTree expression) {
        addWriteMapping(ctx, StructuredParseEventType.MERGE_WRITE_MAPPING, "MERGE_UPDATE",
                targetAlias, targetTable, targetColumn, expression);
    }

    public void mergeInsert(ParserRuleContext ctx, String targetAlias, String targetTable, String targetColumn, ParseTree expression) {
        addWriteMapping(ctx, StructuredParseEventType.MERGE_WRITE_MAPPING, "MERGE_INSERT",
                targetAlias, targetTable, targetColumn, expression);
    }

    public void insertSelect(ParserRuleContext ctx, String targetAlias, String targetTable, String targetColumn, ParseTree expression) {
        addWriteMapping(ctx, StructuredParseEventType.INSERT_SELECT_MAPPING, "INSERT_SELECT",
                targetAlias, targetTable, targetColumn, expression);
    }

    private void addWriteMapping(
            ParserRuleContext ctx,
            StructuredParseEventType type,
            String mappingKind,
            String targetAlias,
            String targetTable,
            String targetColumn,
            ParseTree expression
    ) {
        String cleanColumn = clean(targetColumn);
        if (cleanColumn.isBlank()) {
            return;
        }
        FullGrammerExpressionAnalysis analysis = expressionAnalyzer.analyze(expression);
        if (isNestedCaseWhen(expression, analysis)) {
            addNestedCaseWhenMappings(ctx, type, mappingKind, targetAlias, targetTable, cleanColumn, expression);
        } else {
            Map<String, Object> attributes = nativeAttributes();
            attributes.put("mappingKind", mappingKind);
            attributes.put("targetAlias", clean(targetAlias));
            attributes.put("targetTable", clean(targetTable));
            attributes.put("targetColumn", cleanColumn);
            attributes.put("sourceAliases", analysis.sourceAliases());
            attributes.put("sourceColumns", analysis.sourceColumns());
            attributes.put("transformType", analysis.transformType());
            attributes.put("flowKind", analysis.flowKind());
            add(ctx, type, attributes);
        }
        directAssignmentPredicate(ctx, targetAlias, cleanColumn, analysis);
        expressionSource(ctx, analysis);
    }

    private boolean isNestedCaseWhen(ParseTree expression, FullGrammerExpressionAnalysis analysis) {
        return "CASE_WHEN".equals(analysis.transformType())
                && !expressionAnalyzer.isTopLevelCaseExpression(expression);
    }

    private void addNestedCaseWhenMappings(
            ParserRuleContext ctx,
            StructuredParseEventType type,
            String mappingKind,
            String targetAlias,
            String targetTable,
            String targetColumn,
            ParseTree expression
    ) {
        for (FullGrammerExpressionAnalysis analysis : expressionAnalyzer.caseExpressionAnalyses(expression, defaultProjectionQualifier())) {
            if (!analysis.hasSources()) {
                continue;
            }
            Map<String, Object> attributes = nativeAttributes();
            attributes.put("mappingKind", mappingKind);
            attributes.put("targetAlias", clean(targetAlias));
            attributes.put("targetTable", clean(targetTable));
            attributes.put("targetColumn", targetColumn);
            attributes.put("sourceAliases", analysis.sourceAliases());
            attributes.put("sourceColumns", analysis.sourceColumns());
            attributes.put("transformType", analysis.transformType());
            attributes.put("flowKind", analysis.flowKind());
            add(ctx, type, attributes);
        }
    }

    private void directAssignmentPredicate(
            ParserRuleContext ctx,
            String targetAlias,
            String targetColumn,
            FullGrammerExpressionAnalysis analysis
    ) {
        if (!"DIRECT".equals(analysis.transformType())
                || analysis.sourceAliases().size() != 1
                || analysis.sourceColumns().size() != 1) {
            return;
        }
        Map<String, Object> attributes = nativeAttributes();
        attributes.put("leftAlias", clean(targetAlias).isBlank() ? currentWriteTarget() : clean(targetAlias));
        attributes.put("leftColumn", targetColumn);
        attributes.put("rightAlias", clean(analysis.sourceAliases().get(0)));
        attributes.put("rightColumn", clean(analysis.sourceColumns().get(0)));
        attributes.put("joinKind", "WRITE_ASSIGNMENT");
        add(ctx, StructuredParseEventType.PREDICATE_EQUALITY, attributes);
    }

    public void projection(ParserRuleContext ctx, String outputAlias, String outputColumn, ParseTree expression) {
        String cleanOutputAlias = clean(outputAlias);
        String cleanOutputColumn = clean(outputColumn);
        if (cleanOutputAlias.isBlank() || cleanOutputColumn.isBlank()) {
            return;
        }
        FullGrammerExpressionAnalysis analysis = expressionAnalyzer.analyze(expression, defaultProjectionQualifier());
        Map<String, Object> attributes = nativeAttributes();
        attributes.put("outputAlias", cleanOutputAlias);
        attributes.put("outputColumn", cleanOutputColumn);
        attributes.put("sourceAliases", analysis.sourceAliases());
        attributes.put("sourceColumns", analysis.sourceColumns());
        attributes.put("transformType", analysis.transformType());
        attributes.put("flowKind", analysis.flowKind());
        add(ctx, StructuredParseEventType.PROJECTION_ITEM, attributes);
        expressionSource(ctx, analysis);
    }

    public void predicateEqualities(ParserRuleContext ctx, ParseTree predicate, String joinKind) {
        for (ColumnPair pair : equalityPairs(predicate)) {
            Map<String, Object> attributes = nativeAttributes();
            attributes.put("leftAlias", pair.left().qualifier());
            attributes.put("leftColumn", pair.left().column());
            attributes.put("rightAlias", pair.right().qualifier());
            attributes.put("rightColumn", pair.right().column());
            attributes.put("joinKind", blankTo(joinKind, "WHERE_OR_UNKNOWN"));
            add(ctx, StructuredParseEventType.PREDICATE_EQUALITY, attributes);
        }
    }

    public void subqueryPredicates(ParserRuleContext ctx, ParseTree predicate) {
        collectSubqueryPredicates(ctx, predicate);
    }

    public void joinUsing(ParserRuleContext ctx, String leftAlias, String rightAlias, List<String> columns) {
        if (clean(leftAlias).isBlank() || clean(rightAlias).isBlank() || columns.isEmpty()) {
            return;
        }
        Map<String, Object> attributes = nativeAttributes();
        attributes.put("leftAlias", clean(leftAlias));
        attributes.put("rightAlias", clean(rightAlias));
        attributes.put("usingColumns", columns.stream().map(this::clean).filter(s -> !s.isBlank()).toList());
        add(ctx, StructuredParseEventType.JOIN_USING_COLUMNS, attributes);
    }

    private void expressionSource(ParserRuleContext ctx, FullGrammerExpressionAnalysis analysis) {
        if (!analysis.hasSources()) {
            return;
        }
        Map<String, Object> attributes = nativeAttributes();
        attributes.put("sourceAliases", analysis.sourceAliases());
        attributes.put("sourceColumns", analysis.sourceColumns());
        attributes.put("transformType", analysis.transformType());
        attributes.put("flowKind", analysis.flowKind());
        add(ctx, StructuredParseEventType.EXPRESSION_SOURCE, attributes);
    }

    private String defaultProjectionQualifier() {
        Integer base = selectRowsetBases.peek();
        if (base == null) {
            base = projectionRowsetBases.peek();
        }
        if (base == null) {
            return "";
        }
        List<String> physicalRowsets = rowsetTables.subList(base, rowsetTables.size()).stream()
                .filter(rowset -> !functionRowsetNames.contains(clean(rowset).toLowerCase(Locale.ROOT)))
                .toList();
        if (physicalRowsets.size() == 1) {
            return physicalRowsets.get(0);
        }
        return "";
    }

    private List<ColumnPair> equalityPairs(ParseTree tree) {
        List<ColumnPair> result = new ArrayList<>();
        collectEqualityPairs(tree, result);
        return result;
    }

    private void collectEqualityPairs(ParseTree tree, List<ColumnPair> result) {
        if (tree == null) {
            return;
        }
        if (containsLeaf(tree, "=")) {
            List<ExpressionColumn> columns = expressionColumns(tree);
            if (columns.size() == 2) {
                result.add(new ColumnPair(columns.get(0), columns.get(1)));
                return;
            }
        }
        for (int index = 0; index < tree.getChildCount(); index++) {
            collectEqualityPairs(tree.getChild(index), result);
        }
    }

    private void collectSubqueryPredicates(ParserRuleContext ctx, ParseTree tree) {
        if (tree == null) {
            return;
        }
        if (containsKeyword(tree, "exists")) {
            List<ExpressionColumn> columns = expressionColumns(tree);
            if (columns.size() >= 2) {
                Map<String, Object> attributes = nativeAttributes();
                attributes.put("leftAlias", columns.get(0).qualifier());
                attributes.put("leftColumn", columns.get(0).column());
                attributes.put("rightAlias", columns.get(1).qualifier());
                attributes.put("rightColumn", columns.get(1).column());
                add(ctx, StructuredParseEventType.EXISTS_PREDICATE, attributes);
            }
        }
        if (containsKeyword(tree, "in") && !containsKeyword(tree, "exists") && hasInLeftOperand(tree)) {
            List<ExpressionColumn> columns = expressionColumns(tree);
            if (columns.size() >= 2 && !looksLikeTupleIn(columns)) {
                Map<String, Object> attributes = nativeAttributes();
                attributes.put("outerAlias", columns.get(0).qualifier());
                attributes.put("outerColumn", columns.get(0).column());
                attributes.put("innerAlias", columns.get(1).qualifier());
                attributes.put("innerColumn", columns.get(1).column());
                attributes.put("innerTable", tableFor(columns.get(1).qualifier()));
                add(ctx, StructuredParseEventType.IN_SUBQUERY_PREDICATE, attributes);
            } else if (columns.size() >= 4) {
                int outerCount = tupleOuterColumnCount(columns);
                if (outerCount <= 0 || columns.size() < outerCount * 2) {
                    return;
                }
                Map<String, Object> attributes = nativeAttributes();
                attributes.put("outerAliases", columns.subList(0, outerCount).stream().map(ExpressionColumn::qualifier).toList());
                attributes.put("outerColumns", columns.subList(0, outerCount).stream().map(ExpressionColumn::column).toList());
                attributes.put("innerAliases", columns.subList(outerCount, outerCount * 2).stream().map(ExpressionColumn::qualifier).toList());
                attributes.put("innerColumns", columns.subList(outerCount, outerCount * 2).stream().map(ExpressionColumn::column).toList());
                attributes.put("innerTable", tableFor(columns.get(outerCount).qualifier()));
                add(ctx, StructuredParseEventType.TUPLE_IN_SUBQUERY_PREDICATE, attributes);
            } else if (columns.isEmpty()) {
                addUnqualifiedInPredicate(ctx, tree);
            }
        }
        for (int index = 0; index < tree.getChildCount(); index++) {
            collectSubqueryPredicates(ctx, tree.getChild(index));
        }
    }

    private boolean looksLikeTupleIn(List<ExpressionColumn> columns) {
        return columns.size() >= 4
                && columns.get(0).qualifier().equals(columns.get(1).qualifier());
    }

    private int tupleOuterColumnCount(List<ExpressionColumn> columns) {
        if (columns.size() < 4) {
            return 0;
        }
        String firstQualifier = columns.get(0).qualifier();
        for (int index = 1; index < columns.size(); index++) {
            if (!columns.get(index).qualifier().equals(firstQualifier)) {
                return index > 1 ? index : columns.size() / 2;
            }
        }
        return columns.size() / 2;
    }

    private void addUnqualifiedInPredicate(ParserRuleContext ctx, ParseTree tree) {
        if (rowsetTables.size() < 2) {
            return;
        }
        List<String> identifiers = identifiers(tree).stream()
                .filter(identifier -> !aliasToTable.containsKey(identifier.toLowerCase(Locale.ROOT)))
                .filter(identifier -> rowsetTables.stream().noneMatch(table -> table.equalsIgnoreCase(identifier)))
                .toList();
        if (identifiers.size() < 2) {
            return;
        }
        Map<String, Object> attributes = nativeAttributes();
        attributes.put("outerAlias", rowsetTables.get(0));
        attributes.put("outerTable", rowsetTables.get(0));
        attributes.put("outerColumn", identifiers.get(0));
        attributes.put("innerAlias", rowsetTables.get(rowsetTables.size() - 1));
        attributes.put("innerTable", rowsetTables.get(rowsetTables.size() - 1));
        attributes.put("innerColumn", identifiers.get(1));
        add(ctx, StructuredParseEventType.IN_SUBQUERY_PREDICATE, attributes);
    }

    private boolean hasInLeftOperand(ParseTree tree) {
        String compact = compactLower(tree.getText());
        int inIndex = compact.indexOf("in(");
        if (inIndex < 0) {
            inIndex = compact.indexOf("inselect");
        }
        return inIndex > 0;
    }

    private String compactLower(String raw) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < raw.length(); index++) {
            char ch = raw.charAt(index);
            if (!Character.isWhitespace(ch)) {
                builder.append(Character.toLowerCase(ch));
            }
        }
        return builder.toString();
    }

    private boolean containsKeyword(ParseTree tree, String keyword) {
        if (tree instanceof TerminalNode terminal) {
            return terminal.getText().equalsIgnoreCase(keyword);
        }
        for (int index = 0; index < tree.getChildCount(); index++) {
            if (containsKeyword(tree.getChild(index), keyword)) {
                return true;
            }
        }
        return false;
    }

    private String tableFor(String aliasOrTable) {
        String clean = clean(aliasOrTable);
        if (clean.isBlank()) {
            return "";
        }
        return aliasToTable.getOrDefault(clean.toLowerCase(Locale.ROOT), clean);
    }

    public String tableForAlias(String aliasOrTable) {
        return tableFor(aliasOrTable);
    }

    private List<ExpressionColumn> expressionColumns(ParseTree tree) {
        FullGrammerExpressionAnalysis analysis = expressionAnalyzer.analyze(tree, defaultProjectionQualifier());
        int count = Math.min(analysis.sourceAliases().size(), analysis.sourceColumns().size());
        List<ExpressionColumn> columns = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            String alias = clean(analysis.sourceAliases().get(index));
            String column = clean(analysis.sourceColumns().get(index));
            if (!alias.isBlank() && !column.isBlank()) {
                columns.add(new ExpressionColumn(alias, column));
            }
        }
        return columns.stream().distinct().toList();
    }

    private boolean containsLeaf(ParseTree tree, String text) {
        if (tree instanceof TerminalNode terminal) {
            return terminal.getText().equals(text);
        }
        for (int index = 0; index < tree.getChildCount(); index++) {
            if (containsLeaf(tree.getChild(index), text)) {
                return true;
            }
        }
        return false;
    }

    private void add(ParserRuleContext ctx, StructuredParseEventType type, Map<String, Object> attributes) {
        StructuredSqlEvent event = new StructuredSqlEvent(type, statement.sourceName(), line(ctx), attributes);
        String key = type.name() + "|" + event.line() + "|" + attributes;
        if (eventKeys.add(key)) {
            events.add(event);
        }
    }

    private Map<String, Object> nativeAttributes() {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("tokenEventNative", true);
        attributes.put("fullGrammerNative", true);
        return attributes;
    }

    private int line(ParserRuleContext ctx) {
        Token start = ctx == null ? null : ctx.getStart();
        long line = start == null ? statement.startLine() : statement.startLine() + Math.max(0, start.getLine() - 1);
        return Math.toIntExact(line);
    }

    public String clean(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.trim();
        if (text.indexOf('.') >= 0) {
            List<String> cleanParts = new ArrayList<>();
            for (String part : splitQualifiedName(text)) {
                String cleanPart = stripIdentifierQuotes(part.trim());
                if (!cleanPart.isBlank()) {
                    cleanParts.add(cleanPart);
                }
            }
            return String.join(".", cleanParts);
        }
        return stripIdentifierQuotes(text);
    }

    private List<String> splitQualifiedName(String text) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int index = 0; index < text.length(); index++) {
            char ch = text.charAt(index);
            if (ch == '.') {
                parts.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        parts.add(current.toString());
        return parts;
    }

    private String stripIdentifierQuotes(String raw) {
        String text = raw.trim();
        while ((text.startsWith("`") && text.endsWith("`"))
                || (text.startsWith("\"") && text.endsWith("\""))
                || (text.startsWith("'") && text.endsWith("'"))) {
            if (text.length() < 2) {
                return "";
            }
            text = text.substring(1, text.length() - 1).trim();
        }
        return text;
    }

    public String baseName(String raw) {
        String text = clean(raw);
        int dot = text.lastIndexOf('.');
        return dot >= 0 ? clean(text.substring(dot + 1)) : text;
    }

    public String firstIdentifier(ParseTree tree) {
        return identifiers(tree).stream().findFirst().orElse("");
    }

    public List<String> identifiers(ParseTree tree) {
        List<String> result = new ArrayList<>();
        collectIdentifierLeaves(tree, result);
        return result.stream().map(this::clean).filter(s -> !s.isBlank()).toList();
    }

    public Optional<String> aliasAfter(ParseTree tree, String marker) {
        List<String> identifiers = identifiers(tree);
        if (identifiers.isEmpty()) {
            return Optional.empty();
        }
        String first = identifiers.get(0);
        String last = identifiers.get(identifiers.size() - 1);
        if (!last.equals(first) && !last.equalsIgnoreCase(marker)) {
            return Optional.of(last);
        }
        return Optional.empty();
    }

    private void collectIdentifierLeaves(ParseTree tree, List<String> result) {
        if (tree == null) {
            return;
        }
        if (tree instanceof TerminalNode terminal) {
            String text = clean(terminal.getText());
            if (isIdentifier(text)) {
                result.add(text);
            }
            return;
        }
        for (int index = 0; index < tree.getChildCount(); index++) {
            collectIdentifierLeaves(tree.getChild(index), result);
        }
    }

    private boolean isIdentifier(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if (Set.of("select", "from", "join", "inner", "left", "right", "full", "outer", "where", "on",
                "using", "with", "as", "update", "set", "insert", "into", "values", "merge", "when",
                "then", "case", "else", "end", "and", "or", "not", "in", "exists", "null", "true", "false", "only",
                "tablesample", "system", "materialized", "returning", "group", "by", "order", "having",
                "limit", "default").contains(lower)) {
            return false;
        }
        char first = value.charAt(0);
        if (!(Character.isLetter(first) || first == '_')) {
            return false;
        }
        for (int index = 1; index < value.length(); index++) {
            char ch = value.charAt(index);
            if (!(Character.isLetterOrDigit(ch) || ch == '_' || ch == '$')) {
                return false;
            }
        }
        return true;
    }

    private String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private record ExpressionColumn(String qualifier, String column) {
    }

    private record ColumnPair(ExpressionColumn left, ExpressionColumn right) {
    }
}
