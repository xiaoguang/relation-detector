package com.relationdetector.mysql.tokenevent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.antlr.v4.runtime.ParserRuleContext;

import com.relationdetector.contracts.Enums.LineageFlowKind;
import com.relationdetector.contracts.Enums.LineageTransformType;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.core.lineage.LineageTransformClassifier;
import com.relationdetector.core.tokenevent.TokenEventEventEmitter;

/** Per-parse mutable state and identifier helpers for MySQL token-event traversal. */
abstract class MySqlTokenEventVisitorState extends MySqlRelationSqlBaseVisitor<Void> {
    protected final Map<String, LineageTransformType> functionExtensions = Map.of(
            "string_agg", LineageTransformType.CONCAT_FORMAT);
    protected final TokenEventEventEmitter emitter;
    protected final List<StructuredSqlEvent> events = new ArrayList<>();
    protected final Set<String> cteNames = new LinkedHashSet<>();
    protected final Set<String> nonColumnIdentifiers = new LinkedHashSet<>();
    protected final ArrayDeque<ProjectionOwner> projectionOwners = new ArrayDeque<>();
    protected final ArrayDeque<String> joinKinds = new ArrayDeque<>();
    protected final ArrayDeque<String> ddlTables = new ArrayDeque<>();
    protected int existsDepth;

    MySqlTokenEventVisitorState(SqlStatementRecord statement) {
        emitter = new TokenEventEventEmitter(statement);
    }

    protected MySqlRelationSqlParser.TablePrimaryContext firstTablePrimary(
            List<MySqlRelationSqlParser.TableReferenceContext> tableReferences
    ) {
        if (tableReferences == null || tableReferences.isEmpty()) {
            return null;
        }
        MySqlRelationSqlParser.TablePrimaryContext primary = tableReferences.get(0).tablePrimary();
        if (primary instanceof MySqlRelationSqlParser.OdbcTablePrimaryContext odbc) {
            return firstTablePrimary(List.of(odbc.tableReference()));
        }
        return primary;
    }

    protected String targetAlias(MySqlRelationSqlParser.TablePrimaryContext primary) {
        if (primary instanceof MySqlRelationSqlParser.NamedTablePrimaryContext named && named.tableAlias() != null) {
            return clean(named.tableAlias().identifier().getText());
        }
        return targetTable(primary);
    }

    protected String rowsetAlias(MySqlRelationSqlParser.TablePrimaryContext primary) {
        if (primary instanceof MySqlRelationSqlParser.NamedTablePrimaryContext named) {
            return named.tableAlias() == null
                    ? baseName(qualifiedName(named.qualifiedName()))
                    : clean(named.tableAlias().identifier().getText());
        }
        if (primary instanceof MySqlRelationSqlParser.DerivedTablePrimaryContext derived && derived.tableAlias() != null) {
            return clean(derived.tableAlias().identifier().getText());
        }
        if (primary instanceof MySqlRelationSqlParser.JsonTablePrimaryContext json && json.tableAlias() != null) {
            return clean(json.tableAlias().identifier().getText());
        }
        if (primary instanceof MySqlRelationSqlParser.OdbcTablePrimaryContext odbc) {
            MySqlRelationSqlParser.TablePrimaryContext nested = firstTablePrimary(List.of(odbc.tableReference()));
            return nested == null ? "" : rowsetAlias(nested);
        }
        return "";
    }

    protected String targetTable(MySqlRelationSqlParser.TablePrimaryContext primary) {
        if (primary instanceof MySqlRelationSqlParser.NamedTablePrimaryContext named) {
            return qualifiedName(named.qualifiedName());
        }
        if (primary instanceof MySqlRelationSqlParser.DerivedTablePrimaryContext derived && derived.tableAlias() != null) {
            return clean(derived.tableAlias().identifier().getText());
        }
        if (primary instanceof MySqlRelationSqlParser.OdbcTablePrimaryContext odbc) {
            MySqlRelationSqlParser.TablePrimaryContext nested = firstTablePrimary(List.of(odbc.tableReference()));
            return nested == null ? "" : targetTable(nested);
        }
        return "";
    }

    protected String singleProjectionQualifier(MySqlRelationSqlParser.FromClauseContext fromClause) {
        if (fromClause == null || fromClause.tableReference().size() != 1) {
            return "";
        }
        MySqlRelationSqlParser.TableReferenceContext reference = fromClause.tableReference(0);
        return reference.joinClause().isEmpty() ? rowsetAlias(reference.tablePrimary()) : "";
    }

    protected String enclosingSingleProjectionQualifier(ParserRuleContext ctx) {
        ParserRuleContext current = ctx;
        while (current != null) {
            if (current instanceof MySqlRelationSqlParser.QuerySpecificationContext query) {
                return singleProjectionQualifier(query.fromClause());
            }
            current = current.getParent();
        }
        return "";
    }

    protected String qualifiedName(MySqlRelationSqlParser.QualifiedNameContext ctx) {
        return String.join(".", parts(ctx));
    }

    protected List<String> parts(MySqlRelationSqlParser.QualifiedNameContext ctx) {
        return ctx.identifier().stream().map(identifier -> clean(identifier.getText())).toList();
    }

    protected String clean(String raw) {
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

    protected String normalize(String value) {
        return clean(value).toLowerCase(Locale.ROOT);
    }

    protected String baseName(String qualified) {
        if (qualified == null) {
            return "";
        }
        int dot = qualified.lastIndexOf('.');
        return dot < 0 ? qualified : qualified.substring(dot + 1);
    }

    protected String currentJoinKind() {
        return joinKinds.isEmpty() ? "WHERE_OR_UNKNOWN" : joinKinds.peek();
    }

    protected String joinKind(MySqlRelationSqlParser.JoinClauseContext join) {
        if (join.joinOperator() != null && join.joinOperator().STRAIGHT_JOIN() != null) {
            return "STRAIGHT_JOIN";
        }
        if (join.joinType() == null) {
            return "JOIN";
        }
        if (join.joinType().LEFT() != null) return "LEFT_JOIN";
        if (join.joinType().RIGHT() != null) return "RIGHT_JOIN";
        if (join.joinType().FULL() != null) return "FULL_JOIN";
        return "JOIN";
    }

    protected record ProjectionOwner(String alias, List<String> columns) {
    }

    protected record ColumnRead(String alias, String column) {
    }

    protected record ExpressionAnalysis(
            List<ColumnRead> sources,
            LineageTransformType transform,
            LineageFlowKind flowKind
    ) {
        static ExpressionAnalysis empty() {
            return new ExpressionAnalysis(List.of(), LineageTransformType.DIRECT, LineageFlowKind.VALUE);
        }

        static ExpressionAnalysis emptyControl() {
            return new ExpressionAnalysis(List.of(), LineageTransformType.CASE_WHEN, LineageFlowKind.CONTROL);
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
                    LineageTransformClassifier.dominantForFlow(
                            flowKind, transform, left.transform(), right.transform()), flowKind);
        }

        List<String> aliases() {
            return sources.stream().map(ColumnRead::alias).toList();
        }

        List<String> columns() {
            return sources.stream().map(ColumnRead::column).toList();
        }
    }
}
