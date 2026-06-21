package com.relationdetector.postgres.fullgrammer.common;

import com.relationdetector.contracts.Enums.WarningType;
import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.contracts.parse.StructuredParseResult;
import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.contracts.spi.AdaptorContext;
import com.relationdetector.contracts.spi.Collectors.StructuredDdlParser;
import com.relationdetector.core.parse.SqlDialect;
import com.relationdetector.postgres.fullgrammer.PostgresFullGrammerVersionSyntaxGuard;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.antlr.v4.runtime.ParserRuleContext;

/**
 * PostgreSQL full-grammer DDL parser template.
 *
 * <p>CN: 统一 PostgreSQL 各版本 DDL full-grammer 的 parse 生命周期、version guard、
 * warning 和 attributes。版本包只绑定具体 generated parser 和 DDL typed collector。
 *
 * <p>EN: Template for PostgreSQL full-grammer DDL parsers. It centralizes the
 * parse lifecycle, version guard, warnings, and attributes while version
 * packages bind generated parsers and typed DDL collectors.
 */
public abstract class AbstractPostgresFullGrammerStructuredDdlParser implements StructuredDdlParser {
    @Override
    public final StructuredParseResult parseDdl(String ddl, String sourceName, AdaptorContext context) {
        var unsupportedSyntax = PostgresFullGrammerVersionSyntaxGuard.ddlWarning(majorVersion(), ddl, sourceName);
        if (unsupportedSyntax.isPresent()) {
            return new StructuredParseResult("FULL_GRAMMAR_DDL_SHADOW", SqlDialect.POSTGRES.name(), sourceName,
                    List.of(), List.of(unsupportedSyntax.get()), unsupportedAttributes());
        }
        FullGrammerDdlParse parse = parseFullGrammer(ddl);
        List<StructuredSqlEvent> events = List.of();
        List<WarningMessage> warnings = new java.util.ArrayList<>();
        if (parse.root() != null) {
            try {
                events = collectEvents(sourceName, parse.root());
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
        attributes.put("fullGrammerDdlLexer", lexerName());
        attributes.put("fullGrammerDdlParser", parserName());
        attributes.put("fullGrammerDdlEntryRule", "root");
        attributes.put("fullGrammerDdlSyntaxErrors", parse.syntaxErrors());
        attributes.put("fullGrammerDdlParseTreeRoot", parse.root() == null ? "" : parse.root().getClass().getSimpleName());
        attributes.put("fullGrammerDdlCollector", collectorName());
        return new StructuredParseResult("FULL_GRAMMAR_DDL_SHADOW", SqlDialect.POSTGRES.name(), sourceName,
                events, warnings, attributes);
    }

    protected abstract int majorVersion();

    protected abstract String lexerName();

    protected abstract String parserName();

    protected abstract String collectorName();

    protected abstract FullGrammerDdlParse parseFullGrammer(String ddl);

    protected abstract List<StructuredSqlEvent> collectEvents(String sourceName, ParserRuleContext root);

    private Map<String, Object> unsupportedAttributes() {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("fullGrammerDdlShadow", true);
        attributes.put("fullGrammerDdlLexer", lexerName());
        attributes.put("fullGrammerDdlParser", parserName());
        attributes.put("fullGrammerDdlEntryRule", "root");
        attributes.put("fullGrammerDdlSyntaxErrors", 1);
        attributes.put("fullGrammerVersionBoundary", majorVersion());
        attributes.put("fullGrammerDdlCollector", collectorName());
        return attributes;
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

    /**
     * PostgreSQL full-grammer DDL parse result shared by version bindings.
     *
     * <p>CN: 版本 binding 返回 root 和 syntax error 数量；公共 parser 不引用具体
     * generated parser 类型。
     *
     * <p>EN: DDL parse result returned by version bindings. The common parser
     * does not reference concrete generated parser types.
     */
    public record FullGrammerDdlParse(ParserRuleContext root, int syntaxErrors) {
    }
}
