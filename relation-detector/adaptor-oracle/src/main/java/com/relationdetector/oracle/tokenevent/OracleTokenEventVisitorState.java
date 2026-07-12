package com.relationdetector.oracle.tokenevent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.relationdetector.contracts.Enums.LineageFlowKind;
import com.relationdetector.contracts.Enums.LineageTransformType;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.core.lineage.LineageTransformClassifier;
import com.relationdetector.core.tokenevent.TokenEventEventEmitter;
import com.relationdetector.oracle.routine.OracleRoutineScope;

/** Per-parse state, routine scope and identifiers for Oracle token-event traversal. */
abstract class OracleTokenEventVisitorState extends OracleRelationSqlBaseVisitor<Void> {
    protected final Map<String, LineageTransformType> functionExtensions = Map.of(
            "nvl", LineageTransformType.COALESCE,
            "string_agg", LineageTransformType.CONCAT_FORMAT,
            "listagg", LineageTransformType.CONCAT_FORMAT);
    protected final TokenEventEventEmitter emitter;
    protected final List<StructuredSqlEvent> events = new ArrayList<>();
    protected final Set<String> cteNames = new LinkedHashSet<>();
    protected final ArrayDeque<ProjectionOwner> projectionOwners = new ArrayDeque<>();
    protected final ArrayDeque<QueryScope> queryScopes = new ArrayDeque<>();
    protected final ArrayDeque<String> joinKinds = new ArrayDeque<>();
    protected final ArrayDeque<String> ddlTables = new ArrayDeque<>();
    protected final OracleRoutineScope routineScope = new OracleRoutineScope();
    protected int existsDepth;

    OracleTokenEventVisitorState(SqlStatementRecord statement) {
        emitter = new TokenEventEventEmitter(statement);
    }

    protected String targetAlias(OracleRelationSqlParser.TablePrimaryContext primary) {
        if (primary instanceof OracleRelationSqlParser.NamedTablePrimaryContext named && named.tableAlias() != null)
            return clean(named.tableAlias().identifier().getText());
        return targetTable(primary);
    }

    protected String rowsetAlias(OracleRelationSqlParser.TablePrimaryContext primary) {
        if (primary instanceof OracleRelationSqlParser.NamedTablePrimaryContext named) {
            return named.tableAlias() == null ? baseName(qualifiedName(named.qualifiedName()))
                    : clean(named.tableAlias().identifier().getText());
        }
        if (primary instanceof OracleRelationSqlParser.DerivedTablePrimaryContext value && value.tableAlias() != null)
            return clean(value.tableAlias().identifier().getText());
        return "";
    }

    protected String targetTable(OracleRelationSqlParser.TablePrimaryContext primary) {
        if (primary instanceof OracleRelationSqlParser.NamedTablePrimaryContext named)
            return qualifiedName(named.qualifiedName());
        if (primary instanceof OracleRelationSqlParser.DerivedTablePrimaryContext value && value.tableAlias() != null)
            return clean(value.tableAlias().identifier().getText());
        return "";
    }

    protected String qualifiedName(OracleRelationSqlParser.QualifiedNameContext ctx) {
        return String.join(".", parts(ctx));
    }

    protected List<String> parts(OracleRelationSqlParser.QualifiedNameContext ctx) {
        return ctx.identifier().stream().map(identifier -> clean(identifier.getText())).toList();
    }

    protected String clean(String raw) {
        if (raw == null) return "";
        String trimmed = raw.trim();
        if (trimmed.length() >= 2
                && ((trimmed.startsWith("\"") && trimmed.endsWith("\""))
                || (trimmed.startsWith("`") && trimmed.endsWith("`")))) {
            return trimmed.substring(1, trimmed.length() - 1).replace(
                    trimmed.substring(0, 1) + trimmed.substring(0, 1), trimmed.substring(0, 1));
        }
        return trimmed;
    }

    protected String normalize(String value) { return clean(value).toLowerCase(Locale.ROOT); }

    protected String baseName(String qualified) {
        if (qualified == null) return "";
        int dot = qualified.lastIndexOf('.');
        return dot < 0 ? qualified : qualified.substring(dot + 1);
    }

    protected String currentJoinKind() { return joinKinds.isEmpty() ? "WHERE_OR_UNKNOWN" : joinKinds.peek(); }

