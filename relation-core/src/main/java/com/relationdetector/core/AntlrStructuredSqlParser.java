package com.relationdetector.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;

import com.relationdetector.api.AdaptorContext;
import com.relationdetector.api.Collectors.StructuredSqlParser;
import com.relationdetector.api.SqlStatementRecord;
import com.relationdetector.api.StructuredParseResult;
import com.relationdetector.api.StructuredSqlEvent;
import com.relationdetector.api.WarningMessage;
import com.relationdetector.api.Enums.StructuredParseEventType;
import com.relationdetector.api.Enums.WarningType;
import com.relationdetector.core.antlr.RelationSqlLexer;
import com.relationdetector.core.antlr.RelationSqlParser;

/**
 * ANTLR-backed structural SQL parser used during migration from hand parsing.
 *
 * <p>Call flow:
 *
 * <pre>{@code
 * adaptor.sqlRelationParser()
 *   -> ShadowSqlRelationParser
 *      -> AntlrStructuredSqlParser.parseSql(...)
 *      -> RelationExtractionVisitor.extract(...)
 * }</pre>
 *
 * <p>The ANTLR grammar is intentionally broad and forgiving. It produces a real
 * token stream and parse tree for diagnostics, while this class extracts the
 * first relationship-relevant event types: table references, simple aliased
 * column equalities, and unresolved dynamic SQL. Keeping this layer small makes
 * it possible to replace the tolerant grammar with full MySQL/PostgreSQL
 * grammars later without changing the rest of the system.
 */
public class AntlrStructuredSqlParser implements StructuredSqlParser {
    private final SqlDialect dialect;
    private final StructuredSqlEventVisitor eventVisitor;

    public AntlrStructuredSqlParser(SqlDialect dialect) {
        this(dialect, new StructuredSqlEventVisitor("RelationSqlStructuredSqlEventVisitor",
                Set.of(RelationSqlLexer.IDENTIFIER, RelationSqlLexer.QUOTED_IDENTIFIER)));
    }

    protected AntlrStructuredSqlParser(SqlDialect dialect, StructuredSqlEventVisitor eventVisitor) {
        this.dialect = dialect;
        this.eventVisitor = eventVisitor;
    }

    @Override
    public StructuredParseResult parseSql(SqlStatementRecord statement, AdaptorContext context) {
        List<WarningMessage> warnings = new ArrayList<>();
        SyntaxErrorCounter errors = new SyntaxErrorCounter();
        ParsedSql parsed = parseAntlr(statement.sql(), errors);
        List<Token> visibleTokens = parsed.visibleTokens();
        List<StructuredSqlEvent> events = new ArrayList<>(eventVisitor.extractEvents(statement, visibleTokens));
        detectDynamicSql(statement).ifPresent(warnings::add);
        warnings.forEach(warning -> {
            if (context != null) {
                context.warn(warning);
            }
        });

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("grammar", parsed.grammarName());
        attributes.put("parser", parsed.parserName());
        attributes.put("lexer", parsed.lexerName());
        attributes.put("eventVisitor", eventVisitor.name());
        attributes.put("syntaxErrors", errors.count);
        attributes.put("tokenCount", visibleTokens.size());
        return new StructuredParseResult("ANTLR", dialect.name(), statement.sourceName(),
                events, warnings, attributes);
    }

    /**
     * Runs the fallback tolerant grammar.
     *
     * <p>Dialect subclasses override this method to use their generated parser
     * while preserving the same structured event contract and warning behavior.
     * Keeping the override this small is the Phase-1 migration point: it proves
     * grammar selection is no longer hard-coded to one universal parser.
     */
    protected ParsedSql parseAntlr(String sql, SyntaxErrorCounter errors) {
        RelationSqlLexer lexer = new RelationSqlLexer(CharStreams.fromString(sql));
        lexer.removeErrorListeners();
        lexer.addErrorListener(errors);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        RelationSqlParser parser = new RelationSqlParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(errors);

        /*
         * Parse the whole input before inspecting tokens. Even though the first
         * grammar is permissive, this call is important: it proves the ANTLR
         * pipeline is active, collects syntax diagnostics, and gives future
         * visitors a stable parse-tree entry point.
         */
        parser.script();
        tokens.fill();

        List<Token> visibleTokens = tokens.getTokens().stream()
                .filter(token -> token.getType() != Token.EOF)
                .filter(token -> token.getChannel() == Token.DEFAULT_CHANNEL)
                .toList();
        return new ParsedSql("RelationSql",
                RelationSqlLexer.class.getSimpleName(),
                RelationSqlParser.class.getSimpleName(),
                visibleTokens);
    }

