package com.relationdetector.postgres.routine;

import java.util.List;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;

import com.relationdetector.contracts.parse.SqlStatementRecord;

/**
 * CN: 从 typed routine declaration 或 statement provenance 组装 PostgreSQL routine identity；输入为限定名、
 * 输入参数类型或声明位置，输出为稳定的内部 identity。该类不读取 routine SQL 文本，也不推断参数语法。
 * EN: Assembles PostgreSQL routine identity from a typed declaration or statement provenance. Inputs are a
 * qualified name plus input parameter types or a declaration location, and the output is a stable internal
 * identity. It never reads routine SQL text or infers parameter syntax.
 */
public final class PostgresRoutineIdentity {
    private PostgresRoutineIdentity() {
    }

    public static String signature(String qualifiedName, List<String> inputTypes) {
        String name = normalized(qualifiedName);
        List<String> types = (inputTypes == null ? List.<String>of() : inputTypes).stream()
                .map(PostgresRoutineIdentity::normalized)
                .filter(value -> !value.isBlank())
                .toList();
        return name + "(" + String.join(", ", types) + ")";
    }

    /**
     * CN: 从已经由 grammar 分类为参数类型的 context 重建规范文本；只在相邻词法单元之间补空格，不解释类型名称。
     * EN: Reconstructs canonical text from a context already classified as an argument type, adding spaces only
     * between adjacent lexical words without interpreting type names.
     */
    public static String typedText(List<Token> visibleTokens, ParserRuleContext context) {
        if (context == null || context.getStart() == null || context.getStop() == null) {
            return "";
        }
        int start = context.getStart().getTokenIndex();
        int stop = context.getStop().getTokenIndex();
        StringBuilder result = new StringBuilder();
        for (Token token : visibleTokens == null ? List.<Token>of() : visibleTokens) {
            if (token.getTokenIndex() < start || token.getTokenIndex() > stop) {
                continue;
            }
            String text = token.getText();
            if (text == null || text.isBlank()) {
                continue;
            }
            if (!result.isEmpty() && needsWordSeparator(result.charAt(result.length() - 1), text.charAt(0))) {
                result.append(' ');
            }
            result.append(text);
        }
        return result.toString();
    }

    public static String statementScoped(String objectType, String qualifiedName, SqlStatementRecord statement) {
        String sourceFile = attribute(statement, "sourceFile");
        String declaration = sourceFile.isBlank() ? statement.sourceName() : sourceFile;
        return normalized(objectType) + ":" + normalized(qualifiedName) + "@"
                + normalized(declaration) + ":" + statement.startLine() + "-" + statement.endLine();
    }

    public static String preserveCollectedIdentity(SqlStatementRecord statement, String parsedIdentity) {
        String definitionSource = attribute(statement, "objectDefinitionSource");
        String collectedIdentity = attribute(statement, "sourceObjectIdentity");
        return definitionSource.isBlank() || collectedIdentity.isBlank()
                ? normalized(parsedIdentity)
                : collectedIdentity;
    }

    private static String attribute(SqlStatementRecord statement, String key) {
        Object value = statement.attributes().get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private static String normalized(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean needsWordSeparator(char left, char right) {
        return isWordEdge(left) && isWordEdge(right);
    }

    private static boolean isWordEdge(char value) {
        return Character.isLetterOrDigit(value) || value == '_' || value == '$' || value == '"';
    }
}
