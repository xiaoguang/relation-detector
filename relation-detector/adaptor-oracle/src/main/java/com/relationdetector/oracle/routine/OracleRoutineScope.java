package com.relationdetector.oracle.routine;

import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 *
 * Oracle routine-scope state shared by token-event and full-grammar visitors.
 *
 * <p>Oracle PL/SQL bodies are parsed by the outer Oracle grammar, unlike
 * PostgreSQL dollar-quoted bodies that need a second body parser. This class
 * keeps the routine boundary in the dialect-level package so parser modes do
 * not duplicate routine ownership rules.
 */
public final class OracleRoutineScope {
    private final ArrayDeque<ScopeFrame> scopes = new ArrayDeque<>();

    public void enterRoutine() {
        scopes.push(new ScopeFrame(ScopeKind.ROUTINE, new LinkedHashSet<>()));
    }

    public void enterBlock() {
        if (insideRoutine()) {
            scopes.push(new ScopeFrame(ScopeKind.BLOCK, new LinkedHashSet<>()));
        }
    }

    public void declare(String identifier) {
        String normalized = normalize(identifier);
        if (!scopes.isEmpty() && !normalized.isBlank()) {
            scopes.peek().symbols().add(normalized);
        }
    }

    public boolean isSymbol(String identifier) {
        String normalized = normalize(identifier);
        return !normalized.isBlank()
                && scopes.stream().anyMatch(scope -> scope.symbols().contains(normalized));
    }

    public void leaveRoutine() {
        while (!scopes.isEmpty()) {
            if (scopes.pop().kind() == ScopeKind.ROUTINE) {
                return;
            }
        }
    }

    public void leaveRoutineEnd(boolean controlBlockEnd) {
        if (controlBlockEnd || scopes.isEmpty()) {
            return;
        }
        if (scopes.peek().kind() == ScopeKind.BLOCK) {
            scopes.pop();
        } else {
            leaveRoutine();
        }
    }

    public boolean insideRoutine() {
        return scopes.stream().anyMatch(scope -> scope.kind() == ScopeKind.ROUTINE);
    }

    private String normalize(String identifier) {
        if (identifier == null) {
            return "";
        }
        String value = identifier.trim();
        if (value.startsWith(":")) {
            value = value.substring(1);
        }
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1).replace("\"\"", "\"");
        }
        return value.toLowerCase(Locale.ROOT);
    }

    private enum ScopeKind {
        ROUTINE,
        BLOCK
    }

    private record ScopeFrame(ScopeKind kind, Set<String> symbols) {
    }
}
