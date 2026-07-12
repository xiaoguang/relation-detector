package com.relationdetector.postgres.tokenevent;

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
import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.core.lineage.LineageTransformClassifier;
import com.relationdetector.core.tokenevent.TokenEventEventEmitter;
import com.relationdetector.postgres.routine.PostgresRoutineAttributes;

/** Per-parse state, provenance and identifier helpers for PostgreSQL token-event traversal. */
abstract class PostgresTokenEventVisitorState extends PostgresRelationSqlBaseVisitor<Void> {
    protected final SqlStatementRecord statement;
    protected final TokenEventEventEmitter emitter;
    protected final List<StructuredSqlEvent> events = new ArrayList<>();
    protected final List<WarningMessage> warnings = new ArrayList<>();
    protected final Set<String> cteNames = new LinkedHashSet<>();
    protected final Set<String> nonColumnIdentifiers;
    protected final boolean routineSql;
    protected final ArrayDeque<ProjectionOwner> projectionOwners = new ArrayDeque<>();
    protected final ArrayDeque<String> joinKinds = new ArrayDeque<>();
    protected final ArrayDeque<String> ddlTables = new ArrayDeque<>();
    protected final ArrayDeque<QueryScope> queryScopes = new ArrayDeque<>();
    protected int existsDepth;

    PostgresTokenEventVisitorState(SqlStatementRecord statement) {
        this.statement = statement;
        emitter = new TokenEventEventEmitter(statement);
        nonColumnIdentifiers = PostgresRoutineAttributes.nonColumnIdentifiers(statement.attributes());
        routineSql = PostgresRoutineAttributes.isRoutineSql(statement);
    }

    protected boolean isNonColumnIdentifier(String identifier) {
        return identifier != null && nonColumnIdentifiers.contains(normalize(identifier));
    }

    protected String unquoteDollarBody(String raw) {
        if (raw == null || raw.length() < 4 || raw.charAt(0) != '$') {
            return "";
        }
        int endTagStart = raw.indexOf('$', 1);
        if (endTagStart < 0) {
            return "";
        }
        String tag = raw.substring(0, endTagStart + 1);
        return raw.endsWith(tag) && raw.length() >= tag.length() * 2
                ? raw.substring(tag.length(), raw.length() - tag.length()) : "";
    }

    protected String targetAlias(PostgresRelationSqlParser.TablePrimaryContext primary) {
        if (primary instanceof PostgresRelationSqlParser.NamedTablePrimaryContext named && named.tableAlias() != null) {
            return clean(named.tableAlias().identifier().getText());
        }
        return targetTable(primary);
    }

    protected String rowsetAlias(PostgresRelationSqlParser.TablePrimaryContext primary) {
        if (primary instanceof PostgresRelationSqlParser.NamedTablePrimaryContext named) {
            return named.tableAlias() == null ? baseName(qualifiedName(named.qualifiedName()))
                    : clean(named.tableAlias().identifier().getText());
        }
        if (primary instanceof PostgresRelationSqlParser.DerivedTablePrimaryContext value && value.tableAlias() != null) {
            return clean(value.tableAlias().identifier().getText());
        }
        if (primary instanceof PostgresRelationSqlParser.RowsFromTablePrimaryContext value && value.tableAlias() != null) {
            return clean(value.tableAlias().identifier().getText());
        }
        if (primary instanceof PostgresRelationSqlParser.FunctionRowsetPrimaryContext value
                && value.tableAlias() != null) {
            return clean(value.tableAlias().identifier().getText());
        }
        return "";
    }

