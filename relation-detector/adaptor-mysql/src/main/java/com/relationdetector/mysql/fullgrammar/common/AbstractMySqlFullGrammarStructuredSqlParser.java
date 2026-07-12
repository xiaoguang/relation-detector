package com.relationdetector.mysql.fullgrammar.common;

import com.relationdetector.contracts.Enums.WarningType;
import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredParseResult;
import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.contracts.spi.AdaptorContext;
import com.relationdetector.contracts.spi.Collectors.StructuredSqlParser;
import com.relationdetector.core.fullgrammar.FullGrammarEventMerger;
import com.relationdetector.core.fullgrammar.FullGrammarNativeEventTypes;
import com.relationdetector.mysql.fullgrammar.common.MySqlFullGrammarParseSupport.FullGrammarSqlParse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared MySQL full-grammar SQL parser lifecycle.
 */
public abstract class AbstractMySqlFullGrammarStructuredSqlParser implements StructuredSqlParser {
    private final MySqlFullGrammarSqlBinding binding;

    protected AbstractMySqlFullGrammarStructuredSqlParser(MySqlFullGrammarSqlBinding binding) {
        this.binding = binding;
    }

    @Override
    public final StructuredParseResult parseSql(SqlStatementRecord statement, AdaptorContext context) {
        FullGrammarSqlParse parse = binding.parseSql(statement.sql());
        List<StructuredSqlEvent> nativeEvents = new ArrayList<>();
        List<WarningMessage> warnings = new ArrayList<>();
        if (parse.root() != null) {
            try {
                nativeEvents.addAll(binding.extractEvents(statement, parse.visibleTokens(), parse.root()));
            } catch (RuntimeException ex) {
                warnings.add(fullGrammarWarning(statement, "full-grammar SQL visitor failed: " + ex.getMessage(),
                        parse.syntaxErrors()));
            }
        }
        if (parse.syntaxErrors() > 0) {
            warnings.add(fullGrammarWarning(statement,
                    "full-grammar SQL parser reported " + parse.syntaxErrors() + " syntax error(s)",
                    parse.syntaxErrors()));
        }
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("fullGrammarLexer", binding.lexerName());
        attributes.put("fullGrammarParser", binding.parserName());
        attributes.put("fullGrammarEntryRule", "queries");
        attributes.put("fullGrammarSyntaxErrors", parse.syntaxErrors());
        attributes.put("fullGrammarParseTreeRoot", parse.root() == null ? "" : parse.root().getClass().getSimpleName());
        attributes.put("fullGrammarNativeEventTypes",
                FullGrammarEventMerger.eventTypeNames(FullGrammarNativeEventTypes.MYSQL_NATIVE_EVENTS));
        return new StructuredParseResult("MYSQL_FULL_GRAMMAR_PARSE_TREE", "MYSQL", statement.sourceName(),
                nativeEvents, warnings, attributes);
    }

    private WarningMessage fullGrammarWarning(SqlStatementRecord statement, String message, int syntaxErrors) {
        return WarningMessage.warn(WarningType.PARSE_WARNING,
                "FULL_GRAMMAR_SQL_PARSE_WARNING",
                message,
                statement.sourceName(),
                statement.startLine(),
                Map.of("fullGrammarSyntaxErrors", syntaxErrors,
                        "rawStatement", statement.sql()));
    }
}
