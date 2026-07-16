package com.relationdetector.postgres.tokenevent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;

import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredParseResult;
import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.contracts.spi.AdaptorContext;
import com.relationdetector.contracts.spi.Collectors.StructuredDdlParser;
import com.relationdetector.core.parse.AntlrSllParseSupport;
import com.relationdetector.core.parse.AntlrSqlParseSupport.SyntaxErrorCounter;
import com.relationdetector.core.parse.SqlDialect;

/**
 *
 * PostgreSQL DDL token-event parser backed by PostgresRelationSql.g4.
 *
 * <p>CN: root baseline DDL 走 PostgreSQL 自己的 typed grammar / visitor，
 * 不再借 common DDL parser，也不恢复 DDL cursor/scanner。
 */
public final class PostgresTokenEventStructuredDdlParser implements StructuredDdlParser {
    @Override
    public StructuredParseResult parseDdl(String ddl, String sourceName, AdaptorContext context) {
        SyntaxErrorCounter errors = new SyntaxErrorCounter();
        PostgresRelationSqlLexer lexer = new PostgresRelationSqlLexer(CharStreams.fromString(ddl));
        lexer.removeErrorListeners();
        lexer.addErrorListener(errors);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        PostgresRelationSqlParser.ScriptContext root = AntlrSllParseSupport.parse(
                tokens, PostgresRelationSqlParser::new, PostgresRelationSqlParser::script, errors).root();
        List<Token> visibleTokens = tokens.getTokens().stream()
                .filter(token -> token.getType() != Token.EOF)
                .filter(token -> token.getChannel() == Token.DEFAULT_CHANNEL)
                .toList();
        SqlStatementRecord statement = new SqlStatementRecord(
                ddl,
                StatementSourceType.DDL_FILE,
                sourceName,
                1,
                1,
                Map.of());
        List<StructuredSqlEvent> events = errors.count() == 0
                ? new PostgresTokenEventParseTreeVisitor(statement).collect(root).stream()
                        .filter(event -> event.type().name().startsWith("DDL_"))
                        .toList()
                : List.of();

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("ddlMode", true);
        attributes.put("grammar", "PostgresRelationSql");
        attributes.put("parser", PostgresRelationSqlParser.class.getSimpleName());
        attributes.put("lexer", PostgresRelationSqlLexer.class.getSimpleName());
        attributes.put("eventBuilder", PostgresTokenEventParseTreeVisitor.class.getSimpleName());
        attributes.put("backend", "ANTLR_TOKEN_EVENT_DDL");
        attributes.put("tokenEvent", true);
        attributes.put("tokenEventPrimary", true);
        attributes.put("syntaxErrors", errors.count());
        attributes.put("tokenCount", visibleTokens.size());
        return new StructuredParseResult("ANTLR_TOKEN_EVENT_DDL", SqlDialect.POSTGRES.name(), sourceName,
                events, new ArrayList<>(), attributes);
    }
}
