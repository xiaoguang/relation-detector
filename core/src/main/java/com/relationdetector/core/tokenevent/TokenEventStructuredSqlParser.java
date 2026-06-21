package com.relationdetector.core.tokenevent;

import com.relationdetector.core.parse.AntlrSqlParseSupport;
import com.relationdetector.core.parse.SqlDialect;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.antlr.v4.runtime.Token;

import com.relationdetector.contracts.spi.AdaptorContext;
import com.relationdetector.contracts.spi.Collectors.StructuredSqlParser;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredParseResult;
import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.core.parse.AntlrSqlParseSupport.ParsedSql;
import com.relationdetector.core.parse.AntlrSqlParseSupport.SyntaxErrorCounter;

/**
 * Production structured SQL parser for the token-event pipeline.
 *
 * <p>ANTLR remains the low-level lexer/parser technology through
 * {@link AntlrSqlParseSupport}; this class owns the production token-event SQL
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
