package com.relationdetector.postgres.fullgrammer.common;

import com.relationdetector.core.fullgrammer.FullGrammerEventMerger;
import com.relationdetector.core.fullgrammer.FullGrammerNativeEventTypes;
import com.relationdetector.contracts.Enums.WarningType;
import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredParseResult;
import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.contracts.spi.AdaptorContext;
import com.relationdetector.contracts.spi.Collectors.StructuredSqlParser;
import com.relationdetector.postgres.fullgrammer.PostgresFullGrammerVersionSyntaxGuard;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;

/**
 * PostgreSQL full-grammer SQL parser template.
 *
 * <p>CN: 统一 PostgreSQL 各 major version 的 SQL parse 生命周期：版本语法边界、
 * warning、diagnostic attributes 和 visitor failure 处理都在这里。版本包只负责创建对应
 * generated parser 并从 typed parse tree 产出 events。
 *
 * <p>EN: Template for PostgreSQL full-grammer SQL parsers. It centralizes
 * version-boundary checks, warnings, diagnostic attributes, and visitor failure
 * handling. Version packages only bind generated parser classes and emit events
 * from their typed parse trees.
 */
public abstract class AbstractPostgresFullGrammerStructuredSqlParser implements StructuredSqlParser {
    @Override
    public final StructuredParseResult parseSql(SqlStatementRecord statement, AdaptorContext context) {
        var unsupportedSyntax = PostgresFullGrammerVersionSyntaxGuard.sqlWarning(
                majorVersion(), statement.sql(), statement.sourceName(), statement.startLine());
        if (unsupportedSyntax.isPresent()) {
            if (context != null) {
                context.warn(unsupportedSyntax.get());
            }
            return new StructuredParseResult("POSTGRES_FULL_GRAMMER_PARSE_TREE", "POSTGRES", statement.sourceName(),
                    List.of(), List.of(unsupportedSyntax.get()), unsupportedAttributes());
        }
        FullGrammerSqlParse parse = parseFullGrammer(statement.sql());
        List<StructuredSqlEvent> nativeEvents = List.of();
        List<WarningMessage> warnings = new java.util.ArrayList<>();
        if (parse.root() != null) {
            try {
                nativeEvents = extractEvents(statement, parse.visibleTokens(), parse.root());
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
        attributes.put("fullGrammerLexer", lexerName());
        attributes.put("fullGrammerParser", parserName());
        attributes.put("fullGrammerEntryRule", "root");
        attributes.put("fullGrammerSyntaxErrors", parse.syntaxErrors());
        attributes.put("fullGrammerParseTreeRoot", parse.root() == null ? "" : parse.root().getClass().getSimpleName());
        attributes.put("fullGrammerNativeEventTypes",
                FullGrammerEventMerger.eventTypeNames(FullGrammerNativeEventTypes.POSTGRES_NATIVE_EVENTS));
        return new StructuredParseResult("POSTGRES_FULL_GRAMMER_PARSE_TREE", "POSTGRES", statement.sourceName(),
                nativeEvents, warnings, attributes);
    }

    protected abstract int majorVersion();

    protected abstract String lexerName();

    protected abstract String parserName();

    protected abstract FullGrammerSqlParse parseFullGrammer(String sql);

    protected abstract List<StructuredSqlEvent> extractEvents(
            SqlStatementRecord statement,
            List<Token> visibleTokens,
            ParserRuleContext root
    );

    private Map<String, Object> unsupportedAttributes() {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("fullGrammerLexer", lexerName());
        attributes.put("fullGrammerParser", parserName());
        attributes.put("fullGrammerEntryRule", "root");
        attributes.put("fullGrammerSyntaxErrors", 1);
        attributes.put("fullGrammerVersionBoundary", majorVersion());
        return attributes;
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

    /**
     * PostgreSQL full-grammer parse result shared by version bindings.
     *
     * <p>CN: 版本 binding 返回 root、syntax error 数量和 visible tokens；公共 parser
     * 不依赖具体 generated parser 类型。
     *
     * <p>EN: Parse result returned by version bindings. The common parser does
     * not depend on concrete generated parser types.
     */
    public record FullGrammerSqlParse(ParserRuleContext root, int syntaxErrors, List<Token> visibleTokens) {
    }
}
