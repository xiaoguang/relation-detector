package com.relationdetector.mysql.fullgrammer.common;

import com.relationdetector.contracts.Enums.WarningType;
import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredParseResult;
import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.contracts.spi.AdaptorContext;
import com.relationdetector.contracts.spi.Collectors.StructuredSqlParser;
import com.relationdetector.core.fullgrammer.FullGrammerEventMerger;
import com.relationdetector.core.fullgrammer.FullGrammerNativeEventTypes;
import com.relationdetector.mysql.fullgrammer.common.MySqlFullGrammerParseSupport.FullGrammerSqlParse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared MySQL full-grammer SQL parser lifecycle.
 */
public abstract class AbstractMySqlFullGrammerStructuredSqlParser implements StructuredSqlParser {
    private final MySqlFullGrammerSqlBinding binding;

    protected AbstractMySqlFullGrammerStructuredSqlParser(MySqlFullGrammerSqlBinding binding) {
        this.binding = binding;
    }

    @Override
    public final StructuredParseResult parseSql(SqlStatementRecord statement, AdaptorContext context) {
        FullGrammerSqlParse parse = binding.parseSql(statement.sql());
        List<StructuredSqlEvent> nativeEvents = new ArrayList<>();
        List<WarningMessage> warnings = new ArrayList<>();
        if (parse.root() != null) {
            try {
                nativeEvents.addAll(binding.extractEvents(statement, parse.visibleTokens(), parse.root()));
            } catch (RuntimeException ex) {
                warnings.add(fullGrammerWarning(statement, "full-grammer SQL visitor failed: " + ex.getMessage(),
                        parse.syntaxErrors()));
            }
        }
        if (parse.syntaxErrors() > 0) {
            warnings.add(fullGrammerWarning(statement,
                    "full-grammer SQL parser reported " + parse.syntaxErrors() + " syntax error(s)",
                    parse.syntaxErrors()));
        }
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("fullGrammerLexer", binding.lexerName());
        attributes.put("fullGrammerParser", binding.parserName());
        attributes.put("fullGrammerEntryRule", "queries");
        attributes.put("fullGrammerSyntaxErrors", parse.syntaxErrors());
        attributes.put("fullGrammerParseTreeRoot", parse.root() == null ? "" : parse.root().getClass().getSimpleName());
        attributes.put("fullGrammerNativeEventTypes",
                FullGrammerEventMerger.eventTypeNames(FullGrammerNativeEventTypes.MYSQL_NATIVE_EVENTS));
        return new StructuredParseResult("MYSQL_FULL_GRAMMER_PARSE_TREE", "MYSQL", statement.sourceName(),
                nativeEvents, warnings, attributes);
    }

    private WarningMessage fullGrammerWarning(SqlStatementRecord statement, String message, int syntaxErrors) {
        return WarningMessage.warn(WarningType.PARSE_WARNING,
                "FULL_GRAMMAR_SQL_PARSE_WARNING",
                message,
                statement.sourceName(),
                statement.startLine(),
                Map.of("fullGrammerSyntaxErrors", syntaxErrors,
                        "rawStatement", statement.sql()));
    }
}
