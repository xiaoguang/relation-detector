package com.relationdetector.sqlserver.fullgrammer.common;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.antlr.v4.runtime.Token;

import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredParseResult;
import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.contracts.spi.AdaptorContext;
import com.relationdetector.contracts.spi.Collectors.StructuredDdlParser;
import com.relationdetector.core.fullgrammer.SqlGrammarProfile;

public final class SqlServerFullGrammerStructuredDdlParser implements StructuredDdlParser {
    private final SqlGrammarProfile profile;
    private final SqlServerFullGrammerSqlBinding binding;

    public SqlServerFullGrammerStructuredDdlParser(
            SqlGrammarProfile profile,
            SqlServerFullGrammerSqlBinding binding
    ) {
        this.profile = profile;
        this.binding = binding;
    }

    @Override
    public StructuredParseResult parseDdl(String ddl, String sourceName, AdaptorContext context) {
        SqlStatementRecord statement = new SqlStatementRecord(ddl, StatementSourceType.DDL_FILE, sourceName, 1, 1, Map.of());
        SqlServerFullGrammerSqlBinding.ParsedTree parsed = binding.parse(statement);
        List<StructuredSqlEvent> events = parsed.syntaxErrors() == 0
                ? new SqlServerParseTreeEventCollector(parsed.parser(), statement, true).collect(parsed.root())
                : List.of();
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("grammarProfile", profile.id());
        attributes.put("fullGrammerProfile", profile.id());
        attributes.put("sqlServerVersionProfile", profile.id());
        attributes.put("grammarCoverage", "GRAMMARS_V4_TSQL_VERSIONED");
        attributes.put("ddlMode", true);
        attributes.put("parser", binding.parserName());
        attributes.put("lexer", binding.lexerName());
        attributes.put("eventBuilder", binding.visitorName());
        attributes.put("syntaxErrors", parsed.syntaxErrors());
        attributes.put("tokenCount", parsed.tokens().stream()
                .filter(token -> token.getType() != Token.EOF)
                .filter(token -> token.getChannel() == Token.DEFAULT_CHANNEL)
                .count());
        return new StructuredParseResult("SQLSERVER_FULL_GRAMMER_DDL",
                "SQLSERVER",
                sourceName,
                events,
                new ArrayList<>(),
                attributes);
    }
}
