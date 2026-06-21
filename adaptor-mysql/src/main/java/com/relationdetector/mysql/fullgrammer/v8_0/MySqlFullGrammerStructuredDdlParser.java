package com.relationdetector.mysql.fullgrammer.v8_0;

import com.relationdetector.core.parse.SqlDialect;
import com.relationdetector.core.fullgrammer.*;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;

import com.relationdetector.contracts.spi.AdaptorContext;
import com.relationdetector.contracts.spi.Collectors.StructuredDdlParser;
import com.relationdetector.contracts.parse.StructuredParseResult;
import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.contracts.Enums.WarningType;
import com.relationdetector.mysql.fullgrammer.v8_0.MySqlFullGrammerLexer;
import com.relationdetector.mysql.fullgrammer.v8_0.MySqlFullGrammerParser;

/**
 * MySQL 8.0 full-grammer DDL parser。
 *
 * <p>CN: 使用 MySQL full grammar 解析 DDL，再由 MySqlFullGrammerDdlEventCollector
 * 产生 DDL_FOREIGN_KEY / DDL_INDEX 事件。最终 relationship 仍由 core DDL extractor 生成。
 *
 * <p>EN: MySQL 8.0 full-grammer DDL parser. It parses DDL with the MySQL full
 * grammar and uses MySqlFullGrammerDdlEventCollector to emit DDL_FOREIGN_KEY /
 * DDL_INDEX events. Final relationships are still created by the core DDL extractor.
 */
final class MySqlFullGrammerStructuredDdlParser implements StructuredDdlParser {
    private final MySqlFullGrammerDdlEventCollector collector = new MySqlFullGrammerDdlEventCollector();

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
        attributes.put("fullGrammerDdlLexer", MySqlFullGrammerLexer.class.getSimpleName());
        attributes.put("fullGrammerDdlParser", MySqlFullGrammerParser.class.getSimpleName());
        attributes.put("fullGrammerDdlEntryRule", "queries");
        attributes.put("fullGrammerDdlSyntaxErrors", parse.syntaxErrors());
        attributes.put("fullGrammerDdlParseTreeRoot", parse.root() == null ? "" : parse.root().getClass().getSimpleName());
        attributes.put("fullGrammerDdlCollector", collector.getClass().getSimpleName());
        return new StructuredParseResult("FULL_GRAMMAR_DDL_SHADOW", SqlDialect.MYSQL.name(), sourceName,
                events, warnings, attributes);
    }

    private FullGrammerDdlParse parseFullGrammer(String ddl) {
        try {
            MySqlFullGrammerLexer lexer = new MySqlFullGrammerLexer(CharStreams.fromString(ddl));
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            MySqlFullGrammerParser parser = new MySqlFullGrammerParser(tokens);
            FullGrammerSyntaxErrorCounter errors = new FullGrammerSyntaxErrorCounter();
            lexer.removeErrorListeners();
            parser.removeErrorListeners();
            lexer.addErrorListener(errors);
            parser.addErrorListener(errors);
            ParserRuleContext root = parser.queries();
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
