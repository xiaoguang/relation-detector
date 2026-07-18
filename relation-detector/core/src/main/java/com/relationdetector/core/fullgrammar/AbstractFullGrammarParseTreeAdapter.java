package com.relationdetector.core.fullgrammar;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.antlr.v4.runtime.tree.ParseTree;

/**
 * CN: 为 version-local generated context adapter 保存不可变的 context-class 到 semantic-role 绑定，不使用反射名称。
 * EN: Stores immutable generated-context-class to semantic-role bindings for version-local adapters without reflective names.
 */
public abstract class AbstractFullGrammarParseTreeAdapter implements FullGrammarParseTreeAdapter {
    private final Map<Role, List<Class<?>>> roleTypes;

    protected AbstractFullGrammarParseTreeAdapter(RoleBinding... bindings) {
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
