package com.relationdetector.oracle.fullgrammar.common;

import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * CN: 维护嵌套 Oracle 表达式的 statement-local 可见 rowset alias 栈。
 * EN: Maintains statement-local visible rowset aliases for nested Oracle expressions.
 */
final class OracleQueryScopeStack {
    private final OracleSqlEventVisitorCore core;
    private final ArrayDeque<Set<String>> scopes = new ArrayDeque<>();

    OracleQueryScopeStack(OracleSqlEventVisitorCore core) {
        this.core = core;
    }

    void push() {
        scopes.push(new LinkedHashSet<>());
    }

    void push(Iterable<String> aliases) {
        Set<String> scope = new LinkedHashSet<>();
        for (String alias : aliases) {
            if (alias != null && !alias.isBlank()) {
                scope.add(alias);
            }
        }
        scopes.push(scope);
    }

    void pop() {
        scopes.pop();
    }

    void register(String alias) {
        if (alias != null && !alias.isBlank() && !scopes.isEmpty()) {
            scopes.peek().add(alias);
        }
    }

    String defaultAlias() {
        if (scopes.isEmpty()) {
            return "";
        }
        Set<String> aliases = scopes.peek();
        return aliases.size() == 1 ? aliases.iterator().next() : "";
    }

    boolean isVisible(String qualifier, boolean triggerPseudoRowsVisible) {
        if (qualifier == null || qualifier.isBlank()) {
            return false;
        }
        String normalized = core.normalize(qualifier);
        if (triggerPseudoRowsVisible && ("new".equals(normalized) || "old".equals(normalized))) {
            return true;
        }
        for (Set<String> scope : scopes) {
            if (scope.stream().map(core::normalize).anyMatch(normalized::equals)) {
                return true;
            }
        }
        return false;
    }
}
