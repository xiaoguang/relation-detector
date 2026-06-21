package com.relationdetector.postgres.fullgrammer.v16;

import com.relationdetector.core.*;
import com.relationdetector.core.fullgrammer.*;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;

import com.relationdetector.api.AdaptorContext;
import com.relationdetector.api.Collectors.StructuredDdlParser;
import com.relationdetector.api.StructuredParseResult;
import com.relationdetector.api.StructuredSqlEvent;
import com.relationdetector.api.WarningMessage;
import com.relationdetector.api.Enums.WarningType;
import com.relationdetector.postgres.fullgrammer.v16.PostgresFullGrammerLexer;
import com.relationdetector.postgres.fullgrammer.v16.PostgresFullGrammerParser;

/** PostgreSQL 16 full-grammer DDL shadow parser. */
final class PostgresFullGrammerStructuredDdlParser implements StructuredDdlParser {
    private final PostgresFullGrammerDdlEventCollector collector = new PostgresFullGrammerDdlEventCollector();

    @Override
    public StructuredParseResult parseDdl(String ddl, String sourceName, AdaptorContext context) {
        FullGrammerDdlParse parse = parseFullGrammer(ddl);
        List<StructuredSqlEvent> events = new ArrayList<>();
        List<WarningMessage> warnings = new ArrayList<>();
        if (parse.root() != null) {
            try {
                events.addAll(collector.collect(sourceName, parse.root()));
            } catch (RuntimeException ex) {
                warnings.add(fullGrammerWarning(sourceName, ddl,
                        "full-grammer DDL visitor failed: " + ex.getMessage(),
                        parse.syntaxErrors()));
            }
        }
        if (parse.syntaxErrors() > 0) {
            warnings.add(fullGrammerWarning(sourceName, ddl,
                    "full-grammer DDL parser reported " + parse.syntaxErrors() + " syntax error(s)",
                    parse.syntaxErrors()));
        }
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("fullGrammerDdlShadow", true);
        attributes.put("fullGrammerDdlLexer", PostgresFullGrammerLexer.class.getSimpleName());
        attributes.put("fullGrammerDdlParser", PostgresFullGrammerParser.class.getSimpleName());
        attributes.put("fullGrammerDdlEntryRule", "root");
        attributes.put("fullGrammerDdlSyntaxErrors", parse.syntaxErrors());
        attributes.put("fullGrammerDdlParseTreeRoot", parse.root() == null ? "" : parse.root().getClass().getSimpleName());
        attributes.put("fullGrammerDdlCollector", collector.getClass().getSimpleName());
        return new StructuredParseResult("FULL_GRAMMAR_DDL_SHADOW", SqlDialect.POSTGRES.name(), sourceName,
                events, warnings, attributes);
    }

    private FullGrammerDdlParse parseFullGrammer(String ddl) {
        try {
            PostgresFullGrammerLexer lexer = new PostgresFullGrammerLexer(CharStreams.fromString(ddl));
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            PostgresFullGrammerParser parser = new PostgresFullGrammerParser(tokens);
            FullGrammerSyntaxErrorCounter errors = new FullGrammerSyntaxErrorCounter();
            lexer.removeErrorListeners();
            parser.removeErrorListeners();
            lexer.addErrorListener(errors);
            parser.addErrorListener(errors);
            ParserRuleContext root = parser.root();
            return new FullGrammerDdlParse(root, errors.count());
        } catch (RuntimeException ex) {
            return new FullGrammerDdlParse(null, 1);
        }
    }

    private WarningMessage fullGrammerWarning(String sourceName, String ddl, String message, int syntaxErrors) {
        return WarningMessage.warn(WarningType.PARSE_WARNING,
                "FULL_GRAMMAR_DDL_PARSE_WARNING",
                message,
                sourceName,
                0,
                Map.of("fullGrammerDdlSyntaxErrors", syntaxErrors,
                        "rawStatement", ddl));
    }

    private record FullGrammerDdlParse(ParserRuleContext root, int syntaxErrors) {
    }
}
