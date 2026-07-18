package com.relationdetector.core.identity;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.relationdetector.contracts.model.TableId;

/**
 * CN: 维护 query-scope alias symbol table，按完整 namespace identity 解析并处理内层 shadowing/ambiguity。
 * EN: Maintains query-scoped aliases with full namespace identity, inner-scope shadowing, and ambiguity detection.
 */
public final class AliasSymbolTable {
    private final CanonicalIdentifierResolver resolver;
    private final NamespaceContext namespace;
    private final ArrayDeque<Scope> scopes = new ArrayDeque<>();

    public AliasSymbolTable(CanonicalIdentifierResolver resolver, NamespaceContext namespace) {
        this.resolver = resolver;
        this.namespace = namespace == null ? NamespaceContext.empty() : namespace;
        scopes.push(new Scope());
    }

    public void pushScope() {
        scopes.push(new Scope());
    }

    public void popScope() {
        if (scopes.size() <= 1) {
            throw new IllegalStateException("cannot pop root alias scope");
        }
        scopes.pop();
    }

    public void bind(String alias, TableId table) {
        String key = resolver.normalize(alias);
        if (key.isBlank() || table == null) {
            return;
        }
        Scope scope = scopes.peek();
        TableId resolved = resolver.resolve(table, namespace);
        TableId existing = scope.bindings.get(key);
        if (existing != null && !resolvedIdentity(existing).equals(resolvedIdentity(resolved))) {
            scope.bindings.remove(key);
            scope.ambiguous.add(key);
            return;
        }
        if (!scope.ambiguous.contains(key)) {
            scope.bindings.put(key, resolved);
        }
    }

    public Optional<TableId> resolve(String alias) {
        String key = resolver.normalize(alias);
        for (Scope scope : scopes) {
            if (scope.ambiguous.contains(key)) {
                return Optional.empty();
            }
            TableId table = scope.bindings.get(key);
            if (table != null) {
                return Optional.of(table);
            }
        }
        return Optional.empty();
    }

    public TableId resolveQualified(String qualified) {
        return resolver.resolveQualified(qualified, namespace);
    }

    public String normalizeIdentifier(String identifier) {
        return resolver.normalize(identifier);
    }

    public boolean sameTable(TableId left, TableId right) {
        return left != null
                && right != null
                && resolvedIdentity(left).equals(resolvedIdentity(right));
    }

    private String resolvedIdentity(TableId table) {
        return resolver.tableKey(table, namespace);
    }

    private static final class Scope {
        private final Map<String, TableId> bindings = new LinkedHashMap<>();
        private final Set<String> ambiguous = new HashSet<>();
    }
}
