package com.relationdetector.core.tokenevent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.relationdetector.contracts.Enums.LineageFlowKind;
import com.relationdetector.contracts.Enums.LineageTransformType;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.core.antlr.common.CommonRelationSqlBaseVisitor;
import com.relationdetector.core.antlr.common.CommonRelationSqlParser;
import com.relationdetector.core.lineage.LineageTransformClassifier;

/**
 *
 * Per-parse mutable state and identifier/source helpers for the common visitor.
 */
abstract class CommonTokenEventVisitorState extends CommonRelationSqlBaseVisitor<Void> {
    protected final TokenEventEventEmitter emitter;
    protected final List<StructuredSqlEvent> events = new ArrayList<>();
    protected final Set<String> cteNames = new LinkedHashSet<>();
    protected final ArrayDeque<ProjectionOwner> projectionOwners = new ArrayDeque<>();
    protected final ArrayDeque<String> joinKinds = new ArrayDeque<>();
    protected final ArrayDeque<String> ddlTables = new ArrayDeque<>();
    protected int existsDepth;

    CommonTokenEventVisitorState(SqlStatementRecord statement) {
        emitter = new TokenEventEventEmitter(statement);
    }

    protected String targetAlias(CommonRelationSqlParser.TablePrimaryContext primary) {
        if (primary instanceof CommonRelationSqlParser.NamedTablePrimaryContext named && named.tableAlias() != null) {
            return clean(named.tableAlias().identifier().getText());
        }
        return targetTable(primary);
    }

    protected String rowsetAlias(CommonRelationSqlParser.TablePrimaryContext primary) {
        if (primary instanceof CommonRelationSqlParser.NamedTablePrimaryContext named) {
            if (named.tableAlias() != null) {
                return clean(named.tableAlias().identifier().getText());
            }
            return baseName(qualifiedName(named.qualifiedName()));
        }
        if (primary instanceof CommonRelationSqlParser.DerivedTablePrimaryContext derived && derived.tableAlias() != null) {
            return clean(derived.tableAlias().identifier().getText());
        }
        return "";
    }

    protected String targetTable(CommonRelationSqlParser.TablePrimaryContext primary) {
        if (primary instanceof CommonRelationSqlParser.NamedTablePrimaryContext named) {
            return qualifiedName(named.qualifiedName());
        }
        if (primary instanceof CommonRelationSqlParser.DerivedTablePrimaryContext derived && derived.tableAlias() != null) {
            return clean(derived.tableAlias().identifier().getText());
        }
        return "";
    }

    protected String qualifiedName(CommonRelationSqlParser.QualifiedNameContext ctx) {
        return String.join(".", parts(ctx));
    }

    protected List<String> parts(CommonRelationSqlParser.QualifiedNameContext ctx) {
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

    protected String joinKind(CommonRelationSqlParser.JoinClauseContext join) {
        if (join.joinType() == null) {
            return "JOIN";
        }
        if (join.joinType().LEFT() != null) {
            return "LEFT_JOIN";
        }
        if (join.joinType().RIGHT() != null) {
            return "RIGHT_JOIN";
        }
        if (join.joinType().FULL() != null) {
            return "FULL_JOIN";
        }
        if (join.joinType().CROSS() != null) {
            return "CROSS_JOIN";
        }
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
