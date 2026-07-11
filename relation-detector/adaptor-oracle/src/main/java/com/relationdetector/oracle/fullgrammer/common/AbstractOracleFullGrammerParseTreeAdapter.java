package com.relationdetector.oracle.fullgrammer.common;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.antlr.v4.runtime.tree.ParseTree;

/** Immutable class-literal role table shared by the four Oracle adapters. */
public abstract class AbstractOracleFullGrammerParseTreeAdapter
        implements OracleFullGrammerParseTreeAdapter {
    private final Map<Role, List<Class<?>>> roles;

    @SafeVarargs
    protected AbstractOracleFullGrammerParseTreeAdapter(Map.Entry<Role, List<Class<?>>>... entries) {
        EnumMap<Role, List<Class<?>>> configured = new EnumMap<>(Role.class);
        for (Map.Entry<Role, List<Class<?>>> entry : entries) {
            configured.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        roles = Map.copyOf(configured);
    }

    @Override
    public final boolean hasRole(ParseTree tree, Role role) {
        if (tree == null) {
            return false;
        }
        return roles.getOrDefault(role, List.of()).stream().anyMatch(type -> type.isInstance(tree));
    }

    @SafeVarargs
    protected static Map.Entry<Role, List<Class<?>>> role(Role role, Class<?>... types) {
        return Map.entry(role, List.of(types));
    }
}
