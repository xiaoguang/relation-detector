package com.relationdetector.mysql.tokenevent;

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
import com.relationdetector.mysql.tokenevent.MySqlRelationSqlLexer;
import com.relationdetector.mysql.tokenevent.MySqlRelationSqlParser;
import com.relationdetector.core.parse.AntlrSqlParseSupport.SyntaxErrorCounter;
import com.relationdetector.core.parse.SqlDialect;

/**
 * MySQL DDL token-event parser backed by the MySQL typed structural grammar.
 *
 * <p>CN: MySQL DDL 不再走 common DDL parser 或 DDL cursor/scanner。本类直接运行
 * {@code MySqlRelationSql.g4}，再复用 MySQL typed visitor 中的 DDL context 事件生成。
 *
 * <p>EN: MySQL DDL token-event parser backed by the MySQL typed structural
 * grammar. It emits DDL events from typed parse-tree contexts and does not use
 * the legacy DDL cursor/scanner path.
 */
public final class MySqlTokenEventStructuredDdlParser implements StructuredDdlParser {
    @Override
    public StructuredParseResult parseDdl(String ddl, String sourceName, AdaptorContext context) {
        SyntaxErrorCounter errors = new SyntaxErrorCounter();
        MySqlRelationSqlLexer lexer = new MySqlRelationSqlLexer(CharStreams.fromString(ddl));
        lexer.removeErrorListeners();
        lexer.addErrorListener(errors);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        MySqlRelationSqlParser parser = new MySqlRelationSqlParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(errors);
        MySqlRelationSqlParser.ScriptContext root = parser.script();
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
                ? new MySqlTokenEventParseTreeVisitor(statement).collect(root).stream()
                        .filter(event -> event.type().name().startsWith("DDL_"))
                        .toList()
                : List.of();

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("ddlMode", true);
        attributes.put("grammar", "MySqlRelationSql");
        attributes.put("parser", MySqlRelationSqlParser.class.getSimpleName());
        attributes.put("lexer", MySqlRelationSqlLexer.class.getSimpleName());
        attributes.put("eventBuilder", MySqlTokenEventParseTreeVisitor.class.getSimpleName());
        attributes.put("backend", "ANTLR_TOKEN_EVENT_DDL");
        attributes.put("tokenEvent", true);
        attributes.put("tokenEventPrimary", true);
        attributes.put("syntaxErrors", errors.count());
        attributes.put("tokenCount", visibleTokens.size());
        return new StructuredParseResult("ANTLR_TOKEN_EVENT_DDL", SqlDialect.MYSQL.name(), sourceName,
                events, new ArrayList<>(), attributes);
    }
}
