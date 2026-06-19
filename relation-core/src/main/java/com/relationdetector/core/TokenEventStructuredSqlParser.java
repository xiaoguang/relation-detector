package com.relationdetector.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.antlr.v4.runtime.Token;

import com.relationdetector.api.AdaptorContext;
import com.relationdetector.api.Collectors.StructuredSqlParser;
import com.relationdetector.api.SqlStatementRecord;
import com.relationdetector.api.StructuredParseResult;
import com.relationdetector.api.StructuredSqlEvent;
import com.relationdetector.api.WarningMessage;
import com.relationdetector.core.AntlrSqlParseSupport.ParsedSql;
import com.relationdetector.core.AntlrSqlParseSupport.SyntaxErrorCounter;

/**
 * Production structured SQL parser for the Token/Event pipeline.
 *
 * <p>ANTLR remains the low-level lexer/parser technology through
 * {@link AntlrSqlParseSupport}; this class owns the production Token/Event SQL
 * event builder and parser diagnostics.
 */
public class TokenEventStructuredSqlParser implements StructuredSqlParser {
    private final SqlDialect dialect;
    private final TokenEventSqlEventBuilder eventBuilder;
    private final AntlrSqlParseSupport antlrSupport;

    public TokenEventStructuredSqlParser(SqlDialect dialect) {
        this(dialect, new TokenEventSqlEventBuilder());
    }

    protected TokenEventStructuredSqlParser(SqlDialect dialect, TokenEventSqlEventBuilder eventBuilder) {
        this.dialect = dialect;
        this.eventBuilder = eventBuilder;
        this.antlrSupport = new AntlrSqlParseSupport(dialect);
    }

    @Override
    public StructuredParseResult parseSql(SqlStatementRecord statement, AdaptorContext context) {
        List<WarningMessage> warnings = new ArrayList<>();
        SyntaxErrorCounter errors = new SyntaxErrorCounter();
        ParsedSql parsed = parseAntlr(statement.sql(), errors);
        List<Token> visibleTokens = parsed.visibleTokens();
        List<StructuredSqlEvent> events = new ArrayList<>(eventBuilder.extractEvents(statement, visibleTokens));
        antlrSupport.detectDynamicSql(statement).ifPresent(warnings::add);
        warnings.forEach(warning -> {
            if (context != null) {
                context.warn(warning);
            }
        });

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("grammar", parsed.grammarName());
        attributes.put("parser", parsed.parserName());
        attributes.put("lexer", parsed.lexerName());
        attributes.put("eventBuilder", eventBuilder.name());
        attributes.put("syntaxErrors", errors.count());
        attributes.put("tokenCount", visibleTokens.size());
        attributes.put("tokenEvent", true);
        attributes.put("tokenEventPrimary", true);
        return new StructuredParseResult(
                "ANTLR_TOKEN_EVENT",
                dialect.name(),
                statement.sourceName(),
                events,
                warnings,
                attributes);
    }

    protected ParsedSql parseAntlr(String sql, SyntaxErrorCounter errors) {
        return antlrSupport.parseAntlr(sql, errors);
    }
}
