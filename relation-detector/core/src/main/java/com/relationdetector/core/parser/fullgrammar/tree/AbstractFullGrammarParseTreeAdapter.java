package com.relationdetector.core.parser.fullgrammar.tree;

import java.util.EnumMap;
import java.util.Objects;
import java.util.function.Predicate;

import org.antlr.v4.runtime.tree.ParseTree;

/**
 * CN: 为 version-local generated context adapter 保存 typed predicate 到 semantic-role 绑定，不使用 class reflection。
 * EN: Stores typed-predicate-to-semantic-role bindings for version-local adapters without class reflection.
 */
public abstract class AbstractFullGrammarParseTreeAdapter implements FullGrammarParseTreeAdapter {
    private final EnumMap<Role, Predicate<ParseTree>> rolePredicates;

    protected AbstractFullGrammarParseTreeAdapter(RoleBinding... bindings) {
        EnumMap<Role, Predicate<ParseTree>> copy = new EnumMap<>(Role.class);
        for (RoleBinding binding : bindings) {
            copy.put(binding.semanticRole(), binding.predicate());
        }
        this.rolePredicates = copy;
    }

    protected static RoleBinding role(Role role, Predicate<ParseTree> predicate) {
        return new RoleBinding(role, predicate);
    }

    @Override
    public final boolean hasRole(ParseTree tree, Role role) {
        if (tree == null) {
            return false;
        }
        Predicate<ParseTree> predicate = rolePredicates.get(role);
        return predicate != null && predicate.test(tree);
    }

    protected record RoleBinding(Role semanticRole, Predicate<ParseTree> predicate) {
        protected RoleBinding {
            Objects.requireNonNull(semanticRole, "semanticRole");
            Objects.requireNonNull(predicate, "predicate");
        }
    }
}
