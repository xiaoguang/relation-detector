package com.relationdetector.postgres.plpgsql.v18;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.antlr.v4.runtime.ParserRuleContext;

import com.relationdetector.postgres.routine.PlPgSqlBodyStructure;
import com.relationdetector.postgres.routine.PlPgSqlStaticSqlFragment;
import com.relationdetector.postgres.routine.PlPgSqlStaticSqlFragment.MaskRange;

/** Collects procedural-shell symbols and typed embedded-SQL boundaries. */
final class PlPgSqlShellCollector extends PlPgSqlParserBaseVisitor<Void> {
    private final String source;
    private final List<PlPgSqlBodyStructure.StaticSqlStatement> staticStatements = new ArrayList<>();
    private final List<Integer> dynamicSqlLines = new ArrayList<>();
    private final List<Integer> unsupportedLines = new ArrayList<>();
    private final Set<String> localIdentifiers = new LinkedHashSet<>();
    private int statementCount;

    PlPgSqlShellCollector(String source) {
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
    public Void visitForQueryStatement(PlPgSqlParser.ForQueryStatementContext ctx) {
        localIdentifiers.add(clean(ctx.identifier(0).getText()));
        add(ctx.embeddedQuery(), List.of());
        return visitChildren(ctx);
    }

    @Override
    public Void visitForControlStatement(PlPgSqlParser.ForControlStatementContext ctx) {
        localIdentifiers.add(clean(ctx.identifier(0).getText()));
        return visitChildren(ctx);
    }

    @Override
    public Void visitForeachStatement(PlPgSqlParser.ForeachStatementContext ctx) {
        localIdentifiers.add(clean(ctx.identifier(0).getText()));
        return visitChildren(ctx);
    }

    @Override
    public Void visitStatement(PlPgSqlParser.StatementContext ctx) {
        statementCount++;
        return visitChildren(ctx);
    }

    @Override
    public Void visitStaticSqlStatement(PlPgSqlParser.StaticSqlStatementContext ctx) {
        return addEmbedded(ctx.embeddedSql());
    }

    @Override
    public Void visitReturnQueryStatement(PlPgSqlParser.ReturnQueryStatementContext ctx) {
        return addEmbedded(ctx.embeddedSql());
    }

    @Override
    public Void visitDynamicExecuteStatement(PlPgSqlParser.DynamicExecuteStatementContext ctx) {
        dynamicSqlLines.add(ctx.getStart().getLine());
        return null;
    }

    @Override
    public Void visitUnsupportedStatement(PlPgSqlParser.UnsupportedStatementContext ctx) {
        unsupportedLines.add(ctx.getStart().getLine());
        return null;
    }

    private Void addEmbedded(PlPgSqlParser.EmbeddedSqlContext sql) {
        List<MaskRange> masks = new ArrayList<>();
        if (sql.selectSql() != null && sql.selectSql().plPgSqlIntoClause() != null) {
            masks.add(PlPgSqlStaticSqlFragment.range(sql.selectSql().plPgSqlIntoClause()));
        }
        if (sql.dmlSql() != null && sql.dmlSql().plPgSqlReturningIntoClause() != null) {
            masks.add(PlPgSqlStaticSqlFragment.range(sql.dmlSql().plPgSqlReturningIntoClause()));
        }
        return add(sql, masks);
    }

    private Void add(ParserRuleContext context, List<MaskRange> masks) {
        staticStatements.add(PlPgSqlStaticSqlFragment.from(source, context, masks));
        return null;
    }

    private String clean(String value) {
        String result = value == null ? "" : value.strip();
        return result.length() >= 2 && result.startsWith("\"") && result.endsWith("\"")
                ? result.substring(1, result.length() - 1).replace("\"\"", "\"")
                : result;
    }
}
