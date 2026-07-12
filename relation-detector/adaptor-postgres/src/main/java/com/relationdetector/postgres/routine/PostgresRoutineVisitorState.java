package com.relationdetector.postgres.routine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

import com.relationdetector.contracts.Enums.LineageFlowKind;
import com.relationdetector.contracts.Enums.LineageTransformType;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.core.lineage.LineageTransformClassifier;
import com.relationdetector.core.parse.AntlrSqlParseSupport.SyntaxErrorCounter;
import com.relationdetector.core.tokenevent.TokenEventEventEmitter;

/** Per-parse state, nested-body parsing and identifiers for PostgreSQL routines. */
abstract class PostgresRoutineVisitorState extends PostgresRoutineBodySqlBaseVisitor<Void> {
    protected final SqlStatementRecord statement;
    protected final TokenEventEventEmitter emitter;
    protected final List<StructuredSqlEvent> events = new ArrayList<>();
    protected final Set<String> cteNames = new LinkedHashSet<>();
    protected final ArrayDeque<ProjectionOwner> projectionOwners = new ArrayDeque<>();
    protected final ArrayDeque<String> joinKinds = new ArrayDeque<>();
    protected final ArrayDeque<QueryScope> queryScopes = new ArrayDeque<>();
    protected int existsDepth;

    PostgresRoutineVisitorState(SqlStatementRecord statement) {
        this.statement = statement;
        this.emitter = new TokenEventEventEmitter(statement);
    }

    protected void collectRoutineBody(String quotedBody, int tokenLine) {
        String body = unquoteDollarBody(quotedBody);
        if (body.isBlank()) return;
        SyntaxErrorCounter errors = new SyntaxErrorCounter();
        PostgresRoutineBodySqlLexer lexer = new PostgresRoutineBodySqlLexer(CharStreams.fromString(body));
        lexer.removeErrorListeners();
        lexer.addErrorListener(errors);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        PostgresRoutineBodySqlParser parser = new PostgresRoutineBodySqlParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(errors);
        PostgresRoutineBodySqlParser.ScriptContext root = parser.script();
        tokens.fill();
        if (errors.count() > 0) return;
        SqlStatementRecord nested = new SqlStatementRecord(body, statement.sourceType(), statement.sourceName(),
                statement.startLine() + Math.max(0, tokenLine - 1),
                statement.startLine() + Math.max(0, tokenLine - 1) + body.lines().count(),
                statement.attributes());
        events.addAll(new PostgresRoutineBodyParseTreeVisitor(nested).collect(root));
    }

    private String unquoteDollarBody(String raw) {
        if (raw == null || raw.length() < 4 || raw.charAt(0) != '$') return "";
        int endTagStart = raw.indexOf('$', 1);
        if (endTagStart < 0) return "";
        String tag = raw.substring(0, endTagStart + 1);
        if (!raw.endsWith(tag) || raw.length() < tag.length() * 2) return "";
        return raw.substring(tag.length(), raw.length() - tag.length());
    }

    protected String targetAlias(PostgresRoutineBodySqlParser.TablePrimaryContext primary) {
        if (primary instanceof PostgresRoutineBodySqlParser.NamedTablePrimaryContext named
                && named.tableAlias() != null) return clean(named.tableAlias().identifier().getText());
        return targetTable(primary);
    }

    protected String rowsetAlias(PostgresRoutineBodySqlParser.TablePrimaryContext primary) {
        if (primary instanceof PostgresRoutineBodySqlParser.NamedTablePrimaryContext named) {
            return named.tableAlias() == null ? baseName(qualifiedName(named.qualifiedName()))
                    : clean(named.tableAlias().identifier().getText());
        }
        if (primary instanceof PostgresRoutineBodySqlParser.DerivedTablePrimaryContext value
                && value.tableAlias() != null) return clean(value.tableAlias().identifier().getText());
        if (primary instanceof PostgresRoutineBodySqlParser.RowsFromTablePrimaryContext value
                && value.tableAlias() != null) return clean(value.tableAlias().identifier().getText());
        if (primary instanceof PostgresRoutineBodySqlParser.FunctionRowsetPrimaryContext value
                && value.tableAlias() != null) return clean(value.tableAlias().identifier().getText());
        return "";
    }

    protected String targetTable(PostgresRoutineBodySqlParser.TablePrimaryContext primary) {
        if (primary instanceof PostgresRoutineBodySqlParser.NamedTablePrimaryContext named) {
            return qualifiedName(named.qualifiedName());
        }
        return rowsetAlias(primary);
    }

    protected String qualifiedName(PostgresRoutineBodySqlParser.QualifiedNameContext ctx) {
        return String.join(".", parts(ctx));
    }

    protected List<String> parts(PostgresRoutineBodySqlParser.QualifiedNameContext ctx) {
        return ctx.identifier().stream().map(identifier -> clean(identifier.getText())).toList();
    }

    protected String clean(String raw) {
        if (raw == null) return "";
        String trimmed = raw.trim();
        if (trimmed.length() >= 2 && ((trimmed.startsWith("\"") && trimmed.endsWith("\""))
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

    protected String joinKind(PostgresRoutineBodySqlParser.JoinClauseContext join) {
        if (join.CROSS() != null) return "CROSS_JOIN";
        if (join.joinType() == null) return "JOIN";
        if (join.joinType().LEFT() != null) return "LEFT_JOIN";
        if (join.joinType().RIGHT() != null) return "RIGHT_JOIN";
        if (join.joinType().FULL() != null) return "FULL_JOIN";
        return "JOIN";
    }

    protected void registerCurrentRowset(String alias) {
        if (alias != null && !alias.isBlank() && !queryScopes.isEmpty()) {
            queryScopes.peek().rowsetAliases().add(alias);
        }
    }

    protected String defaultColumnAlias() {
        if (queryScopes.isEmpty()) return "";
        Set<String> aliases = queryScopes.peek().rowsetAliases();
        return aliases.size() == 1 ? aliases.iterator().next() : "";
    }

    protected record QueryScope(Set<String> rowsetAliases) {
        QueryScope() { this(new LinkedHashSet<>()); }
    }
    protected record ProjectionOwner(String alias, List<String> columns) { }
    protected record ColumnRead(String alias, String column) { }
    protected record ExpressionAnalysis(List<ColumnRead> sources,
            LineageTransformType transform, LineageFlowKind flowKind) {
        static ExpressionAnalysis empty() {
            return new ExpressionAnalysis(List.of(), LineageTransformType.DIRECT, LineageFlowKind.VALUE);
        }
        static ExpressionAnalysis of(ColumnRead column, LineageTransformType transform,
                LineageFlowKind flowKind) {
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
