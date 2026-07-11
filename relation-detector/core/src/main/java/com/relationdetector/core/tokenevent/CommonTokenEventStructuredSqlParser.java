package com.relationdetector.core.tokenevent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;

import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredParseResult;
import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.contracts.spi.AdaptorContext;
import com.relationdetector.contracts.spi.Collectors.StructuredSqlParser;
import com.relationdetector.core.antlr.common.CommonRelationSqlLexer;
import com.relationdetector.core.antlr.common.CommonRelationSqlParser;
import com.relationdetector.core.parse.AntlrSqlParseSupport;
import com.relationdetector.core.parse.SqlDialect;
import com.relationdetector.core.parse.AntlrSqlParseSupport.SyntaxErrorCounter;

/**
 * Common SQL token-event parser backed by the portable typed grammar.
 *
 * <p>CN: 这是无具体方言信息时的公共 SQL 结构来源。它使用
 * {@code CommonRelationSql.g4} 的 typed parse tree 直接生成现有
 * {@link StructuredSqlEvent}，只覆盖 MySQL/PostgreSQL 都常见的 portable SQL
 * 子集。MySQL/PostgreSQL 专属语法继续由各自 adaptor parser 负责。
 *
 * <p>EN: Common SQL token-event parser backed by the portable typed grammar.
 * It emits existing StructuredSqlEvent objects directly from
 * CommonRelationSql.g4 parse-tree contexts and intentionally covers only the
 * cross-dialect SQL subset shared by MySQL and PostgreSQL.
 */
public class CommonTokenEventStructuredSqlParser implements StructuredSqlParser {
    private final SqlDialect dialect;
    private final AntlrSqlParseSupport antlrSupport;

    public CommonTokenEventStructuredSqlParser() {
        this(SqlDialect.GENERIC);
    }

    protected CommonTokenEventStructuredSqlParser(SqlDialect dialect) {
        this.dialect = dialect;
        this.antlrSupport = new AntlrSqlParseSupport(dialect);
    }

    @Override
    public StructuredParseResult parseSql(SqlStatementRecord statement, AdaptorContext context) {
        List<WarningMessage> warnings = new ArrayList<>();
        SyntaxErrorCounter errors = new SyntaxErrorCounter();

        CommonRelationSqlLexer lexer = new CommonRelationSqlLexer(CharStreams.fromString(statement.sql()));
        lexer.removeErrorListeners();
        lexer.addErrorListener(errors);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        CommonRelationSqlParser parser = new CommonRelationSqlParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(errors);

        CommonRelationSqlParser.ScriptContext root = parser.script();
        tokens.fill();
        List<Token> visibleTokens = tokens.getTokens().stream()
                .filter(token -> token.getType() != Token.EOF)
                .filter(token -> token.getChannel() == Token.DEFAULT_CHANNEL)
                .toList();
        List<StructuredSqlEvent> events = new CommonTokenEventParseTreeVisitor(statement).collect(root);

        antlrSupport.detectDynamicSql(statement).ifPresent(warnings::add);
        warnings.addAll(TokenEventUnknownStatementDiagnostics.warnings(
                statement,
                root,
                events,
                CommonRelationSqlParser.UnknownStatementContext.class::isInstance));
        warnings.forEach(warning -> {
            if (context != null) {
                context.warn(warning);
            }
        });

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("grammar", "CommonRelationSql");
        attributes.put("parser", CommonRelationSqlParser.class.getSimpleName());
        attributes.put("lexer", CommonRelationSqlLexer.class.getSimpleName());
        attributes.put("eventBuilder", CommonTokenEventParseTreeVisitor.class.getSimpleName());
        attributes.put("syntaxErrors", errors.count());
        attributes.put("tokenCount", visibleTokens.size());
        attributes.put("tokenEvent", true);
        attributes.put("tokenEventPrimary", true);
        return new StructuredParseResult(
                "ANTLR_COMMON_TOKEN_EVENT",
                dialect.name(),
                statement.sourceName(),
                events,
                warnings,
                attributes);
    }
}