    protected String targetTable(PostgresRelationSqlParser.TablePrimaryContext primary) {
        if (primary instanceof PostgresRelationSqlParser.NamedTablePrimaryContext named) {
            return qualifiedName(named.qualifiedName());
        }
        if (primary instanceof PostgresRelationSqlParser.DerivedTablePrimaryContext value && value.tableAlias() != null) {
            return clean(value.tableAlias().identifier().getText());
        }
        if (primary instanceof PostgresRelationSqlParser.RowsFromTablePrimaryContext value && value.tableAlias() != null) {
            return clean(value.tableAlias().identifier().getText());
        }
        if (primary instanceof PostgresRelationSqlParser.FunctionRowsetPrimaryContext value
                && value.tableAlias() != null) {
            return clean(value.tableAlias().identifier().getText());
        }
        return "";
    }

    protected String qualifiedName(PostgresRelationSqlParser.QualifiedNameContext ctx) {
        return String.join(".", parts(ctx));
    }

    protected List<String> parts(PostgresRelationSqlParser.QualifiedNameContext ctx) {
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

    protected String normalize(String value) {
        return clean(value).toLowerCase(Locale.ROOT);
    }

    protected String baseName(String qualified) {
        if (qualified == null) return "";
        int dot = qualified.lastIndexOf('.');
        return dot < 0 ? qualified : qualified.substring(dot + 1);
    }

    protected String currentJoinKind() {
        return joinKinds.isEmpty() ? "WHERE_OR_UNKNOWN" : joinKinds.peek();
    }

    protected String joinKind(PostgresRelationSqlParser.JoinClauseContext join) {
        if (join.joinType() == null) return "JOIN";
        if (join.joinType().LEFT() != null) return "LEFT_JOIN";
        if (join.joinType().RIGHT() != null) return "RIGHT_JOIN";
        if (join.joinType().FULL() != null) return "FULL_JOIN";
        if (join.joinType().CROSS() != null) return "CROSS_JOIN";
        return "JOIN";
    }

    protected void registerCurrentRowset(String alias) {
        if (alias != null && !alias.isBlank() && !queryScopes.isEmpty()) {
            queryScopes.peek().rowsetAliases().add(alias);
        }
    }

    protected QueryScope scopeFor(PostgresRelationSqlParser.QuerySpecificationContext query) {
        QueryScope scope = new QueryScope();
        if (query == null || query.fromClause() == null) return scope;
        for (PostgresRelationSqlParser.TableReferenceContext reference : query.fromClause().tableReference()) {
            addScopeAlias(scope, rowsetAlias(reference.tablePrimary()));
            for (PostgresRelationSqlParser.JoinClauseContext join : reference.joinClause()) {
                addScopeAlias(scope, rowsetAlias(join.tablePrimary()));
            }
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

    protected record QueryScope(Set<String> rowsetAliases) {
        QueryScope() { this(new LinkedHashSet<>()); }
    }

    protected record ProjectionOwner(String alias, List<String> columns) {
    }

    protected record ColumnRead(String alias, String column) {
    }

    protected record ExpressionAnalysis(
            List<ColumnRead> sources, LineageTransformType transform, LineageFlowKind flowKind
    ) {
        static ExpressionAnalysis empty() {
            return new ExpressionAnalysis(List.of(), LineageTransformType.DIRECT, LineageFlowKind.VALUE);
        }
        static ExpressionAnalysis of(ColumnRead column, LineageTransformType transform, LineageFlowKind flowKind) {
            return new ExpressionAnalysis(List.of(column), transform, flowKind);
        }
        static ExpressionAnalysis combine(LineageTransformType transform, LineageFlowKind flowKind,
                ExpressionAnalysis left, ExpressionAnalysis right) {
            List<ColumnRead> sources = new ArrayList<>();
            sources.addAll(left.sources());
            sources.addAll(right.sources());
            return new ExpressionAnalysis(sources.stream().distinct().toList(),
                    LineageTransformClassifier.dominantForFlow(
                            flowKind, transform, left.transform(), right.transform()), flowKind);
        }
        List<String> aliases() { return sources.stream().map(ColumnRead::alias).toList(); }
        List<String> columns() { return sources.stream().map(ColumnRead::column).toList(); }
    }
}
