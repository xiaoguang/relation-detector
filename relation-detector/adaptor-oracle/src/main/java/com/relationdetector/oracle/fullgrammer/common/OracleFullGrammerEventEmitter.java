package com.relationdetector.oracle.fullgrammer.common;

import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import org.antlr.v4.runtime.ParserRuleContext;

import com.relationdetector.contracts.Enums.LineageFlowKind;
import com.relationdetector.contracts.Enums.LineageTransformType;
import com.relationdetector.contracts.Enums.StructuredParseEventType;

/** Per-parse Oracle rowset, projection and write event emitter. */
final class OracleFullGrammerEventEmitter extends OracleFullGrammerParseTreeSupport {
    private final OracleFullGrammerExpressionSupport expressions;
    private final Consumer<String> rowsetRegistrar;
    private final ArrayDeque<String> writeTargetTables = new ArrayDeque<>();
    private final ArrayDeque<String> writeTargetAliases = new ArrayDeque<>();
    private final Set<String> emittedRowsets = new LinkedHashSet<>();

    OracleFullGrammerEventEmitter(
            OracleSqlEventVisitorCore core,
            OracleFullGrammerParseTreeAdapter adapter,
            OracleFullGrammerExpressionSupport expressions,
            Consumer<String> rowsetRegistrar
    ) {
        super(core, adapter);
        this.expressions = expressions;
        this.rowsetRegistrar = rowsetRegistrar;
    }

    void beginWriteTarget(ParserRuleContext ctx, String alias, String table) {
        writeTargetTables.push(table);
        writeTargetAliases.push(alias);
        if (!table.isBlank()) {
            core.write(ctx, StructuredParseEventType.WRITE_TARGET, core.baseName(table), table,
                    alias, "", "", "", "", List.of(), List.of(),
                    LineageTransformType.UNKNOWN_EXPRESSION, LineageFlowKind.VALUE);
        }
    }

    void endWriteTarget() {
        writeTargetAliases.pop();
        writeTargetTables.pop();
    }

    boolean hasWriteTarget() {
        return !writeTargetTables.isEmpty();
    }

    void emitAssignment(
            ParserRuleContext ctx,
            String targetColumn,
            ParserRuleContext expression,
            StructuredParseEventType type,
            String mappingKind
    ) {
        for (OracleExpressionAnalysis source : expressions.writeAnalyses(expression)) {
            emitWriteMapping(type, ctx, writeTargetAliases.peek(), writeTargetTables.peek(),
                    lastPart(targetColumn), source, mappingKind);
        }
    }

    void emitExpressionMappings(
            StructuredParseEventType type,
            ParserRuleContext context,
            String targetAlias,
            String targetTable,
            String targetColumn,
            ParserRuleContext expression,
            String mappingKind
    ) {
        for (OracleExpressionAnalysis source : expressions.writeAnalyses(expression)) {
            emitWriteMapping(type, context, targetAlias, targetTable, targetColumn, source, mappingKind);
        }
    }

    void emitProjectionItems(
            ParserRuleContext selectedList,
            String ownerAlias,
            List<String> ownerColumns
    ) {
        if (selectedList == null) {
            return;
        }
        List<ParserRuleContext> items = children(selectedList, "select_list_elements");
        for (int index = 0; index < items.size(); index++) {
            ParserRuleContext item = items.get(index);
            ParserRuleContext expression = child(item, "expression");
            if (expression == null) {
                continue;
            }
            String outputColumn = index < ownerColumns.size()
                    ? ownerColumns.get(index)
                    : outputColumn(item);
            if (outputColumn.isBlank()) {
                continue;
            }
            for (OracleExpressionAnalysis source : expressions.writeAnalyses(expression)) {
                if (!source.sources().isEmpty()) {
                    core.projection(item, ownerAlias, outputColumn, source.aliases(),
                            source.columns(), source.transform(), source.flowKind());
                }
            }
        }
    }

    void emitIgnoredRowset(ParserRuleContext ctx, String alias) {
        core.rowset(ctx, StructuredParseEventType.IGNORED_ROWSET,
                "", alias, alias, "", alias, "", "DERIVED_TABLE");
        rowsetRegistrar.accept(alias);
    }

    void emitCteDeclaration(ParserRuleContext ctx, String name) {
        core.rowset(ctx, StructuredParseEventType.CTE_DECLARATION,
                "", name, name, "", name, "", "");
        core.rowset(ctx, StructuredParseEventType.IGNORED_ROWSET,
                "", name, name, "", name, "", "CTE_DECLARATION");
    }

    void emitRowset(ParserRuleContext ctx, String table, String alias) {
        if (table.isBlank()) {
            return;
        }
        String key = table + "|" + alias + "|" + core.line(ctx);
        if (!emittedRowsets.add(key)) {
            return;
        }
        core.rowset(ctx, StructuredParseEventType.ROWSET_REFERENCE,
                "FROM", table, core.baseName(table), alias, "", "", "");
        rowsetRegistrar.accept(alias.isBlank() ? core.baseName(table) : alias);
    }

    private void emitWriteMapping(
            StructuredParseEventType type,
            ParserRuleContext context,
            String targetAlias,
            String targetTable,
            String targetColumn,
            OracleExpressionAnalysis source,
            String mappingKind
    ) {
        if (!source.sources().isEmpty()) {
            core.write(context, type, "", "", "", targetAlias, targetTable, targetColumn,
                    mappingKind, source.aliases(), source.columns(), source.transform(), source.flowKind());
        }
    }

    private String outputColumn(ParserRuleContext item) {
        ParserRuleContext alias = child(item, "column_alias");
        if (alias != null) {
            return lastPart(columnAlias(alias));
        }
        OracleColumnRead single = expressions.singleColumn(child(item, "expression"));
        return single == null ? "" : single.column();
    }

    private String columnAlias(ParserRuleContext ctx) {
        ParserRuleContext identifier = child(ctx, "identifier");
        if (identifier != null) {
            return name(identifier);
        }
        ParserRuleContext quoted = child(ctx, "quoted_string");
        return quoted == null ? "" : core.clean(quoted.getText());
    }
}
