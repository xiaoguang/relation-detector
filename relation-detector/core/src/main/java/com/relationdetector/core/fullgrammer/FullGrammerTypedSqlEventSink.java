package com.relationdetector.core.fullgrammer;

import java.util.List;
import java.util.Optional;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredSqlEvent;

/**
 * Thin facade used by typed full-grammar visitors.
 *
 * <p>Traversal remains in dialect visitors. Per-parse helper instances own rowset,
 * projection, predicate, write and provenance state and never share mutable data.
 */
public final class FullGrammerTypedSqlEventSink {
    private final FullGrammerExpressionAnalyzer expressionAnalyzer;
    private final FullGrammerParseTreeAdapter parseTreeAdapter;
    private final SourceLocationSupport source;
    private final FullGrammerEventRecorder recorder;
    private final RowsetScopeSink rowsets;
    private final ProjectionEventSink projectionEvents;
    private final WriteMappingSink writeMappings;
    private final DirectColumnTraceSupport directColumnTraces;
    private final SubqueryProjectionTraceSupport subqueryProjectionTraces;
    private final PredicateEventSink predicateEvents;

    public FullGrammerTypedSqlEventSink(
            SqlStatementRecord statement,
            FullGrammerExpressionAnalyzer expressionAnalyzer
    ) {
        this.expressionAnalyzer = expressionAnalyzer;
        this.parseTreeAdapter = expressionAnalyzer.parseTreeAdapter();
        this.source = new SourceLocationSupport(statement);
        this.recorder = new FullGrammerEventRecorder(statement, source);
        this.rowsets = new RowsetScopeSink(source);
        this.projectionEvents = new ProjectionEventSink(source, rowsets, recorder, expressionAnalyzer);
        this.writeMappings = new WriteMappingSink(
                source, rowsets, recorder, expressionAnalyzer, projectionEvents);
        this.directColumnTraces = new DirectColumnTraceSupport(
                source, rowsets, expressionAnalyzer, parseTreeAdapter);
        this.subqueryProjectionTraces = new SubqueryProjectionTraceSupport(
                source, parseTreeAdapter, directColumnTraces);
        this.predicateEvents = new PredicateEventSink(
                source, recorder, rowsets, parseTreeAdapter,
                directColumnTraces, subqueryProjectionTraces);
    }

    public List<StructuredSqlEvent> events() {
        return recorder.events();
    }

    public FullGrammerParseTreeAdapter parseTreeAdapter() {
        return parseTreeAdapter;
    }

    public void withProjectionOwner(String owner, Runnable visitor) {
        rowsets.withProjectionOwner(owner, visitor);
    }

    public void withWriteTarget(String tableOrAlias, Runnable visitor) {
        rowsets.withWriteTarget(tableOrAlias, visitor);
    }

    public void withSelectScope(Runnable visitor) {
        rowsets.withSelectScope(visitor);
    }

    public void withStatementScope(Runnable visitor) {
        source.withStatementScope(visitor);
    }

    public String currentProjectionOwner() {
        return rowsets.currentProjectionOwner();
    }

    public String currentWriteTarget() {
        return rowsets.currentWriteTarget();
    }

    public int rowsetScopeMark() {
        return rowsets.rowsetScopeMark();
    }

    public void restoreRowsetScope(int mark) {
        rowsets.restoreRowsetScope(mark);
    }

    public void rowset(ParserRuleContext ctx, String keyword, String qualifiedTable, String alias) {
        String table = baseName(qualifiedTable);
        if (table.isBlank()) {
            return;
        }
        recorder.rowset(ctx, StructuredParseEventType.ROWSET_REFERENCE,
                source.blankTo(keyword, "FROM"), clean(qualifiedTable), table, clean(alias), "", "", "");
        rowsets.registerRowset(qualifiedTable, alias);
    }

    public void ignoredRowset(ParserRuleContext ctx, String name, String reason) {
        String cleanName = clean(name);
        if (cleanName.isBlank()) {
            return;
        }
        rowsets.markIgnoredRowset(cleanName, reason);
        recorder.rowset(ctx, StructuredParseEventType.IGNORED_ROWSET,
                "", "", cleanName, "", cleanName, "", reason);
    }

    public void cte(ParserRuleContext ctx, String name) {
        String cleanName = clean(name);
        if (cleanName.isBlank()) {
            return;
        }
        recorder.rowset(ctx, StructuredParseEventType.CTE_DECLARATION,
                "", "", cleanName, "", cleanName, "", "");
        ignoredRowset(ctx, cleanName, "CTE_DECLARATION");
    }

