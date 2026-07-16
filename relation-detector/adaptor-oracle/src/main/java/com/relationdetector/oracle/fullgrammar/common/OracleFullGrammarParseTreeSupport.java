package com.relationdetector.oracle.fullgrammar.common;

import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import com.relationdetector.oracle.fullgrammar.common.OracleFullGrammarParseTreeAdapter.Role;
import com.relationdetector.oracle.fullgrammar.common.OracleFullGrammarParseTreeAdapter.Symbol;

/**
 *
 * Stateless typed-role and generated-context access shared by Oracle collectors.
 */
abstract class OracleFullGrammarParseTreeSupport {
    final OracleSqlEventVisitorCore core;
    private final OracleFullGrammarParseTreeAdapter adapter;

    OracleFullGrammarParseTreeSupport(
            OracleSqlEventVisitorCore core,
            OracleFullGrammarParseTreeAdapter adapter
    ) {
        this.core = core;
        this.adapter = adapter;
    }

    final OracleFullGrammarParseTreeAdapter adapter() {
        return adapter;
    }

    final String tableFrom(ParserRuleContext ctx) {
        if (ctx == null) {
            return "";
        }
        if (hasRole(ctx, Role.TABLE_REF_INTERNAL)) {
            return tableFrom(child(ctx, Role.DML_TABLE_EXPRESSION));
        }
        ParserRuleContext dml = child(ctx, Role.DML_TABLE_EXPRESSION);
        if (dml != null) {
            return tableFrom(dml);
        }
        ParserRuleContext tableview = child(ctx, Role.TABLEVIEW_NAME);
        return tableview == null ? "" : name(tableview);
    }

    final String aliasFrom(ParserRuleContext selectedTableview, String table) {
        ParserRuleContext alias = child(selectedTableview, Role.TABLE_ALIAS);
        return alias == null ? core.baseName(table) : name(alias);
    }

    final String qualifiedTable(ParserRuleContext schema, ParserRuleContext table) {
        String tableName = name(table);
        return schema == null ? tableName : name(schema) + "." + tableName;
    }

    final String name(ParseTree ctx) {
        return ctx == null ? "" : core.clean(ctx.getText());
    }

    final String lastPart(String value) {
        String cleaned = core.clean(value);
        int dot = cleaned.lastIndexOf('.');
        return dot < 0 ? cleaned : cleaned.substring(dot + 1);
    }

    final List<String> columnNamesFromParenColumnList(ParserRuleContext parenColumnList) {
        if (parenColumnList == null) {
            return List.of();
        }
        ParserRuleContext columnList = child(parenColumnList, Role.COLUMN_LIST);
        if (columnList == null) {
            return List.of();
        }
        return children(columnList, Role.COLUMN_NAME).stream()
                .map(this::name)
                .map(this::lastPart)
                .filter(column -> !column.isBlank())
                .toList();
    }

    final ParserRuleContext first(ParseTree tree, Role role) {
        if (tree == null) {
            return null;
        }
        if (hasRole(tree, role) && tree instanceof ParserRuleContext ctx) {
            return ctx;
        }
        for (ParseTree child : typedChildren(tree)) {
            ParserRuleContext found = first(child, role);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    final ParserRuleContext child(ParseTree target, Role role) {
        List<ParserRuleContext> children = children(target, role);
        return children.isEmpty() ? null : children.get(0);
    }

    final List<ParserRuleContext> children(ParseTree target, Role role) {
        if (target == null) {
            return List.of();
        }
        List<ParserRuleContext> result = new ArrayList<>();
        for (ParseTree child : typedChildren(target)) {
            if (child instanceof ParserRuleContext context && hasRole(context, role)) {
                result.add(context);
            }
        }
        return List.copyOf(result);
    }

    final boolean hasSymbol(ParseTree target, Symbol symbol) {
        return target != null && adapter.hasSymbol(target, symbol);
    }

    final boolean isDirectEquality(ParseTree target) {
        return target != null && adapter.isDirectEquality(target);
    }

    final boolean hasRole(ParseTree tree, Role role) {
        return adapter.hasRole(tree, role);
    }

    final List<ParseTree> typedChildren(ParseTree tree) {
        return adapter.typedChildren(tree);
    }

    final java.util.Optional<String> functionName(ParseTree tree) {
        return adapter.functionName(tree);
    }

    final OracleFullGrammarParseTreeAdapter.OperatorSemantic operatorSemantic(ParseTree tree) {
        return adapter.operatorSemantic(tree);
    }
}
