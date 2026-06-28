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
 * full-grammer typed visitor 使用的事件 sink。
 *
 * <p>CN: 本类不通过 token span 拆分来判断 SQL 结构。方言 visitor 从具体 grammar
 * context 调用它；sink 只负责规范化 identifier、记录 source location，并输出既有
 * token-event event shape。
 *
 * <p>EN: Event sink used by full-grammer typed visitors. This class does not
 * classify SQL by token span splitting. Dialect visitors call it from concrete
 * grammar contexts; the sink only normalizes identifiers, records source
 * locations, and emits the existing token-event event shape.
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

    /**
     * 返回 visitor 已收集的结构事件。
     *
     * <p>EN: Returns structured events collected by the visitor.
     */
    public List<StructuredSqlEvent> events() {
        return events;
    }

    /**
     * 在 derived/CTE projection scope 内访问子树。
     *
     * <p>EN: Visits a subtree while binding projection ownership for derived/CTE scopes.
     */
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

    /**
     * 在 UPDATE/MERGE/INSERT write target scope 内访问子树。
     *
     * <p>EN: Visits a subtree while binding the current write target scope.
     */
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

    /**
     * 在 SELECT scope 内访问子树，用于限制 projection 默认 rowset。
     *
     * <p>EN: Visits a subtree inside a SELECT scope to bound default projection rowsets.
     */
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

    public int rowsetScopeMark() {
        return rowsetTables.size();
    }

    public void restoreRowsetScope(int mark) {
        if (mark < 0 || mark > rowsetTables.size()) {
            return;
        }
        while (rowsetTables.size() > mark) {
            rowsetTables.remove(rowsetTables.size() - 1);
        }
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
        rowsetTables.add(clean(alias).isBlank() ? baseName(qualifiedTable) : clean(alias));
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
        if (!isLikelyForeignKeyAssignment(targetColumn, analysis.sourceColumns().get(0))) {
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

    private boolean isLikelyForeignKeyAssignment(String targetColumn, String sourceColumn) {
        String target = clean(targetColumn).toLowerCase(Locale.ROOT);
        String source = clean(sourceColumn).toLowerCase(Locale.ROOT);
        return source.equals("id") && target.endsWith("_id");
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
            addPredicateEvent(ctx, StructuredParseEventType.PREDICATE_EQUALITY, pair.left(), pair.right(), joinKind);
        }
    }

    public void predicateEquality(ParserRuleContext ctx, ParseTree leftExpression, ParseTree rightExpression, String joinKind) {
        Optional<ExpressionColumn> left = singlePredicateColumn(leftExpression, rightExpression);
        Optional<ExpressionColumn> right = singlePredicateColumn(rightExpression, leftExpression);
        if (left.isEmpty() || right.isEmpty()) {
            return;
        }
        addPredicateEvent(ctx, StructuredParseEventType.PREDICATE_EQUALITY, left.get(), right.get(), joinKind);
    }

    public void existsPredicateEqualities(ParserRuleContext ctx, ParseTree predicate) {
        for (ColumnPair pair : equalityPairs(predicate)) {
            addPredicateEvent(ctx, StructuredParseEventType.EXISTS_PREDICATE, pair.left(), pair.right(), "EXISTS");
        }
    }

    public void existsPredicateEquality(ParserRuleContext ctx, ParseTree leftExpression, ParseTree rightExpression) {
        Optional<ExpressionColumn> left = singlePredicateColumn(leftExpression, rightExpression);
        Optional<ExpressionColumn> right = singlePredicateColumn(rightExpression, leftExpression);
        if (left.isEmpty() || right.isEmpty()) {
            return;
        }
        addPredicateEvent(ctx, StructuredParseEventType.EXISTS_PREDICATE, left.get(), right.get(), "EXISTS");
    }

    private void addPredicateEvent(
            ParserRuleContext ctx,
            StructuredParseEventType eventType,
            ExpressionColumn left,
            ExpressionColumn right,
            String joinKind
    ) {
        if (sameQualifier(left, right)) {
            return;
        }
        Map<String, Object> attributes = nativeAttributes();
        attributes.put("leftAlias", left.qualifier());
        attributes.put("leftColumn", left.column());
        attributes.put("rightAlias", right.qualifier());
        attributes.put("rightColumn", right.column());
        attributes.put("joinKind", blankTo(joinKind, "WHERE_OR_UNKNOWN"));
        add(ctx, eventType, attributes);
    }

    private boolean sameQualifier(ExpressionColumn left, ExpressionColumn right) {
        return !clean(left.qualifier()).isBlank()
                && clean(left.qualifier()).equalsIgnoreCase(clean(right.qualifier()));
    }

    public void inSubqueryPredicate(ParserRuleContext ctx, ParseTree outerExpression, ParseTree subquery) {
        List<ExpressionColumn> outerColumns = directColumnList(outerExpression);
        Optional<SelectColumns> inner = selectColumns(subquery);
        if (inner.isEmpty()) {
            return;
        }
        addInSubqueryEvent(ctx, outerColumns, inner.get());
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
        return equalityPairs(tree, true);
    }

    private List<ColumnPair> equalityPairs(ParseTree tree, boolean stopAtNestedQueryBoundary) {
        List<ColumnPair> result = new ArrayList<>();
        collectEqualityPairs(tree, result, true, stopAtNestedQueryBoundary);
        return result;
    }

    private void collectEqualityPairs(
            ParseTree tree,
            List<ColumnPair> result,
            boolean root,
            boolean stopAtNestedQueryBoundary
    ) {
        if (tree == null) {
            return;
        }
        if (stopAtNestedQueryBoundary && !root && isNestedQueryBoundary(tree)) {
            return;
        }
        int equalsIndex = directLeafIndex(tree, "=");
        if (equalsIndex > 0) {
            Optional<ExpressionColumn> left = singlePredicateColumnInChildren(tree, 0, equalsIndex,
                    equalsIndex + 1, tree.getChildCount());
            Optional<ExpressionColumn> right = singlePredicateColumnInChildren(tree, equalsIndex + 1, tree.getChildCount(),
                    0, equalsIndex);
            if (left.isPresent() && right.isPresent()) {
                result.add(new ColumnPair(left.get(), right.get()));
                return;
            }
        }
        for (int index = 0; index < tree.getChildCount(); index++) {
            collectEqualityPairs(tree.getChild(index), result, false, stopAtNestedQueryBoundary);
        }
    }

    private boolean isNestedQueryBoundary(ParseTree tree) {
        String name = tree.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        return name.contains("select")
                || name.contains("subquery")
                || name.contains("preparablestmt");
    }

    private void addInSubqueryEvent(
            ParserRuleContext ctx,
            List<ExpressionColumn> outerColumns,
            SelectColumns inner
    ) {
        List<ExpressionColumn> innerColumns = inner.columns();
        if (outerColumns.size() == 1 && innerColumns.size() == 1) {
            Map<String, Object> attributes = nativeAttributes();
            attributes.put("verifiedColumnSubquery", true);
            attributes.put("outerAlias", outerColumns.get(0).qualifier());
            attributes.put("outerColumn", outerColumns.get(0).column());
            attributes.put("innerAlias", innerColumns.get(0).qualifier());
            attributes.put("innerColumn", innerColumns.get(0).column());
            attributes.put("innerTable", inner.table().isBlank() ? tableFor(innerColumns.get(0).qualifier()) : inner.table());
            add(ctx, StructuredParseEventType.IN_SUBQUERY_PREDICATE, attributes);
        } else if (outerColumns.size() > 1 && outerColumns.size() == innerColumns.size()) {
            Map<String, Object> attributes = nativeAttributes();
            attributes.put("verifiedColumnSubquery", true);
            attributes.put("outerAliases", outerColumns.stream().map(ExpressionColumn::qualifier).toList());
            attributes.put("outerColumns", outerColumns.stream().map(ExpressionColumn::column).toList());
            attributes.put("innerAliases", innerColumns.stream().map(ExpressionColumn::qualifier).toList());
            attributes.put("innerColumns", innerColumns.stream().map(ExpressionColumn::column).toList());
            attributes.put("innerTable", inner.table().isBlank() ? tableFor(innerColumns.get(0).qualifier()) : inner.table());
            add(ctx, StructuredParseEventType.TUPLE_IN_SUBQUERY_PREDICATE, attributes);
        }
    }

    private int directKeywordIndex(ParseTree tree, String keyword) {
        for (int index = 0; index < tree.getChildCount(); index++) {
            if (tree.getChild(index) instanceof TerminalNode terminal
                    && terminal.getText().equalsIgnoreCase(keyword)) {
                return index;
            }
        }
        return -1;
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

    private Optional<ExpressionColumn> singlePredicateColumn(ParseTree tree, ParseTree oppositeTree) {
        Optional<ExpressionColumn> explicit = singleDirectColumnNoDefault(tree);
        if (explicit.isPresent()) {
            return explicit;
        }
        if (singleDirectColumnNoDefault(oppositeTree).isEmpty()) {
            return Optional.empty();
        }
        return singleDirectColumnWithDefault(tree);
    }

    private Optional<ExpressionColumn> singlePredicateColumnInChildren(
            ParseTree tree,
            int startInclusive,
            int endExclusive,
            int oppositeStartInclusive,
            int oppositeEndExclusive
    ) {
        Optional<ExpressionColumn> explicit = singleDirectColumnInChildrenNoDefault(tree, startInclusive, endExclusive);
        if (explicit.isPresent()) {
            return explicit;
        }
        if (singleDirectColumnInChildrenNoDefault(tree, oppositeStartInclusive, oppositeEndExclusive).isEmpty()) {
            return Optional.empty();
        }
        return singleDirectColumnInChildrenWithDefault(tree, startInclusive, endExclusive);
    }

    private Optional<ExpressionColumn> singleDirectColumnWithDefault(ParseTree tree) {
        Optional<ExpressionColumn> naked = nakedColumn(tree);
        if (naked.isPresent()) {
            return naked;
        }
        List<ExpressionColumn> columns = directColumnList(tree);
        return columns.size() == 1 ? Optional.of(columns.get(0)) : Optional.empty();
    }

    private Optional<ExpressionColumn> singleDirectColumnNoDefault(ParseTree tree) {
        Optional<ExpressionColumn> naked = nakedColumnNoDefault(tree);
        if (naked.isPresent()) {
            return naked;
        }
        List<ExpressionColumn> columns = directColumnListNoDefault(tree);
        return columns.size() == 1 ? Optional.of(columns.get(0)) : Optional.empty();
    }

    private Optional<ExpressionColumn> singleDirectColumnInChildrenWithDefault(ParseTree tree, int startInclusive, int endExclusive) {
        List<ExpressionColumn> columns = new ArrayList<>();
        for (int index = startInclusive; index < endExclusive; index++) {
            Optional<ExpressionColumn> naked = nakedColumn(tree.getChild(index));
            if (naked.isPresent()) {
                columns.add(naked.get());
            } else {
                columns.addAll(directColumnList(tree.getChild(index)));
            }
        }
        List<ExpressionColumn> unique = columns.stream().distinct().toList();
        return unique.size() == 1 ? Optional.of(unique.get(0)) : Optional.empty();
    }

    private Optional<ExpressionColumn> singleDirectColumnInChildrenNoDefault(ParseTree tree, int startInclusive, int endExclusive) {
        List<ExpressionColumn> columns = new ArrayList<>();
        for (int index = startInclusive; index < endExclusive; index++) {
            Optional<ExpressionColumn> naked = nakedColumnNoDefault(tree.getChild(index));
            if (naked.isPresent()) {
                columns.add(naked.get());
            } else {
                columns.addAll(directColumnListNoDefault(tree.getChild(index)));
            }
        }
        List<ExpressionColumn> unique = columns.stream().distinct().toList();
        return unique.size() == 1 ? Optional.of(unique.get(0)) : Optional.empty();
    }

    private List<ExpressionColumn> directColumnListNoDefault(ParseTree tree) {
        Optional<ExpressionColumn> naked = nakedColumnNoDefault(tree);
        if (naked.isPresent()) {
            return List.of(naked.get());
        }
        return directExpressionColumns(tree, "");
    }

    private List<ExpressionColumn> directColumnList(ParseTree tree) {
        Optional<ExpressionColumn> naked = nakedColumn(tree);
        if (naked.isPresent()) {
            return List.of(naked.get());
        }
        List<ExpressionColumn> columns = new ArrayList<>();
        if (collectBareColumnList(tree, defaultProjectionQualifier(), columns)) {
            return columns.stream().distinct().toList();
        }
        return directExpressionColumns(tree, defaultProjectionQualifier());
    }

    private boolean collectBareColumnList(ParseTree tree, String defaultQualifier, List<ExpressionColumn> columns) {
        if (tree == null) {
            return false;
        }
        Optional<ExpressionColumn> naked = nakedColumnWithDefault(tree, defaultQualifier);
        if (naked.isPresent()) {
            columns.add(naked.get());
            return true;
        }
        if (tree instanceof TerminalNode terminal) {
            String text = terminal.getText();
            return text.equals(",") || text.equals("(") || text.equals(")");
        }
        if (tree.getChildCount() == 0) {
            return false;
        }
        boolean sawColumn = false;
        for (int index = 0; index < tree.getChildCount(); index++) {
            List<ExpressionColumn> childColumns = new ArrayList<>();
            if (!collectBareColumnList(tree.getChild(index), defaultQualifier, childColumns)) {
                return false;
            }
            if (!childColumns.isEmpty()) {
                sawColumn = true;
                columns.addAll(childColumns);
            }
        }
        return sawColumn;
    }

    private Optional<ExpressionColumn> nakedColumn(ParseTree tree) {
        return nakedColumnWithDefault(tree, defaultProjectionQualifier());
    }

    private Optional<ExpressionColumn> nakedColumnNoDefault(ParseTree tree) {
        return nakedColumnWithDefault(tree, "");
    }

    private Optional<ExpressionColumn> nakedColumnWithDefault(ParseTree tree, String defaultQualifier) {
        ParseTree current = unwrapTransparentSingleChild(tree);
        if (current == null) {
            return Optional.empty();
        }
        String className = current.getClass().getSimpleName();
        if (!(className.equals("ColumnrefContext")
                || className.equals("ColumnRefContext")
                || className.equals("SimpleExprColumnRefContext"))) {
            return Optional.empty();
        }
        List<String> parts = splitQualifiedName(clean(current.getText())).stream()
                .map(this::clean)
                .filter(part -> !part.isBlank())
                .toList();
        if (parts.size() >= 2 && isIdentifier(parts.get(parts.size() - 2)) && isIdentifier(parts.get(parts.size() - 1))) {
            String qualifier = parts.get(parts.size() - 2);
            String column = parts.get(parts.size() - 1);
            if (expressionAnalyzer.isNonColumnIdentifier(qualifier)
                    || expressionAnalyzer.isNonColumnIdentifier(column)) {
                return Optional.empty();
            }
            return Optional.of(new ExpressionColumn(qualifier, column));
        }
        if (parts.size() == 1 && isIdentifier(parts.get(0))) {
            String column = parts.get(0);
            if (!defaultQualifier.isBlank() && !expressionAnalyzer.isNonColumnIdentifier(column)) {
                return Optional.of(new ExpressionColumn(defaultQualifier, column));
            }
        }
        return Optional.empty();
    }

    private ParseTree unwrapTransparentSingleChild(ParseTree tree) {
        ParseTree current = tree;
        while (current != null && !(current instanceof TerminalNode) && current.getChildCount() == 1) {
            current = current.getChild(0);
        }
        return current;
    }

    private Optional<SelectColumns> selectColumns(ParseTree tree) {
        Optional<SelectColumns> fromTargetList = selectColumnsFromTypedSelect(tree);
        if (fromTargetList.isPresent()) {
            return fromTargetList;
        }
        int selectIndex = directKeywordIndex(tree, "select");
        int fromIndex = directKeywordIndex(tree, "from");
        if (selectIndex >= 0 && fromIndex > selectIndex) {
            FromBinding binding = bindingFromDirectFrom(tree, fromIndex + 1);
            List<ExpressionColumn> columns = new ArrayList<>();
            for (int index = selectIndex + 1; index < fromIndex; index++) {
                ParseTree child = tree.getChild(index);
                if (child instanceof TerminalNode terminal && terminal.getText().equals(",")) {
                    continue;
                }
                List<ExpressionColumn> childColumns = directTargetItemColumns(child, binding.qualifier());
                if (childColumns.isEmpty()) {
                    return Optional.empty();
                }
                for (ExpressionColumn childColumn : childColumns) {
                    String alias = clean(childColumn.qualifier());
                    String column = clean(childColumn.column());
                    if (alias.isBlank() || column.isBlank()) {
                        return Optional.empty();
                    }
                    columns.add(new ExpressionColumn(alias, column));
                }
            }
            return columns.isEmpty()
                    ? Optional.empty()
                    : Optional.of(new SelectColumns(columns.stream().distinct().toList(), binding.table()));
        }
        for (int index = 0; index < tree.getChildCount(); index++) {
            Optional<SelectColumns> selected = selectColumns(tree.getChild(index));
            if (selected.isPresent()) {
                return selected;
            }
        }
        return Optional.empty();
    }

    private Optional<SelectColumns> selectColumnsFromTypedSelect(ParseTree tree) {
        if (directKeywordIndex(tree, "select") < 0) {
            return Optional.empty();
        }
        ParseTree targetList = directChildWithClassContaining(tree, "Target_list");
        if (targetList == null) {
            targetList = directChildWithClassContaining(tree, "SelectItemList");
        }
        ParseTree fromClause = directChildWithClassContaining(tree, "From_clause");
        if (fromClause == null) {
            fromClause = directChildWithClassContaining(tree, "FromClause");
        }
        if (targetList == null || fromClause == null) {
            return Optional.empty();
        }
        FromBinding binding = bindingFromFromNode(fromClause);
        List<ExpressionColumn> columns = targetListColumns(targetList, binding.qualifier());
        return columns.isEmpty() ? Optional.empty() : Optional.of(new SelectColumns(columns, binding.table()));
    }

    private ParseTree directChildWithClassContaining(ParseTree tree, String fragment) {
        for (int index = 0; index < tree.getChildCount(); index++) {
            ParseTree child = tree.getChild(index);
            if (child.getClass().getSimpleName().contains(fragment)) {
                return child;
            }
        }
        return null;
    }

    private FromBinding bindingFromFromNode(ParseTree fromClause) {
        List<String> identifiers = identifiers(fromClause);
        if (identifiers.isEmpty()) {
            return new FromBinding("", "");
        }
        String table = identifiers.get(0);
        String qualifier = identifiers.size() >= 2 ? identifiers.get(1) : table;
        return new FromBinding(qualifier, table);
    }

    private List<ExpressionColumn> targetListColumns(ParseTree targetList, String defaultQualifier) {
        List<ParseTree> items = new ArrayList<>();
        collectTargetListItems(targetList, items);
        List<ExpressionColumn> columns = new ArrayList<>();
        for (ParseTree item : items) {
            List<ExpressionColumn> itemColumns = directTargetItemColumns(item, defaultQualifier);
            if (itemColumns.size() != 1) {
                return List.of();
            }
            String alias = clean(itemColumns.get(0).qualifier());
            String column = clean(itemColumns.get(0).column());
            if (alias.isBlank() || column.isBlank()) {
                return List.of();
            }
            columns.add(new ExpressionColumn(alias, column));
        }
        return columns.stream().distinct().toList();
    }

    private List<ExpressionColumn> directTargetItemColumns(ParseTree item, String defaultQualifier) {
        List<ExpressionColumn> columns = new ArrayList<>();
        if (collectBareColumnList(item, defaultQualifier, columns)) {
            return columns.stream().distinct().toList();
        }
        return directExpressionColumns(item, defaultQualifier);
    }

    private List<ExpressionColumn> directExpressionColumns(ParseTree tree, String defaultQualifier) {
        FullGrammerExpressionAnalysis analysis = expressionAnalyzer.analyzeRelationColumnExpression(tree, defaultQualifier);
        if (!"DIRECT".equals(analysis.transformType())) {
            return List.of();
        }
        int count = Math.min(analysis.sourceAliases().size(), analysis.sourceColumns().size());
        List<ExpressionColumn> columns = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            String alias = clean(analysis.sourceAliases().get(index));
            String column = clean(analysis.sourceColumns().get(index));
            if (alias.isBlank() || column.isBlank()) {
                return List.of();
            }
            columns.add(new ExpressionColumn(alias, column));
        }
        return columns.stream().distinct().toList();
    }

    private void collectTargetListItems(ParseTree tree, List<ParseTree> items) {
        String className = tree.getClass().getSimpleName();
        if (className.equals("Target_labelContext") || className.equals("SelectItemContext")) {
            items.add(tree);
            return;
        }
        for (int index = 0; index < tree.getChildCount(); index++) {
            collectTargetListItems(tree.getChild(index), items);
        }
    }

    private FromBinding bindingFromDirectFrom(ParseTree tree, int startIndex) {
        for (int index = startIndex; index < tree.getChildCount(); index++) {
            ParseTree child = tree.getChild(index);
            if (child instanceof TerminalNode terminal
                    && Set.of("where", "group", "having", "order", "limit", "union")
                    .contains(terminal.getText().toLowerCase(Locale.ROOT))) {
                break;
            }
            List<String> identifiers = identifiers(child);
            if (!identifiers.isEmpty()) {
                String table = identifiers.get(0);
                String qualifier = identifiers.size() >= 2 ? identifiers.get(1) : table;
                return new FromBinding(qualifier, table);
            }
        }
        return new FromBinding("", "");
    }

    private int directLeafIndex(ParseTree tree, String text) {
        for (int index = 0; index < tree.getChildCount(); index++) {
            if (tree.getChild(index) instanceof TerminalNode terminal && terminal.getText().equals(text)) {
                return index;
            }
        }
        return -1;
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

    private record FromBinding(String qualifier, String table) {
    }

    private record SelectColumns(List<ExpressionColumn> columns, String table) {
    }
}
