package com.relationdetector.postgres.fullgrammar.v18;

import java.util.Locale;
import java.util.Optional;

import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.postgres.routine.PostgresRoutineBodyKind;
import com.relationdetector.postgres.routine.PostgresRoutineBodyText;
import com.relationdetector.postgres.routine.PostgresRoutineDescriptor;

/** Adapts the version-local generated routine declaration to the shared descriptor. */
final class RoutineDeclarationAdapter {
    java.util.List<String> parameterNames(PostgresFullGrammarParser.CreatefunctionstmtContext declaration) {
        var list = declaration.func_args_with_defaults().func_args_with_defaults_list();
        if (list == null) return java.util.List.of();
        return list.func_arg_with_default().stream()
                .map(item -> item.func_arg().param_name())
                .filter(java.util.Objects::nonNull)
                .map(org.antlr.v4.runtime.tree.ParseTree::getText)
                .toList();
    }

    Optional<PostgresRoutineDescriptor> describe(
            PostgresFullGrammarParser.CreatefunctionstmtContext declaration,
            SqlStatementRecord statement
    ) {
        var options = declaration.createfunc_opt_list().createfunc_opt_item();
        var bodyItem = options.stream().filter(item -> item.func_as() != null).findFirst();
        if (bodyItem.isEmpty()) return Optional.empty();
        String language = options.stream()
                .filter(item -> item.LANGUAGE() != null && item.nonreservedword_or_sconst() != null)
                .map(item -> item.nonreservedword_or_sconst().getText().replace("'", "")
                        .toLowerCase(Locale.ROOT))
                .findFirst().orElse("");
        var body = bodyItem.get().func_as().sconst(0);
        PostgresRoutineBodyKind kind = switch (language) {
            case "plpgsql" -> PostgresRoutineBodyKind.PLPGSQL;
            case "sql" -> PostgresRoutineBodyKind.SQL_STRING;
            default -> PostgresRoutineBodyKind.UNSUPPORTED_LANGUAGE;
        };
        return Optional.of(new PostgresRoutineDescriptor(kind, language,
                PostgresRoutineBodyText.unquote(body.getText()),
                Math.toIntExact(statement.startLine() + Math.max(0, body.getStart().getLine() - 1)),
                declaration.PROCEDURE() == null ? "FUNCTION" : "PROCEDURE",
                declaration.func_name().getText()));
    }
}
