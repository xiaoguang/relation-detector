package com.relationdetector.oracle.fullgrammer.common;

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
    private final OracleFullGrammerSqlBinding binding;

    OracleFullGrammerStructuredSqlParser(SqlGrammarProfile profile, OracleFullGrammerSqlBinding binding) {
        this.profile = profile;
        this.binding = binding;
    }

    @Override
    public StructuredParseResult parseSql(SqlStatementRecord statement, AdaptorContext context) {
        OracleFullGrammerParseSupport.ParsedEvents parsed = binding.parseSql(statement);
        return result(statement, parsed.events(), parsed.tokens(), parsed.syntaxErrors());
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
        attributes.put("parser", binding.parserName());
        attributes.put("lexer", binding.lexerName());
        attributes.put("eventBuilder", binding.visitorName());
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
