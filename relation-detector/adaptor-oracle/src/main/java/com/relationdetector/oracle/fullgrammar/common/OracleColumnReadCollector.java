package com.relationdetector.oracle.fullgrammar.common;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import com.relationdetector.oracle.fullgrammar.common.OracleFullGrammarParseTreeAdapter.Role;
import com.relationdetector.oracle.routine.OracleRoutineScope;

/**
 *
 * Collects physical Oracle column reads while excluding typed routine symbols.
 */
final class OracleColumnReadCollector extends OracleFullGrammarParseTreeSupport {
    private final Supplier<String> defaultAlias;
    private final OracleRoutineScope routineScope;

    OracleColumnReadCollector(
            OracleSqlEventVisitorCore core,
            OracleFullGrammarParseTreeAdapter adapter,
            Supplier<String> defaultAlias,
            OracleRoutineScope routineScope
    ) {
        super(core, adapter);
        this.defaultAlias = defaultAlias;
        this.routineScope = routineScope;
    }

    List<OracleColumnRead> reads(ParseTree tree) {
        Map<String, OracleColumnRead> reads = new LinkedHashMap<>();
        collect(tree, reads);
        return new ArrayList<>(reads.values());
    }

    void add(String raw, Map<String, OracleColumnRead> reads) {
        String value = core.clean(raw);
        int dot = value.lastIndexOf('.');
        if (dot < 0) {
            String alias = defaultAlias.get();
            String column = core.clean(value);
            if (!column.isBlank() && !routineScope.isSymbol(column)) {
                reads.putIfAbsent(alias + "." + column, new OracleColumnRead(alias, column));
            }
            return;
        }
        if (dot == 0 || dot == value.length() - 1) {
            return;
        }
        String alias = core.clean(value.substring(0, dot));
        if (alias.startsWith(":")) {
            alias = alias.substring(1);
        }
        String column = core.clean(value.substring(dot + 1));
        if (!alias.isBlank() && !column.isBlank()) {
            reads.putIfAbsent(alias + "." + column, new OracleColumnRead(alias, column));
        }
    }

    private void collect(ParseTree tree, Map<String, OracleColumnRead> reads) {
        if (tree == null) {
            return;
        }
        if (hasRole(tree, Role.BIND_VARIABLE) || hasRole(tree, Role.COLUMN_REFERENCE)) {
            add(name(tree), reads);
            return;
        }
        if (hasRole(tree, Role.GENERAL_ELEMENT)) {
            String text = name(tree);
            if (!text.contains("(") && text.contains(".")) {
                add(text, reads);
                return;
            }
            List<ParserRuleContext> parts = children(tree, Role.GENERAL_ELEMENT_PART);
            if (!text.contains("(") && parts.size() == 1
                    && children(parts.get(0), Role.FUNCTION_ARGUMENT).isEmpty()) {
                add(text, reads);
                return;
            }
        }
        for (ParseTree child : typedChildren(tree)) {
            collect(child, reads);
        }
    }
}
