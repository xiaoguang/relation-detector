package com.relationdetector.postgres.plpgsql.tokenevent;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.antlr.v4.runtime.ParserRuleContext;

import com.relationdetector.postgres.routine.PlPgSqlBodyStructure;
import com.relationdetector.postgres.routine.PlPgSqlStaticSqlFragment;
import com.relationdetector.postgres.routine.PlPgSqlStaticSqlFragment.MaskRange;

/** Collects typed static-SQL boundaries without interpreting SQL semantics. */
final class PlPgSqlStaticStatementCollector extends PlPgSqlBaseVisitor<Void> {
    private final String source;
    private final List<PlPgSqlBodyStructure.StaticSqlStatement> staticStatements = new ArrayList<>();
    private final List<Integer> dynamicSqlLines = new ArrayList<>();
    private final List<Integer> unsupportedLines = new ArrayList<>();
    private final Set<String> localIdentifiers = new LinkedHashSet<>();
    private int statementCount;

    PlPgSqlStaticStatementCollector(String source) {
        this.source = source == null ? "" : source;
    }

    PlPgSqlBodyStructure collect(PlPgSqlParser.ScriptContext root) {
        visit(root);
        return new PlPgSqlBodyStructure(
                staticStatements, dynamicSqlLines, unsupportedLines, localIdentifiers, statementCount);
    }

    @Override
    public Void visitDeclarationItem(PlPgSqlParser.DeclarationItemContext ctx) {
        localIdentifiers.add(clean(ctx.identifier().getText()));
        return null;
    }

    @Override
    public Void visitStatement(PlPgSqlParser.StatementContext ctx) {
        statementCount++;
        return visitChildren(ctx);
    }

    @Override public Void visitSelectStatement(PlPgSqlParser.SelectStatementContext ctx) { return addSelect(ctx); }
    @Override public Void visitInsertSelectStatement(PlPgSqlParser.InsertSelectStatementContext ctx) {
        return addInsert(ctx, ctx.returningIntoClause());
    }
    @Override public Void visitInsertValuesStatement(PlPgSqlParser.InsertValuesStatementContext ctx) {
        return addInsert(ctx, ctx.returningIntoClause());
    }
    @Override public Void visitUpdateStatement(PlPgSqlParser.UpdateStatementContext ctx) { return add(ctx); }
    @Override public Void visitMergeStatement(PlPgSqlParser.MergeStatementContext ctx) { return add(ctx); }
    @Override public Void visitDeleteStatement(PlPgSqlParser.DeleteStatementContext ctx) { return add(ctx); }
    @Override public Void visitCreateTableStatement(PlPgSqlParser.CreateTableStatementContext ctx) { return add(ctx); }
    @Override public Void visitAlterTableStatement(PlPgSqlParser.AlterTableStatementContext ctx) { return add(ctx); }
    @Override public Void visitCreateIndexStatement(PlPgSqlParser.CreateIndexStatementContext ctx) { return add(ctx); }

    @Override
    public Void visitDynamicExecuteStatement(PlPgSqlParser.DynamicExecuteStatementContext ctx) {
        dynamicSqlLines.add(ctx.getStart().getLine());
        return null;
    }

    @Override
    public Void visitUnknownStatement(PlPgSqlParser.UnknownStatementContext ctx) {
        unsupportedLines.add(ctx.getStart().getLine());
        return null;
    }

    private Void add(ParserRuleContext ctx) {
        staticStatements.add(PlPgSqlStaticSqlFragment.from(source, ctx, List.of()));
        return null;
    }

    private Void addInsert(ParserRuleContext statement, ParserRuleContext returningInto) {
        List<MaskRange> masks = returningInto == null
                ? List.of()
                : List.of(PlPgSqlStaticSqlFragment.range(returningInto));
        staticStatements.add(PlPgSqlStaticSqlFragment.from(source, statement, masks));
        return null;
    }

    private Void addSelect(PlPgSqlParser.SelectStatementContext ctx) {
        List<MaskRange> masks = new ArrayList<>();
        addIntoMask(ctx.querySpecification(), masks);
        for (PlPgSqlParser.SetOperationContext operation : ctx.setOperation()) {
            addIntoMask(operation.querySpecification(), masks);
        }
        staticStatements.add(PlPgSqlStaticSqlFragment.from(source, ctx, masks));
        return null;
    }

    private void addIntoMask(PlPgSqlParser.QuerySpecificationContext query, List<MaskRange> masks) {
        if (query != null && query.intoClause() != null) {
            masks.add(PlPgSqlStaticSqlFragment.range(query.intoClause()));
        }
    }

    private String clean(String value) {
        String result = value == null ? "" : value.strip();
        return result.length() >= 2 && result.startsWith("\"") && result.endsWith("\"")
                ? result.substring(1, result.length() - 1).replace("\"\"", "\"")
                : result;
    }
}
