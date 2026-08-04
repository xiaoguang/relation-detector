package com.relationdetector.oracle.fullgrammar.common;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.ParserRuleContext;

/**
 * CN: 保存四个 Oracle version adapter 显式注册的 generated-context predicates 与 typed roles；实例不可变且不持有 parse state。
 * EN: Stores generated-context predicates and typed roles explicitly registered by Oracle version adapters. Instances are immutable and retain no parse state.
 */
public abstract class AbstractOracleFullGrammarParseTreeAdapter
        implements OracleFullGrammarParseTreeAdapter {
    private final EnumMap<Role, Predicate<ParseTree>> roles;
    private final Map<Symbol, Integer> symbols;

    protected AbstractOracleFullGrammarParseTreeAdapter(RoleBinding... entries) {
        this(Map.of(), entries);
    }

    protected AbstractOracleFullGrammarParseTreeAdapter(
            Map<Symbol, Integer> symbols,
            RoleBinding... entries
    ) {
        EnumMap<Role, Predicate<ParseTree>> configured = new EnumMap<>(Role.class);
        for (RoleBinding entry : entries) {
            configured.put(entry.semanticRole(), entry.predicate());
        }
        roles = configured;
        this.symbols = symbols == null ? Map.of() : Map.copyOf(symbols);
    }

    @Override
    public final boolean hasRole(ParseTree tree, Role role) {
        if (tree == null) {
            return false;
        }
        Predicate<ParseTree> predicate = roles.get(role);
        return predicate != null && predicate.test(tree);
    }

    @Override
    public final boolean hasSymbol(ParseTree tree, Symbol symbol) {
        Integer tokenType = symbols.get(symbol);
        return tokenType != null
                && tree instanceof ParserRuleContext context
                && context.getToken(tokenType, 0) != null;
    }

    @Override
    public final Optional<String> functionName(ParseTree tree) {
        if (hasRole(tree, Role.GENERAL_ELEMENT)) {
            return generalElementView(tree)
                    .filter(view -> view.kind() == GeneralElementKind.FUNCTION)
                    .flatMap(view -> view.nameParts().stream().reduce((left, right) -> right));
        }
        if (!hasRole(tree, Role.FUNCTION_EXPRESSION)) {
            return Optional.empty();
        }
        ParserRuleContext function = (ParserRuleContext) tree;
        if (function.getStart() == null) {
            return Optional.empty();
        }
        String name = function.getStart().getText();
        return name == null || name.isBlank() ? Optional.empty() : Optional.of(name);
    }

    @Override
    public OperatorSemantic operatorSemantic(ParseTree tree) {
        if (!hasRole(tree, Role.CONCATENATION)) return OperatorSemantic.NONE;
        if (hasSymbol(tree, Symbol.CONCAT)) return OperatorSemantic.CONCAT_FORMAT;
        if (hasSymbol(tree, Symbol.PLUS) || hasSymbol(tree, Symbol.MINUS)
                || hasSymbol(tree, Symbol.MULTIPLY) || hasSymbol(tree, Symbol.DIVIDE)) {
            return OperatorSemantic.ARITHMETIC;
        }
        return OperatorSemantic.NONE;
    }

    protected static RoleBinding role(Role role, Predicate<ParseTree> predicate) {
        return new RoleBinding(role, predicate);
    }

    protected record RoleBinding(Role semanticRole, Predicate<ParseTree> predicate) {
        protected RoleBinding {
            Objects.requireNonNull(semanticRole, "semanticRole");
            Objects.requireNonNull(predicate, "predicate");
        }
    }
}
