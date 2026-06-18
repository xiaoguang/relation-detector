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
