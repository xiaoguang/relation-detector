package com.relationdetector.oracle.fullgrammar.common;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import com.relationdetector.oracle.fullgrammar.common.OracleFullGrammarParseTreeAdapter.Role;
import com.relationdetector.oracle.fullgrammar.common.OracleFullGrammarParseTreeAdapter.GeneralElementKind;
import com.relationdetector.oracle.routine.OracleRoutineScope;

/**
 * CN: 递归访问 adapter 提供的 typed expression children，收集 direct columns 并通过 OracleRoutineScope 排除参数/局部变量；歧义 context 不按 terminal text 回退。
 * EN: Visits typed expression children supplied by the adapter, collecting direct columns while excluding parameters and locals through OracleRoutineScope. Ambiguous contexts never fall back to terminal text.
 */
final class OracleColumnReadCollector extends OracleFullGrammarParseTreeSupport {
    private final Supplier<String> defaultAlias;
    private final Predicate<String> visibleQualifier;
    private final OracleRoutineScope routineScope;

    OracleColumnReadCollector(
            OracleSqlEventVisitorCore core,
            OracleFullGrammarParseTreeAdapter adapter,
            Supplier<String> defaultAlias,
            Predicate<String> visibleQualifier,
            OracleRoutineScope routineScope
    ) {
        super(core, adapter);
        this.defaultAlias = defaultAlias;
        this.visibleQualifier = visibleQualifier;
        this.routineScope = routineScope;
    }

    List<OracleColumnRead> reads(ParseTree tree) {
        Map<String, OracleColumnRead> reads = new LinkedHashMap<>();
        collect(tree, reads);
        return new ArrayList<>(reads.values());
    }

    void add(ParseTree context, String raw, Map<String, OracleColumnRead> reads) {
        String value = core.clean(raw);
        int dot = value.lastIndexOf('.');
        if (dot < 0) {
            String alias = defaultAliasFor(context);
            String column = core.clean(value);
            if (canResolveUnqualifiedColumn() && !alias.isBlank() && !column.isBlank()
                    && !routineScope.isSymbol(column)) {
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
        if (!alias.isBlank() && !column.isBlank()
                && !routineScope.isSymbol(alias)
                && isVisibleQualifier(context, alias)) {
            reads.putIfAbsent(alias + "." + column, new OracleColumnRead(alias, column));
        }
    }

    void addGeneralElement(ParseTree tree, Map<String, OracleColumnRead> reads) {
        adapter().generalElementView(tree).ifPresent(view -> {
            if (view.kind() == GeneralElementKind.FUNCTION) {
                view.argumentExpressions().forEach(argument -> collect(argument, reads));
            } else if (view.kind() == GeneralElementKind.COLUMN_CANDIDATE) {
                OracleColumnRead column = typedColumn(
                        view.nameParts(), defaultAliasFor(tree),
                        qualifier -> isVisibleQualifier(tree, qualifier));
                if (column != null) {
                    reads.putIfAbsent(column.alias() + "." + column.column(), column);
                }
            }
        });
    }

    OracleColumnRead directGeneralElement(ParseTree tree, java.util.Set<String> visibleAliases) {
        return adapter().generalElementView(tree)
                .filter(view -> view.kind() == GeneralElementKind.COLUMN_CANDIDATE)
                .map(view -> typedColumn(
                        view.nameParts(),
                        visibleAliases.size() == 1 ? visibleAliases.iterator().next() : "",
                        qualifier -> visibleAliases.stream()
                                .map(core::normalize)
                                .anyMatch(core.normalize(qualifier)::equals)))
                .orElse(null);
    }

    private OracleColumnRead typedColumn(
            List<String> rawParts,
            String unqualifiedAlias,
            Predicate<String> qualifierVisible
    ) {
        List<String> parts = rawParts.stream().map(core::clean).filter(part -> !part.isBlank()).toList();
        if (parts.size() != rawParts.size() || parts.isEmpty()) {
            return null;
        }
        String column = parts.get(parts.size() - 1);
        if (parts.size() == 1) {
            return !canResolveUnqualifiedColumn()
                    || unqualifiedAlias.isBlank() || routineScope.isSymbol(column)
                    ? null : new OracleColumnRead(unqualifiedAlias, column);
        }
        String qualifier = parts.get(parts.size() - 2);
        return routineScope.isSymbol(qualifier) || !qualifierVisible.test(qualifier)
                ? null : new OracleColumnRead(qualifier, column);
    }

    private boolean canResolveUnqualifiedColumn() {
        return !core.isRoutineSource() || routineScope.insideRoutine();
    }

    private String defaultAliasFor(ParseTree tree) {
        ParseTree current = tree;
        while (current != null) {
            if (hasRole(current, Role.QUERY_BLOCK)) {
                Set<String> aliases = directQueryAliases(current);
                return aliases.size() == 1 ? aliases.iterator().next() : "";
            }
            current = current.getParent();
        }
        return defaultAlias.get();
    }

    private Set<String> enclosingQueryAliases(ParseTree tree) {
        Set<String> aliases = new LinkedHashSet<>();
        ParseTree current = tree;
        while (current != null) {
            if (hasRole(current, Role.QUERY_BLOCK)) {
                aliases.addAll(directQueryAliases(current));
            }
            current = current.getParent();
        }
        return aliases;
    }

    private boolean isVisibleQualifier(ParseTree tree, String qualifier) {
        if (visibleQualifier.test(qualifier)) {
            return true;
        }
        String normalized = core.normalize(qualifier);
        return enclosingQueryAliases(tree).stream()
                .map(core::normalize)
                .anyMatch(normalized::equals);
    }

    private Set<String> directQueryAliases(ParseTree queryBlock) {
        Set<String> aliases = new LinkedHashSet<>();
        collectDirectQueryAliases(queryBlock, queryBlock, aliases);
        return aliases;
    }

    private void collectDirectQueryAliases(ParseTree root, ParseTree tree, Set<String> aliases) {
        if (tree == null || (tree != root && hasRole(tree, Role.SUBQUERY))) {
            return;
        }
        if (hasRole(tree, Role.TABLE_REF_AUX) && tree instanceof ParserRuleContext tableRef) {
            ParserRuleContext internal = child(tableRef, Role.TABLE_REF_INTERNAL_WRAPPER);
            String table = tableFrom(internal);
            ParserRuleContext alias = child(tableRef, Role.TABLE_ALIAS);
            if (alias != null) {
                aliases.add(name(alias));
            } else if (!table.isBlank()) {
                aliases.add(core.baseName(table));
            }
            return;
        }
        for (ParseTree child : typedChildren(tree)) {
            collectDirectQueryAliases(root, child, aliases);
        }
    }

    private void collect(ParseTree tree, Map<String, OracleColumnRead> reads) {
        if (tree == null) {
            return;
        }
        if (hasRole(tree, Role.BIND_VARIABLE) || hasRole(tree, Role.COLUMN_REFERENCE)) {
            add(tree, name(tree), reads);
            return;
        }
        if (hasRole(tree, Role.GENERAL_ELEMENT)) {
            addGeneralElement(tree, reads);
            return;
        }
        for (ParseTree child : typedChildren(tree)) {
            collect(child, reads);
        }
    }
}
