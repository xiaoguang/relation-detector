package com.relationdetector.postgres.fullgrammar.v17;

import java.util.Locale;
import java.util.List;
import java.util.Optional;

import org.antlr.v4.runtime.Token;

import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.postgres.routine.PlPgSqlStringBody;
import com.relationdetector.postgres.routine.PostgresRoutineBodyText;
import com.relationdetector.postgres.routine.PostgresRoutineDescriptor;
import com.relationdetector.postgres.routine.PostgresRoutineIdentity;
import com.relationdetector.postgres.routine.PostgresRoutineBody;
import com.relationdetector.postgres.routine.PostgresRoutineStatementFactory;
import com.relationdetector.postgres.routine.SqlAtomicBody;
import com.relationdetector.postgres.routine.SqlStringBody;
import com.relationdetector.postgres.routine.UnsupportedRoutineBody;

/**
 *
 * Adapts the version-local generated routine declaration to the shared descriptor.
 */
final class RoutineDeclarationAdapter {
    private final List<Token> visibleTokens;

    RoutineDeclarationAdapter(List<Token> visibleTokens) {
        this.visibleTokens = List.copyOf(visibleTokens);
    }

    java.util.List<String> parameterNames(PostgresFullGrammarParser.CreatefunctionstmtContext declaration) {
        var list = declaration.func_args_with_defaults().func_args_with_defaults_list();
        if (list == null) return java.util.List.of();
        return list.func_arg_with_default().stream()
                .map(item -> item.func_arg().param_name())
                .filter(java.util.Objects::nonNull)
                .map(org.antlr.v4.runtime.tree.ParseTree::getText)
                .toList();
    }

    private java.util.List<String> inputParameterTypes(
            PostgresFullGrammarParser.CreatefunctionstmtContext declaration) {
        var arguments = declaration.func_args_with_defaults();
        if (arguments == null || arguments.func_args_with_defaults_list() == null) {
            return java.util.List.of();
        }
        return arguments.func_args_with_defaults_list().func_arg_with_default().stream()
                .map(PostgresFullGrammarParser.Func_arg_with_defaultContext::func_arg)
                .filter(argument -> argument.arg_class() == null
                        || argument.arg_class().OUT_P() == null
                        || argument.arg_class().IN_P() != null)
                .map(argument -> PostgresRoutineIdentity.typedText(visibleTokens, argument.func_type()))
                .toList();
    }

    Optional<PostgresRoutineDescriptor> describe(
            PostgresFullGrammarParser.CreatefunctionstmtContext declaration,
            SqlStatementRecord statement
    ) {
        var options = declaration.createfunc_opt_list() == null ? java.util.List
                .<PostgresFullGrammarParser.Createfunc_opt_itemContext>of()
                : declaration.createfunc_opt_list().createfunc_opt_item();
        var bodyItem = options.stream().filter(item -> item.func_as() != null).findFirst();
        String language = options.stream()
                .filter(item -> item.LANGUAGE() != null && item.nonreservedword_or_sconst() != null)
                .map(item -> item.nonreservedword_or_sconst().getText().replace("'", "")
                        .toLowerCase(Locale.ROOT))
                .findFirst().orElse("");
        String objectType = declaration.PROCEDURE() == null ? "FUNCTION" : "PROCEDURE";
        String objectName = declaration.func_name().getText();
        String objectIdentity = PostgresRoutineIdentity.preserveCollectedIdentity(statement,
                PostgresRoutineIdentity.signature(objectName, inputParameterTypes(declaration)));
        if (declaration.routine_sql_body() != null) {
            var statements = declaration.routine_sql_body().stmtmulti().stmt().stream()
                    .map(item -> PostgresRoutineStatementFactory.fromContext(statement, item))
                    .toList();
            int startLine = Math.toIntExact(statement.startLine()
                    + declaration.routine_sql_body().getStart().getLine() - 1L);
            return Optional.of(new PostgresRoutineDescriptor(language,
                    new SqlAtomicBody(statements, startLine),
                    objectType, objectName, objectIdentity, statement.attributes()));
        }
        if (bodyItem.isEmpty()) return Optional.empty();
        var body = bodyItem.get().func_as().sconst(0);
        int startLine = Math.toIntExact(statement.startLine() + Math.max(0, body.getStart().getLine() - 1));
        String text = PostgresRoutineBodyText.unquote(body.getText());
        PostgresRoutineBody routineBody = switch (language) {
            case "plpgsql" -> new PlPgSqlStringBody(text, startLine);
            case "sql", "" -> new SqlStringBody(text, startLine);
            default -> new UnsupportedRoutineBody(language, startLine);
        };
        return Optional.of(new PostgresRoutineDescriptor(language, routineBody,
                objectType, objectName, objectIdentity, statement.attributes()));
    }
}
