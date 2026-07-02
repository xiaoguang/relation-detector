package com.relationdetector.mysql.fullgrammer.v8_0;

import com.relationdetector.core.fullgrammer.*;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;

import com.relationdetector.contracts.spi.AdaptorContext;
import com.relationdetector.contracts.spi.Collectors.StructuredSqlParser;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredParseResult;
import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.contracts.Enums.WarningType;
import com.relationdetector.mysql.fullgrammer.v8_0.MySqlFullGrammerLexer;
import com.relationdetector.mysql.fullgrammer.v8_0.MySqlFullGrammerParser;
import com.relationdetector.mysql.fullgrammer.MySqlFullGrammerSqlNormalizer;

/**
 * MySQL 8.0 full-grammer SQL parser。
 *
 * <p>CN: 使用 vendored MySQL full grammar entry rule 解析 SQL，再由
 * MySqlTokenEventParseTreeVisitor 从 typed parse-tree context 生成统一 StructuredSqlEvent。
 * relationship 和 lineage 语义不在这里判断。
 *
 * <p>EN: MySQL 8.0 full-grammer SQL parser. It parses SQL with the vendored
 * MySQL full grammar entry rule, then uses MySqlTokenEventParseTreeVisitor to
 * emit unified StructuredSqlEvent records from typed parse-tree contexts. It
 * does not decide relationship or lineage semantics.
 */
final class MySqlFullGrammerStructuredSqlParser implements StructuredSqlParser {
    MySqlFullGrammerStructuredSqlParser() {
    }

    /**
     * 解析 SQL 并返回 full-grammer 结构事件、warning 和 profile 诊断属性。
     *
     * <p>EN: Parses SQL and returns full-grammer structured events, warnings,
     * and profile diagnostic attributes.
     */
    @Override
    public StructuredParseResult parseSql(SqlStatementRecord statement, AdaptorContext context) {
        FullGrammerParse parse = parseFullGrammer(statement.sql());
        List<StructuredSqlEvent> nativeEvents = new ArrayList<>();
        List<WarningMessage> warnings = new ArrayList<>();
        if (parse.root() != null) {
            try {
                nativeEvents.addAll(new MySqlTokenEventParseTreeVisitor(statement, parse.visibleTokens())
                        .extract(parse.root()));
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
        attributes.put("fullGrammerLexer", MySqlFullGrammerLexer.class.getSimpleName());
        attributes.put("fullGrammerParser", MySqlFullGrammerParser.class.getSimpleName());
        attributes.put("fullGrammerEntryRule", "queries");
        attributes.put("fullGrammerSyntaxErrors", parse.syntaxErrors());
        attributes.put("fullGrammerParseTreeRoot", parse.root() == null ? "" : parse.root().getClass().getSimpleName());
        attributes.put("fullGrammerNativeEventTypes",
                FullGrammerEventMerger.eventTypeNames(FullGrammerNativeEventTypes.MYSQL_NATIVE_EVENTS));
        return new StructuredParseResult("MYSQL_FULL_GRAMMER_PARSE_TREE", "MYSQL", statement.sourceName(),
                nativeEvents, warnings, attributes);
    }

    private FullGrammerParse parseFullGrammer(String sql) {
        try {
            MySqlFullGrammerLexer lexer = new MySqlFullGrammerLexer(
                    CharStreams.fromString(MySqlFullGrammerSqlNormalizer.normalizeClientDelimiters(sql)));
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            MySqlFullGrammerParser parser = new MySqlFullGrammerParser(tokens);
            FullGrammerSyntaxErrorCounter errors = new FullGrammerSyntaxErrorCounter();
            lexer.removeErrorListeners();
            parser.removeErrorListeners();
            lexer.addErrorListener(errors);
            parser.addErrorListener(errors);
            ParserRuleContext root = parser.queries();
            List<Token> visibleTokens = tokens.getTokens().stream()
                    .filter(token -> token.getChannel() == Token.DEFAULT_CHANNEL)
                    .toList();
            return new FullGrammerParse(root, errors.count(), visibleTokens);
        } catch (RuntimeException ex) {
            return new FullGrammerParse(null, 1, List.of());
        }
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

    private record FullGrammerParse(ParserRuleContext root, int syntaxErrors, List<Token> visibleTokens) {
    }
}
