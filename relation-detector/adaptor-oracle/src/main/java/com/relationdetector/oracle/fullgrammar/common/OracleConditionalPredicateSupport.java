package com.relationdetector.oracle.fullgrammar.common;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import com.relationdetector.contracts.parse.ExpressionSource;
import com.relationdetector.contracts.parse.PredicateGuard;
import com.relationdetector.oracle.fullgrammar.common.OracleFullGrammarParseTreeAdapter.Role;

/**
 *
 * Collects typed Oracle CASE and conjunction guards without owning SQL traversal.
 */
final class OracleConditionalPredicateSupport extends OracleFullGrammarParseTreeSupport {
    private final OracleFullGrammarExpressionSupport expressions;

    OracleConditionalPredicateSupport(
            OracleSqlEventVisitorCore core,
            OracleFullGrammarParseTreeAdapter adapter,
            OracleFullGrammarExpressionSupport expressions
    ) {
        super(core, adapter);
        this.expressions = expressions;
    }

    void visitGuardedPredicate(ParserRuleContext context, Runnable visitor) {
        core.withPredicateGuards(literalEqualityGuards(context), visitor);
    }

    void visitLogicalExpression(ParserRuleContext context, Runnable visitor) {
        if (adapter().isConjunction(context)) {
            visitGuardedPredicate(context, visitor);
        } else {
            visitor.run();
        }
    }

    void visitCaseExpression(
            ParserRuleContext context,
            Consumer<ParseTree> visit,
            Consumer<ParserRuleContext> visitChildren
    ) {
        ParserRuleContext simple = child(context, Role.SIMPLE_CASE_EXPRESSION);
        ParserRuleContext searched = child(context, Role.SEARCHED_CASE_EXPRESSION);
        ParserRuleContext owner = simple != null ? simple : searched;
        if (owner == null) {
            visitChildren.accept(context);
            return;
        }
        List<ParserRuleContext> ownerExpressions = children(owner, Role.EXPRESSION);
        ParserRuleContext selector = simple == null || ownerExpressions.isEmpty()
                ? null : ownerExpressions.get(0);
        if (selector != null) visit.accept(selector);
        for (ParserRuleContext part : children(owner, Role.CASE_WHEN_PART)) {
            List<ParserRuleContext> branch = children(part, Role.EXPRESSION);
            if (branch.size() != 2) continue;
            List<PredicateGuard> guards = selector == null
                    ? literalEqualityGuards(branch.get(0))
                    : equalsLiteralGuard(selector, branch.get(0)).stream().toList();
            core.withPredicateGuards(guards, () -> {
                visit.accept(branch.get(0));
                visit.accept(branch.get(1));
            });
        }
        ParserRuleContext elsePart = child(owner, Role.CASE_ELSE_PART);
        if (elsePart != null) visitChildren.accept(elsePart);
    }

    private List<PredicateGuard> literalEqualityGuards(ParseTree tree) {
        List<PredicateGuard> result = new ArrayList<>();
        collectLiteralEqualityGuards(tree, result);
        return result.stream().distinct().toList();
    }

    private void collectLiteralEqualityGuards(ParseTree tree, List<PredicateGuard> result) {
        if (!(tree instanceof ParserRuleContext context)) return;
        if (hasRole(context, Role.RELATIONAL_EXPRESSION)
                && isDirectEquality(child(context, Role.RELATIONAL_OPERATOR))) {
            List<ParserRuleContext> parts = children(context, Role.RELATIONAL_EXPRESSION);
            if (parts.size() == 2) {
                equalsLiteralGuard(parts.get(0), parts.get(1)).ifPresent(result::add);
                equalsLiteralGuard(parts.get(1), parts.get(0)).ifPresent(result::add);
            }
        }
        for (ParseTree child : adapter().typedChildren(context)) {
            collectLiteralEqualityGuards(child, result);
        }
    }

    private Optional<PredicateGuard> equalsLiteralGuard(ParseTree columnTree, ParseTree literalTree) {
        OracleColumnRead column = expressions.singleDirectColumn(columnTree);
        Optional<String> literal = adapter().literalValue(literalTree);
        if (column == null || literal.isEmpty()) return Optional.empty();
        return Optional.of(new PredicateGuard(
                new ExpressionSource(column.alias(), column.column()), "EQUALS", literal.get()));
    }
}
