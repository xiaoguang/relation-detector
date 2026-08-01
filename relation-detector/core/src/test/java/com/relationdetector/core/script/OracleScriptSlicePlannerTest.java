package com.relationdetector.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.parse.ScriptFrameRequest;

final class OracleScriptSlicePlannerTest {
    @Test
    void splitsConsecutiveViewsAsOrdinarySemicolonTerminatedStatements() {
        String script = """
                CREATE OR REPLACE VIEW v_one AS SELECT 1;
                CREATE OR REPLACE VIEW v_two AS SELECT 2;
                """;

        var result = new StructuredScriptFramer().frame(
                new ScriptFrameRequest(script, "views.sql", StatementSourceType.DDL_FILE),
                lexemes(script),
                ScriptDialect.ORACLE);

        assertEquals(2, result.statements().size());
        assertEquals("v_one", result.statements().get(0).attributes().get("sourceObjectName"));
        assertEquals("v_two", result.statements().get(1).attributes().get("sourceObjectName"));
        assertEquals(StatementSourceType.VIEW, result.statements().get(0).sourceType());
        assertEquals(StatementSourceType.VIEW, result.statements().get(1).sourceType());
    }

    private List<ScriptLexeme> lexemes(String script) {
        List<ScriptLexeme> result = new ArrayList<>();
        int cursor = 0;
        for (String token : List.of(
                "CREATE", "OR", "REPLACE", "VIEW", "v_one", "AS", "SELECT", "1", ";",
                "CREATE", "OR", "REPLACE", "VIEW", "v_two", "AS", "SELECT", "2", ";")) {
            int offset = script.indexOf(token, cursor);
            ScriptLexemeKind kind = switch (token) {
                case "CREATE" -> ScriptLexemeKind.CREATE;
                case "OR" -> ScriptLexemeKind.OR;
                case "REPLACE" -> ScriptLexemeKind.REPLACE;
                case "VIEW" -> ScriptLexemeKind.VIEW;
                case ";" -> ScriptLexemeKind.SEMICOLON;
                default -> ScriptLexemeKind.WORD;
            };
            result.add(new ScriptLexeme(kind, token, offset, offset + token.length(), 1, offset));
            cursor = offset + token.length();
        }
        return result;
    }
}
