package com.relationdetector.postgres.fullgrammar.common;

import com.relationdetector.contracts.Enums.WarningType;
import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.contracts.parse.StructuredParseResult;
import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.contracts.spi.AdaptorContext;
import com.relationdetector.contracts.spi.Collectors.StructuredDdlParser;
import com.relationdetector.core.parse.SqlDialect;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.antlr.v4.runtime.ParserRuleContext;

/**
 * PostgreSQL full-grammar DDL parser template.
 *
 * <p>CN: 统一 PostgreSQL 各版本 DDL full-grammar 的 parse 生命周期、version guard、
 * warning 和 attributes。版本包只绑定具体 generated parser 和 DDL typed collector。
 *
 * <p>EN: Template for PostgreSQL full-grammar DDL parsers. It centralizes the
 * parse lifecycle, version guard, warnings, and attributes while version
 * packages bind generated parsers and typed DDL collectors.
 */
public abstract class AbstractPostgresFullGrammarStructuredDdlParser implements StructuredDdlParser {
    @Override
    public final StructuredParseResult parseDdl(String ddl, String sourceName, AdaptorContext context) {
        FullGrammarDdlParse parse = parseFullGrammar(ddl);
        List<StructuredSqlEvent> events = List.of();
        List<WarningMessage> warnings = new java.util.ArrayList<>();
        if (parse.root() != null && parse.syntaxErrors() == 0) {
            try {
                events = collectEvents(sourceName, parse.root());
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
        attributes.put("fullGrammarDdlLexer", lexerName());
        attributes.put("fullGrammarDdlParser", parserName());
        attributes.put("fullGrammarDdlEntryRule", "root");
        attributes.put("fullGrammarDdlSyntaxErrors", parse.syntaxErrors());
        attributes.put("fullGrammarDdlParseTreeRoot", parse.root() == null ? "" : parse.root().getClass().getSimpleName());
        attributes.put("fullGrammarDdlCollector", collectorName());
        return new StructuredParseResult("FULL_GRAMMAR_DDL", SqlDialect.POSTGRES.name(), sourceName,
                events, warnings, attributes);
    }

    protected abstract int majorVersion();

    protected abstract String lexerName();

    protected abstract String parserName();

    protected abstract String collectorName();

    protected abstract FullGrammarDdlParse parseFullGrammar(String ddl);

    protected abstract List<StructuredSqlEvent> collectEvents(String sourceName, ParserRuleContext root);

    private WarningMessage fullGrammarWarning(String sourceName, String ddl, String message, int syntaxErrors) {
        return WarningMessage.warn(WarningType.PARSE_WARNING,
                "FULL_GRAMMAR_DDL_PARSE_WARNING",
                message,
                sourceName,
                0,
                Map.of("fullGrammarDdlSyntaxErrors", syntaxErrors,
                        "rawStatement", ddl));
    }

    /**
     * PostgreSQL full-grammar DDL parse result shared by version bindings.
     *
     * <p>CN: 版本 binding 返回 root 和 syntax error 数量；公共 parser 不引用具体
     * generated parser 类型。
     *
     * <p>EN: DDL parse result returned by version bindings. The common parser
     * does not reference concrete generated parser types.
     */
    public record FullGrammarDdlParse(ParserRuleContext root, int syntaxErrors) {
    }
}
