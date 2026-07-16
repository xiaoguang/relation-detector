package com.relationdetector.postgres.fullgrammar.v18;

import java.util.ArrayDeque;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import org.antlr.v4.runtime.tree.ParseTree;
import com.relationdetector.contracts.Enums.WarningType;
import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.core.fullgrammar.FullGrammarEventFacade;
import com.relationdetector.postgres.common.PostgresSetProjectionLayout;
import com.relationdetector.postgres.fullgrammar.v18.PostgresFullGrammarParser.*;

/**
 *
 * Coordinates PostgreSQL set-operation projection layout for the v18 typed contexts.
 */
final class PostgresSetProjectionSupport {
    private final SqlStatementRecord statement;
    private final FullGrammarEventFacade sink;
    private final List<WarningMessage> warnings;
    private final Function<ParseTree, String> projectedColumnName;
    private final ArrayDeque<PostgresSetProjectionLayout.Cursor> layouts = new ArrayDeque<>();
    private final ArrayDeque<List<String>> columnHints = new ArrayDeque<>();

    PostgresSetProjectionSupport(SqlStatementRecord statement, FullGrammarEventFacade sink,
            List<WarningMessage> warnings, Function<ParseTree, String> projectedColumnName) {
        this.statement = statement; this.sink = sink; this.warnings = warnings;
        this.projectedColumnName = projectedColumnName;
    }

    void pushColumnHints(List<String> hints) { columnHints.push(hints); }
    void popColumnHints() { columnHints.pop(); }
    String nextOutputColumn(String fallback) {
        return layouts.isEmpty() ? fallback : layouts.peek().nextOr(fallback);
    }

    boolean visit(Select_clauseContext context, Consumer<ParseTree> visitor) {
        if (context.UNION().isEmpty() || sink.currentProjectionOwner().isBlank()) return false;
        List<Simple_select_pramaryContext> branches = context.simple_select_intersect().stream()
                .flatMap(group -> group.simple_select_pramary().stream())
                .flatMap(this::leafBranches).toList();
        List<Target_elContext> first = branches.isEmpty() ? List.of() : targets(branches.get(0));
        PostgresSetProjectionLayout layout = PostgresSetProjectionLayout.resolve(
                columnHints.isEmpty() ? List.of() : columnHints.peek(),
                first.stream().map(this::projectedName).toList(),
                branches.stream().map(this::branchArity).toList());
        if (!layout.arityMatches()) warnings.add(WarningMessage.warn(WarningType.PARSE_WARNING,
                "POSTGRES_SET_OPERATION_ARITY_MISMATCH",
                "PostgreSQL set-operation branches do not share one projection arity",
                statement.sourceName(), statement.startLine() + context.getStart().getLine() - 1));
        PostgresSetProjectionLayout.Cursor cursor = new PostgresSetProjectionLayout.Cursor(layout.columns());
        layouts.push(cursor);
        try {
            for (Simple_select_pramaryContext branch : branches) { cursor.reset(); visitor.accept(branch); }
        } finally { layouts.pop(); }
        return true;
    }

    private List<Target_elContext> targets(Simple_select_pramaryContext branch) {
        if (branch.target_list() != null) return branch.target_list().target_el();
        if (branch.target_list_() != null && branch.target_list_().target_list() != null)
            return branch.target_list_().target_list().target_el();
        return List.of();
    }

    private int branchArity(Simple_select_pramaryContext branch) {
        List<Target_elContext> targets = targets(branch);
        return PostgresSetProjectionLayout.branchArity(
                targets.size(), targets.stream().anyMatch(Target_starContext.class::isInstance));
    }

    private Stream<Simple_select_pramaryContext> leafBranches(Simple_select_pramaryContext branch) {
        if (branch.select_with_parens() == null) return Stream.of(branch);
        Select_with_parensContext parenthesized = branch.select_with_parens();
        while (parenthesized.select_with_parens() != null) parenthesized = parenthesized.select_with_parens();
        if (parenthesized.select_no_parens() == null
                || parenthesized.select_no_parens().select_clause() == null) return Stream.empty();
        return parenthesized.select_no_parens().select_clause().simple_select_intersect().stream()
                .flatMap(group -> group.simple_select_pramary().stream()).flatMap(this::leafBranches);
    }

    private String projectedName(Target_elContext target) {
        if (!(target instanceof Target_labelContext label) || label.a_expr() == null) return "";
        return label.bareColLabel() != null ? label.bareColLabel().getText()
                : label.colLabel() != null ? label.colLabel().getText()
                : projectedColumnName.apply(label.a_expr());
    }
}
