package com.relationdetector.postgres.fullgrammar.common;

import com.relationdetector.core.fullgrammar.FullGrammarEventMerger;
import com.relationdetector.core.fullgrammar.FullGrammarNativeEventTypes;
import com.relationdetector.contracts.Enums.WarningType;
import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredParseResult;
import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.contracts.spi.AdaptorContext;
import com.relationdetector.contracts.spi.Collectors.StructuredSqlParser;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;

/**
 * PostgreSQL full-grammar SQL parser template.
 *
 * <p>CN: 统一 PostgreSQL 各 major version 的 SQL parse 生命周期：版本语法边界、
 * warning、diagnostic attributes 和 visitor failure 处理都在这里。版本包只负责创建对应
 * generated parser 并从 typed parse tree 产出 events。
 *
 * <p>EN: Template for PostgreSQL full-grammar SQL parsers. It centralizes
 * version-boundary checks, warnings, diagnostic attributes, and visitor failure
 * handling. Version packages only bind generated parser classes and emit events
 * from their typed parse trees.
 */
public abstract class AbstractPostgresFullGrammarStructuredSqlParser implements StructuredSqlParser {
    @Override
    public final StructuredParseResult parseSql(SqlStatementRecord statement, AdaptorContext context) {
        FullGrammarSqlParse parse = parseFullGrammar(statement.sql());
        List<StructuredSqlEvent> nativeEvents = List.of();
        List<WarningMessage> warnings = new java.util.ArrayList<>();
        Map<String, Object> visitorAttributes = Map.of();
        if (parse.root() != null && parse.syntaxErrors() == 0) {
            try {
                PostgresFullGrammarEventOutcome outcome = extractEvents(
                        statement, parse.visibleTokens(), parse.root());
                nativeEvents = outcome.events();
                warnings.addAll(outcome.warnings());
                visitorAttributes = outcome.attributes();
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
        attributes.put("fullGrammarLexer", lexerName());
        attributes.put("fullGrammarParser", parserName());
        attributes.put("fullGrammarEntryRule", "root");
        attributes.put("fullGrammarSyntaxErrors", parse.syntaxErrors());
        attributes.put("fullGrammarParseTreeRoot", parse.root() == null ? "" : parse.root().getClass().getSimpleName());
        attributes.put("fullGrammarNativeEventTypes",
                FullGrammarEventMerger.eventTypeNames(FullGrammarNativeEventTypes.POSTGRES_NATIVE_EVENTS));
        attributes.putAll(visitorAttributes);
        return new StructuredParseResult("POSTGRES_FULL_GRAMMAR_PARSE_TREE", "POSTGRES", statement.sourceName(),
                nativeEvents, warnings, attributes);
    }

    protected abstract int majorVersion();

    protected abstract String lexerName();

    protected abstract String parserName();

    protected abstract FullGrammarSqlParse parseFullGrammar(String sql);

    protected abstract PostgresFullGrammarEventOutcome extractEvents(
            SqlStatementRecord statement,
            List<Token> visibleTokens,
            ParserRuleContext root
    );

    private WarningMessage fullGrammarWarning(SqlStatementRecord statement, String message, int syntaxErrors) {
        return WarningMessage.warn(WarningType.PARSE_WARNING,
                "FULL_GRAMMAR_SQL_PARSE_WARNING",
                message,
                statement.sourceName(),
                statement.startLine(),
                Map.of("fullGrammarSyntaxErrors", syntaxErrors,
                        "rawStatement", statement.sql()));
    }

    /**
     * PostgreSQL full-grammar parse result shared by version bindings.
     *
     * <p>CN: 版本 binding 返回 root、syntax error 数量和 visible tokens；公共 parser
     * 不依赖具体 generated parser 类型。
     *
     * <p>EN: Parse result returned by version bindings. The common parser does
     * not depend on concrete generated parser types.
     */
    public record FullGrammarSqlParse(ParserRuleContext root, int syntaxErrors, List<Token> visibleTokens) {
    }
}
