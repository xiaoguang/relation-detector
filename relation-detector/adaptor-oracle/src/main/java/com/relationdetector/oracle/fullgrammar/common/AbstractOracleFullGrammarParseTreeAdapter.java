package com.relationdetector.oracle.fullgrammar.common;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.ParserRuleContext;

/**
 *
 * Immutable class-literal role table shared by the four Oracle adapters.
 */
public abstract class AbstractOracleFullGrammarParseTreeAdapter
        implements OracleFullGrammarParseTreeAdapter {
    private final Map<Role, List<Class<?>>> roles;
    private final Map<Symbol, Integer> symbols;

    @SafeVarargs
    protected AbstractOracleFullGrammarParseTreeAdapter(Map.Entry<Role, List<Class<?>>>... entries) {
        this(Map.of(), entries);
    }

    @SafeVarargs
    protected AbstractOracleFullGrammarParseTreeAdapter(
            Map<Symbol, Integer> symbols,
            Map.Entry<Role, List<Class<?>>>... entries
    ) {
        EnumMap<Role, List<Class<?>>> configured = new EnumMap<>(Role.class);
        for (Map.Entry<Role, List<Class<?>>> entry : entries) {
            configured.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        roles = Map.copyOf(configured);
        this.symbols = symbols == null ? Map.of() : Map.copyOf(symbols);
    }

    @Override
    public final boolean hasRole(ParseTree tree, Role role) {
        if (tree == null) {
            return false;
        }
        return roles.getOrDefault(role, List.of()).stream().anyMatch(type -> type.isInstance(tree));
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
        if (!hasRole(tree, Role.FUNCTION_EXPRESSION) && !hasRole(tree, Role.GENERAL_ELEMENT)) {
            return Optional.empty();
        }
        ParserRuleContext function = functionContext(tree);
        if (function == null || function.getStart() == null) {
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

    private ParserRuleContext functionContext(ParseTree tree) {
        if (!(tree instanceof ParserRuleContext context)) return null;
        if (hasRole(context, Role.FUNCTION_EXPRESSION)) return context;
        for (ParseTree child : typedChildren(context)) {
            if (hasRole(child, Role.GENERAL_ELEMENT_PART)
                    && containsRole(child, Role.FUNCTION_ARGUMENT)) {
                return (ParserRuleContext) child;
            }
        }
        return null;
    }

    private boolean containsRole(ParseTree tree, Role role) {
        if (hasRole(tree, role)) return true;
        for (ParseTree child : typedChildren(tree)) {
            if (containsRole(child, role)) return true;
        }
        return false;
    }

    @SafeVarargs
    protected static Map.Entry<Role, List<Class<?>>> role(Role role, Class<?>... types) {
        return Map.entry(role, List.of(types));
    }
}
