package com.relationdetector.oracle.fullgrammer.common;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.antlr.v4.runtime.ParserRuleContext;

import com.relationdetector.contracts.Enums.LineageFlowKind;
import com.relationdetector.contracts.Enums.LineageTransformType;
import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.parse.ExpressionSource;
import com.relationdetector.contracts.parse.ExpressionTrace;
import com.relationdetector.contracts.parse.DdlEvent;
import com.relationdetector.contracts.parse.PredicateEvent;
import com.relationdetector.contracts.parse.ProjectionEvent;
import com.relationdetector.contracts.parse.RowsetEvent;
import com.relationdetector.contracts.parse.SourceProvenance;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.contracts.parse.WriteEvent;

/** Shared per-parse state and typed event factory for Oracle full grammar. */
public final class OracleSqlEventVisitorCore {
    private final SqlStatementRecord statement;
    private final List<StructuredSqlEvent> events = new ArrayList<>();

    public OracleSqlEventVisitorCore(SqlStatementRecord statement) {
        this.statement = statement;
    }

    public List<StructuredSqlEvent> events() {
        return List.copyOf(events);
    }

    public void rowset(ParserRuleContext ctx, StructuredParseEventType type, String keyword,
            String qualifiedTable, String table, String alias, String name,
            String targetTable, String reason) {
        events.add(new RowsetEvent(type, provenance(ctx), keyword, qualifiedTable,
                table, alias, name, targetTable, reason));
    }

    public void predicate(ParserRuleContext ctx, StructuredParseEventType type,
            String leftAlias, String leftColumn, String rightAlias, String rightColumn,
            String joinKind) {
        events.add(new PredicateEvent(type, provenance(ctx),
                new ExpressionSource(leftAlias, leftColumn),
                new ExpressionSource(rightAlias, rightColumn),
                List.of(), List.of(), "", joinKind, List.of(), false));
    }

    public void joinUsing(ParserRuleContext ctx, List<String> columns) {
        events.add(new PredicateEvent(StructuredParseEventType.JOIN_USING_COLUMNS,
                provenance(ctx), ExpressionSource.EMPTY, ExpressionSource.EMPTY,
                List.of(), List.of(), "", "", columns, false));
    }

    public void inSubquery(ParserRuleContext ctx, String outerAlias, String outerColumn,
            String innerAlias, String innerColumn, String innerTable) {
        ExpressionSource outer = new ExpressionSource(outerAlias, outerColumn);
        ExpressionSource inner = new ExpressionSource(innerAlias, innerColumn);
        events.add(new PredicateEvent(StructuredParseEventType.IN_SUBQUERY_PREDICATE,
                provenance(ctx), outer, inner, List.of(outer), List.of(inner), innerTable,
                "", List.of(), true));
    }

    public void projection(ParserRuleContext ctx, String outputAlias, String outputColumn,
            List<String> sourceAliases, List<String> sourceColumns,
            LineageTransformType transform, LineageFlowKind flowKind) {
        events.add(new ProjectionEvent(StructuredParseEventType.PROJECTION_ITEM,
                provenance(ctx), outputAlias, outputColumn,
                ExpressionTrace.of(sourceAliases, sourceColumns, flowKind, transform)));
    }

    public void write(ParserRuleContext ctx, StructuredParseEventType type,
            String table, String qualifiedTable, String alias, String targetAlias,
            String targetTable, String targetColumn, String mappingKind,
            List<String> sourceAliases, List<String> sourceColumns,
            LineageTransformType transform, LineageFlowKind flowKind) {
        events.add(new WriteEvent(type, provenance(ctx), table, qualifiedTable, alias,
                targetAlias, targetTable, targetColumn, mappingKind,
                ExpressionTrace.of(sourceAliases, sourceColumns, flowKind, transform)));
    }

    public void ddlForeignKey(ParserRuleContext ctx, String sourceTable, String sourceColumn,
            String targetTable, String targetColumn, int position, int size) {
        events.add(new DdlEvent(StructuredParseEventType.DDL_FOREIGN_KEY, provenance(ctx),
                sourceTable, sourceColumn, targetTable, targetColumn, "", "", "", "",
                position, size));
    }

    public void ddlIndex(ParserRuleContext ctx, String table, String column, String role, String kind) {
        events.add(new DdlEvent(StructuredParseEventType.DDL_INDEX, provenance(ctx),
                "", "", "", "", table, column, role, kind, 1, 1));
    }

    public void ddlColumn(ParserRuleContext ctx, String table, String column) {
        events.add(new DdlEvent(StructuredParseEventType.DDL_COLUMN, provenance(ctx),
                "", "", "", "", table, column, "", "", 1, 1));
    }

    public long line(ParserRuleContext ctx) {
        if (ctx == null || ctx.getStart() == null) {
            return statement.startLine();
        }
        return statement.startLine() + Math.max(0, ctx.getStart().getLine() - 1);
    }

    public String clean(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.length() >= 2
                && ((trimmed.startsWith("\"") && trimmed.endsWith("\""))
                || (trimmed.startsWith("`") && trimmed.endsWith("`")))) {
            return trimmed.substring(1, trimmed.length() - 1).replace(
                    trimmed.substring(0, 1) + trimmed.substring(0, 1), trimmed.substring(0, 1));
        }
        return trimmed;
    }

    public String normalize(String value) {
        return clean(value).toLowerCase(Locale.ROOT);
    }

    public String baseName(String qualified) {
        if (qualified == null) {
            return "";
        }
        int dot = qualified.lastIndexOf('.');
        return dot < 0 ? qualified : qualified.substring(dot + 1);
    }

    private SourceProvenance provenance(ParserRuleContext ctx) {
        return SourceProvenance.fullGrammer(statement, line(ctx), "", "oracle-generated-context");
    }
}
