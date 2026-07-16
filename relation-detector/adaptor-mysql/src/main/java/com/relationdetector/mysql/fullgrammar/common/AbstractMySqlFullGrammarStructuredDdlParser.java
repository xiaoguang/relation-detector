package com.relationdetector.mysql.fullgrammar.common;

import com.relationdetector.contracts.Enums.WarningType;
import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.contracts.parse.StructuredParseResult;
import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.contracts.spi.AdaptorContext;
import com.relationdetector.contracts.spi.Collectors.StructuredDdlParser;
import com.relationdetector.core.parse.SqlDialect;
import com.relationdetector.mysql.fullgrammar.common.MySqlFullGrammarParseSupport.FullGrammarDdlParse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * Shared MySQL full-grammar DDL parser lifecycle.
 */
public abstract class AbstractMySqlFullGrammarStructuredDdlParser implements StructuredDdlParser {
    private final MySqlFullGrammarDdlBinding binding;

    protected AbstractMySqlFullGrammarStructuredDdlParser(MySqlFullGrammarDdlBinding binding) {
        this.binding = binding;
    }

    @Override
    public final StructuredParseResult parseDdl(String ddl, String sourceName, AdaptorContext context) {
        FullGrammarDdlParse parse = binding.parseDdl(ddl);
        List<StructuredSqlEvent> events = new ArrayList<>();
        List<WarningMessage> warnings = new ArrayList<>();
        if (parse.root() != null) {
            try {
                events.addAll(binding.collectEvents(sourceName, parse.root()));
            } catch (RuntimeException ex) {
                warnings.add(fullGrammarWarning(sourceName, ddl,
                        "full-grammar DDL visitor failed: " + ex.getMessage(),
                        parse.syntaxErrors()));
            }
        }
        if (parse.syntaxErrors() > 0) {
            warnings.add(fullGrammarWarning(sourceName, ddl,
                    "full-grammar DDL parser reported " + parse.syntaxErrors() + " syntax error(s)",
                    parse.syntaxErrors()));
        }
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("fullGrammarDdlParserSelected", true);
        attributes.put("fullGrammarDdlLexer", binding.lexerName());
        attributes.put("fullGrammarDdlParser", binding.parserName());
        attributes.put("fullGrammarDdlEntryRule", "queries");
        attributes.put("fullGrammarDdlSyntaxErrors", parse.syntaxErrors());
        attributes.put("fullGrammarDdlParseTreeRoot", parse.root() == null ? "" : parse.root().getClass().getSimpleName());
        attributes.put("fullGrammarDdlCollector", binding.collectorName());
        return new StructuredParseResult("FULL_GRAMMAR_DDL", SqlDialect.MYSQL.name(), sourceName,
                events, warnings, attributes);
    }

    private WarningMessage fullGrammarWarning(String sourceName, String ddl, String message, int syntaxErrors) {
        return WarningMessage.warn(WarningType.PARSE_WARNING,
                "FULL_GRAMMAR_DDL_PARSE_WARNING",
                message,
                sourceName,
                0,
                Map.of("fullGrammarDdlSyntaxErrors", syntaxErrors,
                        "rawStatement", ddl));
    }
}
