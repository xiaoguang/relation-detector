package com.relationdetector.core.fullgrammer;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.antlr.v4.runtime.tree.ParseTree;

/** Immutable class-to-role binding shared by version context adapters. */
public abstract class AbstractFullGrammerParseTreeAdapter implements FullGrammerParseTreeAdapter {
    private final Map<Role, List<Class<?>>> roleTypes;

    protected AbstractFullGrammerParseTreeAdapter(RoleBinding... bindings) {
        EnumMap<Role, List<Class<?>>> copy = new EnumMap<>(Role.class);
        for (RoleBinding binding : bindings) {
            copy.put(binding.role(), binding.types());
        }
        this.roleTypes = Map.copyOf(copy);
    }

    protected static RoleBinding role(Role role, Class<?>... types) {
        return new RoleBinding(role, List.of(types));
    }

    @Override
    public final boolean hasRole(ParseTree tree, Role role) {
        if (tree == null) {
            return false;
        }
        return roleTypes.getOrDefault(role, List.of()).stream().anyMatch(type -> type.isInstance(tree));
    }

    protected record RoleBinding(Role role, List<Class<?>> types) {
        protected RoleBinding {
            types = List.copyOf(types);
        }
    }
}
