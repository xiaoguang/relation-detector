package com.relationdetector.oracle.fullgrammer.common;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import com.relationdetector.oracle.fullgrammer.common.OracleFullGrammerParseTreeAdapter.Role;

/** Stateless typed-role and generated-context access shared by Oracle collectors. */
abstract class OracleFullGrammerParseTreeSupport {
    final OracleSqlEventVisitorCore core;
    private final OracleFullGrammerParseTreeAdapter adapter;

    OracleFullGrammerParseTreeSupport(
            OracleSqlEventVisitorCore core,
            OracleFullGrammerParseTreeAdapter adapter
    ) {
        this.core = core;
        this.adapter = adapter;
    }

    final String tableFrom(ParserRuleContext ctx) {
        if (ctx == null) {
            return "";
        }
        if (hasRole(ctx, Role.TABLE_REF_INTERNAL)) {
            return tableFrom(child(ctx, "dml_table_expression_clause"));
        }
        ParserRuleContext dml = child(ctx, "dml_table_expression_clause");
        if (dml != null) {
            return tableFrom(dml);
        }
        ParserRuleContext tableview = child(ctx, "tableview_name");
        return tableview == null ? "" : name(tableview);
    }

    final String aliasFrom(ParserRuleContext selectedTableview, String table) {
        ParserRuleContext alias = child(selectedTableview, "table_alias");
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
        ParserRuleContext columnList = child(parenColumnList, "column_list");
        if (columnList == null) {
            return List.of();
        }
        return children(columnList, "column_name").stream()
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
        for (int index = 0; index < tree.getChildCount(); index++) {
            ParserRuleContext found = first(tree.getChild(index), role);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    final ParserRuleContext child(Object target, String methodName) {
        List<ParserRuleContext> children = children(target, methodName);
        return children.isEmpty() ? null : children.get(0);
    }

    final List<ParserRuleContext> children(Object target, String methodName) {
        if (target == null) {
            return List.of();
        }
        Object value = invoke(target, methodName);
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            return list.stream()
                    .filter(ParserRuleContext.class::isInstance)
                    .map(ParserRuleContext.class::cast)
                    .toList();
        }
        if (value instanceof ParserRuleContext context) {
            return List.of(context);
        }
        return List.of();
    }

    final Object node(Object target, String methodName) {
        return target == null ? null : invoke(target, methodName);
    }

    private Object invoke(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (NoSuchMethodException ignored) {
            return null;
        } catch (IllegalAccessException | InvocationTargetException exception) {
            throw new IllegalStateException("Failed to read Oracle parse context " + methodName, exception);
        }
    }

    final boolean hasRole(ParseTree tree, Role role) {
        return adapter.hasRole(tree, role);
    }
}
