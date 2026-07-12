package com.relationdetector.sqlserver.fullgrammar.common;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.antlr.v4.runtime.Token;

import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredParseResult;
import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.contracts.spi.AdaptorContext;
import com.relationdetector.contracts.spi.Collectors.StructuredSqlParser;
import com.relationdetector.core.fullgrammar.SqlGrammarProfile;

public final class SqlServerFullGrammarStructuredSqlParser implements StructuredSqlParser {
    private final SqlGrammarProfile profile;
    private final SqlServerFullGrammarSqlBinding binding;

    public SqlServerFullGrammarStructuredSqlParser(
            SqlGrammarProfile profile,
            SqlServerFullGrammarSqlBinding binding
    ) {
        this.profile = profile;
        this.binding = binding;
    }

    @Override
    public StructuredParseResult parseSql(SqlStatementRecord statement, AdaptorContext context) {
        SqlServerFullGrammarSqlBinding.ParsedTree parsed = binding.parse(statement);
        List<StructuredSqlEvent> events = parsed.syntaxErrors() == 0
                ? new SqlServerParseTreeEventCollector(
                        statement, false, binding.parseTreeAdapter()).collect(parsed.root())
                : List.of();
        return result(statement, events, parsed.tokens(), parsed.syntaxErrors());
    }

    private StructuredParseResult result(
            SqlStatementRecord statement,
            List<StructuredSqlEvent> events,
            List<Token> tokens,
            int syntaxErrors
    ) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("grammarProfile", profile.id());
        attributes.put("fullGrammarProfile", profile.id());
        attributes.put("sqlServerVersionProfile", profile.id());
        attributes.put("grammarCoverage", "GRAMMARS_V4_TSQL_VERSIONED");
        attributes.put("parser", binding.parserName());
        attributes.put("lexer", binding.lexerName());
        attributes.put("eventBuilder", binding.visitorName());
        attributes.put("syntaxErrors", syntaxErrors);
        attributes.put("tokenCount", tokens.stream()
                .filter(token -> token.getType() != Token.EOF)
                .filter(token -> token.getChannel() == Token.DEFAULT_CHANNEL)
                .count());
        attributes.put("fullGrammarNativeEventTypes", "sqlserver-generated-parser");
        attributes.put("fullGrammarDelegatedEventTypes", "");
        return new StructuredParseResult("SQLSERVER_FULL_GRAMMAR_PARSE_TREE",
                "SQLSERVER",
                statement.sourceName(),
                events,
                new ArrayList<>(),
                attributes);
    }
}
