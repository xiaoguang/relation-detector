package com.relationdetector.oracle.fullgrammer.common;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;

import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredParseResult;
import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.contracts.spi.AdaptorContext;
import com.relationdetector.contracts.spi.Collectors.StructuredSqlParser;
import com.relationdetector.core.fullgrammer.SqlGrammarProfile;
import com.relationdetector.core.parse.AntlrSqlParseSupport.SyntaxErrorCounter;

/**
 * Oracle versioned full-grammer SQL parser backed by generated parser classes.
 *
 * <p>CN: 这是 Oracle full-grammer 的真实 generated-parser 入口。每个 profile
 * 都使用自己的 lexer/parser grammar 和 parse-tree visitor；它不委托 Oracle token-event
 * parser，也不混入 token-event 事件。
 *
 * <p>EN: Oracle versioned full-grammer SQL parser backed by generated parser
 * classes. Each profile uses its own lexer/parser grammar and parse-tree
 * visitor; this path does not delegate to Oracle token-event or merge
 * token-event events.
 */
public final class OracleFullGrammerStructuredSqlParser implements StructuredSqlParser {
    private final SqlGrammarProfile profile;

    OracleFullGrammerStructuredSqlParser(SqlGrammarProfile profile) {
        this.profile = profile;
    }

    @Override
    public StructuredParseResult parseSql(SqlStatementRecord statement, AdaptorContext context) {
        return switch (profile.id()) {
            case "oracle-12c" -> parse12c(statement);
            case "oracle-19c" -> parse19c(statement);
            case "oracle-21c" -> parse21c(statement);
            case "oracle-26ai" -> parse26ai(statement);
            default -> throw new IllegalStateException("Unsupported Oracle full-grammer profile: " + profile.id());
        };
    }

    private StructuredParseResult parse12c(SqlStatementRecord statement) {
        SyntaxErrorCounter errors = new SyntaxErrorCounter();
        var lexer = new com.relationdetector.oracle.fullgrammer.v12c.OracleFullGrammerLexer(
                CharStreams.fromString(statement.sql()));
        var tokens = tokens(lexer, errors);
        var parser = new com.relationdetector.oracle.fullgrammer.v12c.OracleFullGrammerParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(errors);
        var root = parser.script();
        List<StructuredSqlEvent> events = errors.count() == 0
                ? new com.relationdetector.oracle.fullgrammer.v12c.OracleFullGrammerParseTreeVisitor(statement)
                        .collect(root)
                : List.of();
        return result(statement, events, List.copyOf(tokens.getTokens()), errors.count());
    }

    private StructuredParseResult parse19c(SqlStatementRecord statement) {
        SyntaxErrorCounter errors = new SyntaxErrorCounter();
        var lexer = new com.relationdetector.oracle.fullgrammer.v19c.OracleFullGrammerLexer(
                CharStreams.fromString(statement.sql()));
        var tokens = tokens(lexer, errors);
        var parser = new com.relationdetector.oracle.fullgrammer.v19c.OracleFullGrammerParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(errors);
        var root = parser.script();
        List<StructuredSqlEvent> events = errors.count() == 0
                ? new com.relationdetector.oracle.fullgrammer.v19c.OracleFullGrammerParseTreeVisitor(statement)
                        .collect(root)
                : List.of();
        return result(statement, events, List.copyOf(tokens.getTokens()), errors.count());
    }

    private StructuredParseResult parse21c(SqlStatementRecord statement) {
        SyntaxErrorCounter errors = new SyntaxErrorCounter();
        var lexer = new com.relationdetector.oracle.fullgrammer.v21c.OracleFullGrammerLexer(
                CharStreams.fromString(statement.sql()));
        var tokens = tokens(lexer, errors);
        var parser = new com.relationdetector.oracle.fullgrammer.v21c.OracleFullGrammerParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(errors);
        var root = parser.script();
        List<StructuredSqlEvent> events = errors.count() == 0
                ? new com.relationdetector.oracle.fullgrammer.v21c.OracleFullGrammerParseTreeVisitor(statement)
                        .collect(root)
                : List.of();
        return result(statement, events, List.copyOf(tokens.getTokens()), errors.count());
    }

    private StructuredParseResult parse26ai(SqlStatementRecord statement) {
        SyntaxErrorCounter errors = new SyntaxErrorCounter();
        var lexer = new com.relationdetector.oracle.fullgrammer.v26ai.OracleFullGrammerLexer(
                CharStreams.fromString(statement.sql()));
        var tokens = tokens(lexer, errors);
        var parser = new com.relationdetector.oracle.fullgrammer.v26ai.OracleFullGrammerParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(errors);
        var root = parser.script();
        List<StructuredSqlEvent> events = errors.count() == 0
                ? new com.relationdetector.oracle.fullgrammer.v26ai.OracleFullGrammerParseTreeVisitor(statement)
                        .collect(root)
                : List.of();
        return result(statement, events, List.copyOf(tokens.getTokens()), errors.count());
    }

    private static CommonTokenStream tokens(org.antlr.v4.runtime.Lexer lexer, SyntaxErrorCounter errors) {
        lexer.removeErrorListeners();
        lexer.addErrorListener(errors);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        tokens.fill();
        return tokens;
    }

    private StructuredParseResult result(
            SqlStatementRecord statement,
            List<StructuredSqlEvent> events,
            List<Token> tokens,
            int syntaxErrors
    ) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("grammarProfile", profile.id());
        attributes.put("fullGrammerProfile", profile.id());
        attributes.put("oracleVersionProfile", profile.id());
        attributes.put("grammarCoverage", "INCOMPLETE_VERSIONED");
        attributes.put("parser", "OracleFullGrammerParser");
        attributes.put("lexer", "OracleFullGrammerLexer");
        attributes.put("eventBuilder", "OracleFullGrammerParseTreeVisitor");
        attributes.put("syntaxErrors", syntaxErrors);
        attributes.put("tokenCount", tokens.stream()
                .filter(token -> token.getType() != Token.EOF)
                .filter(token -> token.getChannel() == Token.DEFAULT_CHANNEL)
                .count());
        attributes.put("fullGrammerNativeEventTypes", "oracle-scoped-generated-parser");
        attributes.put("fullGrammerDelegatedEventTypes", "");
        return new StructuredParseResult("ORACLE_FULL_GRAMMER_PARSE_TREE",
                "ORACLE",
                statement.sourceName(),
                events,
                new ArrayList<>(),
                attributes);
    }
}
