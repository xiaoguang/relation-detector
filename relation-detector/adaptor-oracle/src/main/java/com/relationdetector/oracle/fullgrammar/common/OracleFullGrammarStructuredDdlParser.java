package com.relationdetector.oracle.fullgrammar.common;

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
import com.relationdetector.core.fullgrammar.SqlGrammarProfile;

/**
 * Oracle versioned full-grammar DDL parser backed by generated parser classes.
 *
 * <p>CN: 每个 Oracle version profile 都使用自己的 generated lexer/parser 和
 * typed visitor 收集 DDL events；该链路不委托 token-event DDL parser。
 *
 * <p>EN: Oracle versioned full-grammar DDL parser backed by generated parser
 * classes. Each profile uses its own generated lexer/parser and typed visitor
 * to collect DDL events; this path does not delegate to token-event DDL.
 */
public final class OracleFullGrammarStructuredDdlParser implements StructuredDdlParser {
    private final SqlGrammarProfile profile;
    private final OracleFullGrammarDdlBinding ddlBinding;
    private final OracleFullGrammarSqlBinding sqlBinding;

    OracleFullGrammarStructuredDdlParser(
            SqlGrammarProfile profile,
            OracleFullGrammarDdlBinding ddlBinding,
            OracleFullGrammarSqlBinding sqlBinding
    ) {
        this.profile = profile;
        this.ddlBinding = ddlBinding;
        this.sqlBinding = sqlBinding;
    }

    @Override
    public StructuredParseResult parseDdl(String ddl, String sourceName, AdaptorContext context) {
        SqlStatementRecord statement = new SqlStatementRecord(
                ddl,
                StatementSourceType.DDL_FILE,
                sourceName,
                1,
                1,
                Map.of());
        OracleFullGrammarParseSupport.ParsedEvents parsed = ddlBinding.parseDdl(statement);
        return result(statement,
                OracleDdlEventVisitorCore.ddlEvents(parsed.events()),
                parsed.tokens(),
                parsed.syntaxErrors());
    }

    private StructuredParseResult result(
            SqlStatementRecord statement,
            List<StructuredSqlEvent> events,
            List<Token> tokens,
            int syntaxErrors
    ) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("ddlMode", true);
        attributes.put("grammarProfile", profile.id());
        attributes.put("fullGrammarProfile", profile.id());
        attributes.put("oracleVersionProfile", profile.id());
        attributes.put("grammarCoverage", "INCOMPLETE_VERSIONED");
        attributes.put("parser", sqlBinding.parserName());
        attributes.put("lexer", sqlBinding.lexerName());
        attributes.put("eventBuilder", sqlBinding.visitorName());
        attributes.put("backend", "ORACLE_FULL_GRAMMAR_DDL_PARSE_TREE");
        attributes.put("syntaxErrors", syntaxErrors);
        attributes.put("tokenCount", tokens.stream()
                .filter(token -> token.getType() != Token.EOF)
                .filter(token -> token.getChannel() == Token.DEFAULT_CHANNEL)
                .count());
        return new StructuredParseResult("ORACLE_FULL_GRAMMAR_DDL_PARSE_TREE",
                "ORACLE",
                statement.sourceName(),
                events,
                new ArrayList<>(),
                attributes);
    }
}
