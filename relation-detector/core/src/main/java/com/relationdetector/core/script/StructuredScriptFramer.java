package com.relationdetector.core.script;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.relationdetector.contracts.parse.ScriptFrameRequest;
import com.relationdetector.contracts.parse.ScriptFrameResult;
import com.relationdetector.contracts.parse.SqlStatementRecord;

/**
 * CN: 仅使用方言 generated script-lexer tokens 选择 planner 并装配 statements/provenance，不解析 server SQL 语义。
 * EN: Selects a planner and assembles statements and provenance solely from dialect-generated script-lexer tokens without parsing server SQL semantics.
 */
public final class StructuredScriptFramer extends ScriptFramingSupport {
    public ScriptFrameResult frame(
            ScriptFrameRequest request,
            List<ScriptLexeme> sourceLexemes,
            ScriptDialect dialect
    ) {
        if (request.text().isBlank()) {
            return ScriptFrameResult.empty();
        }
        List<ScriptLexeme> lexemes = sourceLexemes.stream()
                .filter(token -> token.startOffset() >= 0)
                .sorted(Comparator.comparingInt(ScriptLexeme::startOffset))
                .toList();
        List<Slice> markedSlices = markedObjectSlices(request.text(), request.sourceFile(), lexemes);
        List<Slice> slices = planner(dialect).plan(request.text(), lexemes, markedSlices);
        LineIndex lines = LineIndex.of(request.text());
        List<String> localTempTables = localTempTables(lexemes);
        List<SqlStatementRecord> statements = new ArrayList<>();
        for (Slice slice : slices) {
            SqlStatementRecord statement = statement(request, lexemes, lines, slice, localTempTables);
            if (statement != null) {
                statements.add(statement);
            }
        }
        return new ScriptFrameResult(statements, List.of());
    }

    private ScriptSlicePlanner planner(ScriptDialect dialect) {
        return switch (dialect) {
            case MYSQL -> new MySqlScriptSlicePlanner();
            case POSTGRESQL -> new PostgresScriptSlicePlanner();
            case ORACLE -> new OracleScriptSlicePlanner();
            case SQLSERVER -> new SqlServerScriptSlicePlanner();
            case COMMON -> new CommonScriptSlicePlanner();
        };
    }

    private List<Slice> markedObjectSlices(String text, String sourceFile, List<ScriptLexeme> lexemes) {
        List<Slice> result = new ArrayList<>();
        ScriptLexeme opening = null;
        String explicitSource = "";
        for (ScriptLexeme token : lexemes) {
            if (token.kind() != ScriptLexemeKind.COMMENT) {
                continue;
            }
            String trimmed = token.text().trim();
            String prefix = "-- relation-detector-fixture-source:";
            if (startsWithIgnoreCase(trimmed, prefix)) {
                if (opening != null) {
                    throw new IllegalArgumentException("Nested fixture source marker at line " + token.line());
                }
                opening = token;
                explicitSource = trimmed.substring(prefix.length()).trim();
                continue;
            }
            if (equalsIgnoreCase(trimmed, "-- relation-detector-fixture-end")) {
                if (opening == null) {
                    throw new IllegalArgumentException("Fixture end without source marker at line " + token.line());
                }
                result.add(new Slice(includeFollowingNewline(text, lineEnd(text, opening.endOffset())),
                        lineStart(text, token.startOffset()), false, explicitSource));
                opening = null;
                explicitSource = "";
            }
        }
        if (opening != null) {
            throw new IllegalArgumentException("Missing relation-detector-fixture-end for "
                    + explicitSource + " in " + sourceFile);
        }
        return result;
    }
}
