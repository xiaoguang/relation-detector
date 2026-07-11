package com.relationdetector.core.tokenevent;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import com.relationdetector.contracts.Enums.WarningType;
import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredSqlEvent;

final class TokenEventUnknownStatementDiagnostics {
    private TokenEventUnknownStatementDiagnostics() {
    }

    static List<WarningMessage> warnings(
            SqlStatementRecord statement,
            ParseTree root,
            List<StructuredSqlEvent> structuredEvents,
            Predicate<ParseTree> unknownStatement
    ) {
        UnknownStatementStats stats = count(root,
                new UnknownStatementStats(0, statement.startLine(), statement.startLine()),
                unknownStatement);
        if (statement.sourceType() != StatementSourceType.NATIVE_LOG
                || stats.count() == 0
                || structuredEvents == null
                || !structuredEvents.isEmpty()) {
            return List.of();
        }
        return List.of(WarningMessage.warn(
                WarningType.PARSE_WARNING,
                "TOKEN_EVENT_UNKNOWN_STATEMENT_SKIPPED",
                "Token-event grammar skipped unsupported statement structure",
                statement.sourceName(),
                stats.firstLine(),
                Map.of("unknownStatementCount", stats.count())));
    }

    private static UnknownStatementStats count(
            ParseTree tree,
            UnknownStatementStats stats,
            Predicate<ParseTree> unknownStatement
    ) {
        if (tree == null) {
            return stats;
        }
        UnknownStatementStats current = stats;
        if (unknownStatement.test(tree)) {
            long line = stats.firstLine();
            if (tree instanceof ParserRuleContext context && context.getStart() != null) {
                line = stats.statementStartLine() + Math.max(0, context.getStart().getLine() - 1);
            }
            current = new UnknownStatementStats(stats.count() + 1, stats.statementStartLine(),
                    stats.count() == 0 ? line : stats.firstLine());
        }
        for (int index = 0; index < tree.getChildCount(); index++) {
            current = count(tree.getChild(index), current, unknownStatement);
        }
        return current;
    }

    private record UnknownStatementStats(int count, long statementStartLine, long firstLine) {
    }
}
