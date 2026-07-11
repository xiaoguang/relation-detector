package com.relationdetector.mysql.fullgrammer.common;

import java.util.List;
import java.util.Optional;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import com.relationdetector.core.fullgrammer.FullGrammerParseTreeAdapter;

/** Version-owned typed access to generated MySQL expression contexts. */
public interface MySqlExpressionContextAdapter extends FullGrammerParseTreeAdapter {
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