    /**
     * Extracts table references from tokens following FROM/JOIN/UPDATE/INTO.
     *
     * <p>Complete SQL examples handled here:
     *
     * <pre>{@code
     * SELECT * FROM orders o JOIN users u ON o.user_id = u.id
     * SELECT * FROM "public"."orders" AS o JOIN "public"."users" u ON o.user_id = u.id
     * UPDATE orders o SET status = 'X' FROM users u WHERE o.user_id = u.id
     * INSERT INTO order_archive SELECT * FROM orders o JOIN users u ON o.user_id = u.id
     * }</pre>
     */
    private List<StructuredSqlEvent> extractTableReferences(SqlStatementRecord statement, List<Token> tokens) {
        List<StructuredSqlEvent> events = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i++) {
            String lower = lower(tokens.get(i));
            if (!lower.equals("from") && !lower.equals("join") && !lower.equals("update") && !lower.equals("into")) {
                continue;
            }
            IdentifierRead table = readQualifiedIdentifier(tokens, i + 1);
            if (table == null) {
                continue;
            }
            int aliasIndex = table.nextIndex;
            if (aliasIndex < tokens.size() && lower(tokens.get(aliasIndex)).equals("as")) {
                aliasIndex++;
            }
            String alias = null;
            if (aliasIndex < tokens.size() && isIdentifier(tokens.get(aliasIndex)) && !isKeyword(lower(tokens.get(aliasIndex)))) {
                alias = cleanIdentifier(tokens.get(aliasIndex).getText());
            }
            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put("keyword", lower.toUpperCase(Locale.ROOT));
            attributes.put("qualifiedTable", table.qualifiedName);
            attributes.put("table", baseName(table.qualifiedName));
            if (alias != null) {
                attributes.put("alias", alias);
            }
            events.add(new StructuredSqlEvent(StructuredParseEventType.TABLE_REFERENCE,
                    statement.sourceName(), line(statement, tokens.get(i)), attributes));
        }
        return events;
    }

    /**
     * Extracts simple equality predicates between two qualified column names.
     *
     * <p>This intentionally mirrors the existing lightweight parser's first
     * useful predicate shape:
     *
     * <pre>{@code
     * SELECT * FROM orders o JOIN users u ON o.user_id = u.id
     * SELECT * FROM `orders` o JOIN `users` u ON o.`user_id` = u.`id`
     * }</pre>
     */
    private List<StructuredSqlEvent> extractColumnEqualities(SqlStatementRecord statement, List<Token> tokens) {
        List<StructuredSqlEvent> events = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i++) {
            if (!tokens.get(i).getText().equals("=")) {
                continue;
            }
            ColumnRead left = readColumnBackwards(tokens, i - 1);
            ColumnRead right = readColumnForward(tokens, i + 1);
            if (left == null || right == null) {
                continue;
            }
            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put("leftAlias", left.qualifier);
            attributes.put("leftColumn", left.column);
            attributes.put("rightAlias", right.qualifier);
            attributes.put("rightColumn", right.column);
            events.add(new StructuredSqlEvent(StructuredParseEventType.COLUMN_EQUALITY,
                    statement.sourceName(), line(statement, tokens.get(i)), attributes));
        }
        return events;
    }

    /**
     * Detects dynamic SQL that cannot be safely resolved by static parsing.
     *
     * <p>Examples:
     *
     * <pre>{@code
     * PREPARE stmt FROM @sql;
     * EXECUTE stmt;
     * EXECUTE IMMEDIATE 'SELECT * FROM orders o JOIN users u ON o.user_id = u.id';
     * }</pre>
     */
    private java.util.Optional<WarningMessage> detectDynamicSql(SqlStatementRecord statement) {
        String lower = statement.sql().toLowerCase(Locale.ROOT);
        if (!lower.matches("(?s).*\\b(prepare|execute\\s+immediate|execute)\\b.*")) {
            return java.util.Optional.empty();
        }
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("rawStatement", statement.sql());
        attributes.put("statementSourceType", statement.sourceType().name());
        attributes.put("dialect", dialect.name());
        attributes.putAll(statement.attributes());
        return java.util.Optional.of(WarningMessage.warn(WarningType.PARSE_WARNING,
                "DYNAMIC_SQL_UNRESOLVED",
                "dynamic SQL is present but cannot be statically resolved",
                statement.sourceName(),
                statement.startLine(),
                attributes));
    }

    private IdentifierRead readQualifiedIdentifier(List<Token> tokens, int index) {
        if (index >= tokens.size() || !isIdentifier(tokens.get(index))) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        int cursor = index;
        parts.add(cleanIdentifier(tokens.get(cursor++).getText()));
        while (cursor + 1 < tokens.size() && tokens.get(cursor).getText().equals(".") && isIdentifier(tokens.get(cursor + 1))) {
            cursor++;
            parts.add(cleanIdentifier(tokens.get(cursor++).getText()));
        }
        return new IdentifierRead(String.join(".", parts), cursor);
    }

    private ColumnRead readColumnForward(List<Token> tokens, int index) {
        if (index + 2 >= tokens.size() || !isIdentifier(tokens.get(index)) || !tokens.get(index + 1).getText().equals(".")
                || !isIdentifier(tokens.get(index + 2))) {
            return null;
        }
        return new ColumnRead(cleanIdentifier(tokens.get(index).getText()), cleanIdentifier(tokens.get(index + 2).getText()));
    }

    private ColumnRead readColumnBackwards(List<Token> tokens, int index) {
        if (index - 2 < 0 || !isIdentifier(tokens.get(index)) || !tokens.get(index - 1).getText().equals(".")
                || !isIdentifier(tokens.get(index - 2))) {
            return null;
        }
        return new ColumnRead(cleanIdentifier(tokens.get(index - 2).getText()), cleanIdentifier(tokens.get(index).getText()));
    }

    private boolean isIdentifier(Token token) {
        int type = token.getType();
        return type == RelationSqlLexer.IDENTIFIER || type == RelationSqlLexer.QUOTED_IDENTIFIER;
    }

    private boolean isKeyword(String lower) {
        return switch (lower) {
            case "on", "where", "join", "left", "right", "inner", "outer", "full", "cross", "using", "natural",
                    "group", "order", "having", "limit", "union", "set", "values", "select", "from" -> true;
            default -> false;
        };
    }

    private String cleanIdentifier(String value) {
        if ((value.startsWith("`") && value.endsWith("`")) || (value.startsWith("\"") && value.endsWith("\""))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private String baseName(String qualifiedName) {
        int dot = qualifiedName.lastIndexOf('.');
        return dot >= 0 ? qualifiedName.substring(dot + 1) : qualifiedName;
    }

    private String lower(Token token) {
        return cleanIdentifier(token.getText()).toLowerCase(Locale.ROOT);
    }

    private long line(SqlStatementRecord statement, Token token) {
        return statement.startLine() + Math.max(0, token.getLine() - 1);
    }

    private record IdentifierRead(String qualifiedName, int nextIndex) {
    }

    private record ColumnRead(String qualifier, String column) {
    }

    public record ParsedSql(
            String grammarName,
            String lexerName,
            String parserName,
            List<Token> visibleTokens
    ) {
    }

    protected static final class SyntaxErrorCounter extends BaseErrorListener {
        private int count;

        @Override
        public void syntaxError(
                Recognizer<?, ?> recognizer,
                Object offendingSymbol,
                int line,
                int charPositionInLine,
                String msg,
                RecognitionException e
        ) {
            count++;
        }
    }
}
