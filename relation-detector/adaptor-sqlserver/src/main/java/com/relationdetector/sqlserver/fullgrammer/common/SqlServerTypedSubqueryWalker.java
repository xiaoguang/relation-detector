package com.relationdetector.sqlserver.fullgrammer.common;

import java.util.function.Consumer;
import java.util.function.Predicate;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import com.relationdetector.core.fullgrammer.FullGrammerParseTreeAdapter;

final class SqlServerTypedSubqueryWalker {
    private SqlServerTypedSubqueryWalker() {
    }

    static void visit(
            ParseTree tree,
            FullGrammerParseTreeAdapter adapter,
            Predicate<ParserRuleContext> scalarSubquery,
            Consumer<ParserRuleContext> visitor
    ) {
        if (tree instanceof ParserRuleContext context && scalarSubquery.test(context)) {
            visitor.accept(context);
            return;
        }
        for (ParseTree child : adapter.typedChildren(tree)) {
            visit(child, adapter, scalarSubquery, visitor);
        }
    }
}