    protected void registerCurrentRowset(String alias, String table) {
        if (alias == null || alias.isBlank() || queryScopes.isEmpty()) return;
        QueryScope scope = queryScopes.peek();
        scope.rowsetAliases().add(alias);
        if (table != null && !table.isBlank()) scope.rowsets().put(normalize(alias), clean(table));
    }

    protected OracleExpressionAnalysis resolveCurrentScope(OracleExpressionAnalysis analysis) {
        if (queryScopes.isEmpty() || analysis.sources().isEmpty()) return analysis;
        QueryScope scope = queryScopes.peek();
        List<OracleColumnRead> sources = analysis.sources().stream()
                .map(source -> new OracleColumnRead(
                        scope.rowsets().getOrDefault(normalize(source.alias()), source.alias()), source.column()))
                .distinct().toList();
        return new OracleExpressionAnalysis(sources, analysis.transform(), analysis.flowKind());
    }

    protected QueryScope scopeFor(OracleRelationSqlParser.QuerySpecificationContext query) {
        QueryScope scope = new QueryScope();
        if (query == null || query.fromClause() == null) return scope;
        for (OracleRelationSqlParser.TableReferenceContext reference : query.fromClause().tableReference()) {
            addScopeAlias(scope, rowsetAlias(reference.tablePrimary()));
            for (OracleRelationSqlParser.JoinClauseContext join : reference.joinClause())
                addScopeAlias(scope, rowsetAlias(join.tablePrimary()));
        }
        return scope;
    }

    private void addScopeAlias(QueryScope scope, String alias) {
        if (alias != null && !alias.isBlank()) scope.rowsetAliases().add(alias);
    }

    protected String defaultColumnAlias() {
        if (queryScopes.isEmpty()) return "";
        Set<String> aliases = queryScopes.peek().rowsetAliases();
        return aliases.size() == 1 ? aliases.iterator().next() : "";
    }

    protected String joinKind(OracleRelationSqlParser.JoinClauseContext join) {
        if (join.joinType() == null) return "JOIN";
        if (join.joinType().LEFT() != null) return "LEFT_JOIN";
        if (join.joinType().RIGHT() != null) return "RIGHT_JOIN";
        if (join.joinType().FULL() != null) return "FULL_JOIN";
        if (join.joinType().CROSS() != null) return "CROSS_JOIN";
        return "JOIN";
    }

    protected record OracleColumnRead(String alias, String column) { }

    protected record OracleExpressionAnalysis(
            List<OracleColumnRead> sources, LineageTransformType transform, LineageFlowKind flowKind
    ) {
        static OracleExpressionAnalysis empty() {
            return new OracleExpressionAnalysis(List.of(), LineageTransformType.DIRECT, LineageFlowKind.VALUE);
        }
        static OracleExpressionAnalysis of(OracleColumnRead column,
                LineageTransformType transform, LineageFlowKind flowKind) {
            return new OracleExpressionAnalysis(List.of(column), transform, flowKind);
        }
        static OracleExpressionAnalysis combine(LineageTransformType transform, LineageFlowKind flowKind,
                OracleExpressionAnalysis left, OracleExpressionAnalysis right) {
            List<OracleColumnRead> sources = new ArrayList<>();
            sources.addAll(left.sources());
            sources.addAll(right.sources());
            return new OracleExpressionAnalysis(sources.stream().distinct().toList(),
                    LineageTransformClassifier.dominantForFlow(
                            flowKind, transform, left.transform(), right.transform()), flowKind);
        }
        static OracleExpressionAnalysis withTransform(LineageTransformType transform, LineageFlowKind flowKind,
                OracleExpressionAnalysis... analyses) {
            List<OracleColumnRead> sources = new ArrayList<>();
            for (OracleExpressionAnalysis analysis : analyses) sources.addAll(analysis.sources());
            return new OracleExpressionAnalysis(sources.stream().distinct().toList(), transform, flowKind);
        }
        List<String> aliases() { return sources.stream().map(OracleColumnRead::alias).toList(); }
        List<String> columns() { return sources.stream().map(OracleColumnRead::column).toList(); }
    }

    protected record ProjectionOwner(String alias, List<String> columns) { }
    protected record QueryScope(Set<String> rowsetAliases, Map<String, String> rowsets) {
        QueryScope() { this(new LinkedHashSet<>(), new LinkedHashMap<>()); }
    }
}