    public void localTempTable(ParserRuleContext ctx, String table) {
        String cleanTable = clean(table);
        if (cleanTable.isBlank()) {
            return;
        }
        recorder.rowset(ctx, StructuredParseEventType.LOCAL_TEMP_TABLE_DECLARATION,
                "", cleanTable, baseName(cleanTable), "", "", "", "");
    }

    public void nonColumnIdentifier(String identifier) {
        expressionAnalyzer.ignoreIdentifier(identifier);
    }

    public void triggerTarget(ParserRuleContext ctx, String table) {
        String cleanTable = clean(table);
        if (cleanTable.isBlank()) {
            return;
        }
        recorder.rowset(ctx, StructuredParseEventType.TRIGGER_TARGET_TABLE,
                "", cleanTable, baseName(cleanTable), "", "", "", "");
        triggerPseudoRowset(ctx, "NEW", cleanTable);
        triggerPseudoRowset(ctx, "OLD", cleanTable);
    }

    public void triggerPseudoRowset(ParserRuleContext ctx, String name, String targetTable) {
        recorder.rowset(ctx, StructuredParseEventType.TRIGGER_PSEUDO_ROWSET,
                "", "", "", "", name, clean(targetTable), "");
    }

    public void writeTarget(ParserRuleContext ctx, String table, String alias) {
        String cleanTable = clean(table);
        if (!cleanTable.isBlank()) {
            recorder.writeTarget(ctx, baseName(cleanTable), cleanTable, clean(alias));
        }
    }

    public void updateAssignment(
            ParserRuleContext ctx, String targetAlias, String targetTable,
            String targetColumn, ParseTree expression
    ) {
        writeMappings.updateAssignment(ctx, targetAlias, targetTable, targetColumn, expression);
    }

    public void mergeUpdate(
            ParserRuleContext ctx, String targetAlias, String targetTable,
            String targetColumn, ParseTree expression
    ) {
        writeMappings.mergeUpdate(ctx, targetAlias, targetTable, targetColumn, expression);
    }

    public void mergeInsert(
            ParserRuleContext ctx, String targetAlias, String targetTable,
            String targetColumn, ParseTree expression
    ) {
        writeMappings.mergeInsert(ctx, targetAlias, targetTable, targetColumn, expression);
    }

    public void insertSelect(
            ParserRuleContext ctx, String targetAlias, String targetTable,
            String targetColumn, ParseTree expression
    ) {
        writeMappings.insertSelect(ctx, targetAlias, targetTable, targetColumn, expression);
    }

    public void projection(
            ParserRuleContext ctx, String outputAlias, String outputColumn, ParseTree expression
    ) {
        projectionEvents.projection(ctx, outputAlias, outputColumn, expression);
    }

    public void predicateEqualities(ParserRuleContext ctx, ParseTree predicate, String joinKind) {
        predicateEvents.predicateEqualities(ctx, predicate, joinKind);
    }

    public void predicateEquality(
            ParserRuleContext ctx, ParseTree leftExpression, ParseTree rightExpression, String joinKind
    ) {
        predicateEvents.predicateEquality(ctx, leftExpression, rightExpression, joinKind);
    }

    public void predicateEqualityColumns(
            ParserRuleContext ctx,
            String leftAlias,
            String leftColumn,
            String rightAlias,
            String rightColumn,
            String joinKind
    ) {
        predicateEvents.predicateEqualityColumns(
                ctx, leftAlias, leftColumn, rightAlias, rightColumn, joinKind);
    }

    public void existsPredicateEqualities(ParserRuleContext ctx, ParseTree predicate) {
        predicateEvents.existsPredicateEqualities(ctx, predicate);
    }

    public void existsPredicateEquality(
            ParserRuleContext ctx, ParseTree leftExpression, ParseTree rightExpression
    ) {
        predicateEvents.existsPredicateEquality(ctx, leftExpression, rightExpression);
    }

    public void inSubqueryPredicate(
            ParserRuleContext ctx, ParseTree outerExpression, ParseTree subquery
    ) {
        predicateEvents.inSubqueryPredicate(ctx, outerExpression, subquery);
    }

    public void joinUsing(
            ParserRuleContext ctx, String leftAlias, String rightAlias, List<String> columns
    ) {
        predicateEvents.joinUsing(ctx, leftAlias, rightAlias, columns);
    }

    public String tableForAlias(String aliasOrTable) {
        return rowsets.tableFor(aliasOrTable);
    }

    public String clean(String raw) {
        return source.clean(raw);
    }

    public String baseName(String raw) {
        return source.baseName(raw);
    }

    public String firstIdentifier(ParseTree tree) {
        return source.firstIdentifier(tree);
    }

    public List<String> identifiers(ParseTree tree) {
        return source.identifiers(tree);
    }

    public Optional<String> aliasAfter(ParseTree tree, String marker) {
        return source.aliasAfter(tree, marker);
    }
}
