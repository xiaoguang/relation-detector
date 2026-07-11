package com.relationdetector.sqlserver.fullgrammer.common;

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
import com.relationdetector.core.fullgrammer.SqlGrammarProfile;

public final class SqlServerFullGrammerStructuredSqlParser implements StructuredSqlParser {
    private final SqlGrammarProfile profile;
    private final SqlServerFullGrammerSqlBinding binding;

    public SqlServerFullGrammerStructuredSqlParser(
            SqlGrammarProfile profile,
            SqlServerFullGrammerSqlBinding binding
    ) {
        this.profile = profile;
        this.binding = binding;
    }

    @Override
    public StructuredParseResult parseSql(SqlStatementRecord statement, AdaptorContext context) {
        SqlServerFullGrammerSqlBinding.ParsedTree parsed = binding.parse(statement);
        List<StructuredSqlEvent> events = parsed.syntaxErrors() == 0
                ? new SqlServerParseTreeEventCollector(
                        parsed.parser(), statement, false, binding.parseTreeAdapter()).collect(parsed.root())
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
        attributes.put("fullGrammerProfile", profile.id());
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
        attributes.put("fullGrammerNativeEventTypes", "sqlserver-generated-parser");
        attributes.put("fullGrammerDelegatedEventTypes", "");
        return new StructuredParseResult("SQLSERVER_FULL_GRAMMER_PARSE_TREE",
                "SQLSERVER",
                statement.sourceName(),
                events,
                new ArrayList<>(),
                attributes);
    }
}
