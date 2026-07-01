package com.relationdetector.oracle.tokenevent;

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
import com.relationdetector.core.parse.SqlDialect;
import com.relationdetector.core.parse.AntlrSqlParseSupport.SyntaxErrorCounter;

/**
 * Oracle DDL token-event parser.
 *
 * <p>CN: Oracle root baseline DDL 走 OracleRelationSql.g4 生成的 typed parser，
 * 不借 common/MySQL/PostgreSQL parser，也不恢复 DDL cursor/scanner。
 *
 * <p>EN: Oracle DDL token-event parser backed by OracleRelationSql.g4. It does
 * not borrow common/MySQL/PostgreSQL parsers and does not reintroduce
 * cursor/scanner DDL extraction.
 */
public final class OracleTokenEventStructuredDdlParser implements StructuredDdlParser {
    @Override
    public StructuredParseResult parseDdl(String ddl, String sourceName, AdaptorContext context) {
        SyntaxErrorCounter errors = new SyntaxErrorCounter();
        OracleRelationSqlLexer lexer = new OracleRelationSqlLexer(CharStreams.fromString(ddl));
        lexer.removeErrorListeners();
        lexer.addErrorListener(errors);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        OracleRelationSqlParser parser = new OracleRelationSqlParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(errors);
        OracleRelationSqlParser.ScriptContext root = parser.script();
        tokens.fill();
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
                ? new OracleTokenEventParseTreeVisitor(statement).collect(root).stream()
                        .filter(event -> event.type().name().startsWith("DDL_"))
                        .toList()
                : List.of();

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("ddlMode", true);
        attributes.put("grammar", "OracleRelationSql");
        attributes.put("parser", OracleRelationSqlParser.class.getSimpleName());
        attributes.put("lexer", OracleRelationSqlLexer.class.getSimpleName());
        attributes.put("eventBuilder", OracleTokenEventParseTreeVisitor.class.getSimpleName());
        attributes.put("backend", "ANTLR_TOKEN_EVENT_DDL");
        attributes.put("tokenEvent", true);
        attributes.put("tokenEventPrimary", true);
        attributes.put("syntaxErrors", errors.count());
        attributes.put("tokenCount", visibleTokens.size());
        return new StructuredParseResult("ANTLR_TOKEN_EVENT_DDL", SqlDialect.ORACLE.name(), sourceName,
                events, new ArrayList<>(), attributes);
    }
}
