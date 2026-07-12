package com.relationdetector.mysql.fullgrammar.common;

import java.util.List;
import java.util.Optional;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import com.relationdetector.core.fullgrammar.FullGrammarParseTreeAdapter;
import com.relationdetector.core.fullgrammar.FullGrammarColumnReference;

/** Version-owned typed access to generated MySQL expression contexts. */
public interface MySqlExpressionContextAdapter extends FullGrammarParseTreeAdapter {
    boolean isArithmeticExpression(ParseTree tree);

    ConditionalParts conditionalParts(ParseTree tree);

    QueryParts firstQuery(ParseTree tree);

    String singleProjectionQualifier(ParseTree fromClause, String fallback);

    Optional<List<ParseTree>> runtimeDateArguments(ParseTree tree);

    String joinKind(ParseTree tree);

    String firstTableName(ParseTree tree);

    List<String> insertTargets(ParseTree fields);

    List<ProjectionItem> selectItems(ParseTree tree);

    List<ProjectionItem> topLevelProjectionItems(ParseTree tree);

    /** Resolves only transparent wrappers around one typed direct-column projection. */
    default Optional<FullGrammarColumnReference> directProjectionColumn(ParseTree tree) {
        ParseTree current = tree;
        while (current != null) {
            Optional<FullGrammarColumnReference> direct = directColumn(current);
            if (direct.isPresent()) {
                return direct;
            }
            if (hasRole(current, Role.QUERY_BOUNDARY)
                    || hasRole(current, Role.SCALAR_SUBQUERY)
                    || hasRole(current, Role.FUNCTION_CALL)
                    || hasRole(current, Role.CASE_EXPRESSION)
                    || hasRole(current, Role.AGGREGATE_FUNCTION)
                    || hasRole(current, Role.WINDOW_FUNCTION)
                    || functionName(current).isPresent()
                    || operatorSemantic(current) != OperatorSemantic.NONE
                    || isNonColumnValue(current)) {
                return Optional.empty();
            }
            List<ParseTree> children = typedChildren(current);
            if (children.size() != 1) {
                return Optional.empty();
            }
            current = children.get(0);
        }
        return Optional.empty();
    }

    @Override
    default OperatorSemantic operatorSemantic(ParseTree tree) {
        return isArithmeticExpression(tree) ? OperatorSemantic.ARITHMETIC : OperatorSemantic.NONE;
    }

    @Override
    default CaseParts caseParts(ParseTree tree) {
        ConditionalParts parts = conditionalParts(tree);
        return parts.conditional()
                ? new CaseParts(true, parts.values(), parts.controls())
                : CaseParts.NONE;
    }

    record ProjectionItem(ParserRuleContext context, ParseTree expression, ParseTree explicitAlias) { }

    record ConditionalParts(boolean conditional, List<ParseTree> values, List<ParseTree> controls) {
        public static final ConditionalParts NONE = new ConditionalParts(false, List.of(), List.of());

        public ConditionalParts {
            values = List.copyOf(values);
            controls = List.copyOf(controls);
        }
    }

    record QueryParts(
            List<ParseTree> projections,
            ParseTree fromClause,
            List<ParseTree> joinPredicates,
            ParseTree wherePredicate,
            ParseTree groupBy,
            ParseTree havingPredicate
    ) {
        public QueryParts {
            projections = List.copyOf(projections);
            joinPredicates = List.copyOf(joinPredicates);
        }
    }
}
