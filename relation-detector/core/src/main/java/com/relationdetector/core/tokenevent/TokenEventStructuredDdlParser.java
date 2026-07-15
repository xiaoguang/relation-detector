package com.relationdetector.core.tokenevent;

import com.relationdetector.core.parse.SqlDialect;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;

import com.relationdetector.contracts.spi.AdaptorContext;
import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.spi.Collectors.StructuredDdlParser;
import com.relationdetector.contracts.parse.StructuredParseResult;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.core.antlr.common.CommonRelationSqlLexer;
import com.relationdetector.core.antlr.common.CommonRelationSqlParser;
import com.relationdetector.core.parse.AntlrSqlParseSupport.SyntaxErrorCounter;
import com.relationdetector.core.parse.AntlrSllParseSupport;

/**
 * token-event DDL parser 基类。
 *
 * <p>CN: DDL 不复用 SQL parser，因为 schema definition 与 query/DML 的结构和语义不同。
 * 本类使用 common typed structural grammar，把 DDL text 转成统一
 * StructuredParseResult。
 *
 * <p>EN: Base DDL parser for the token-event pipeline. DDL does not reuse the
 * SQL parser because schema definition text has different structure and
 * semantics from query/DML text. This class uses the common typed structural
 * grammar and returns a StructuredParseResult.
 */
public class TokenEventStructuredDdlParser implements StructuredDdlParser {
    private final SqlDialect dialect;

    public TokenEventStructuredDdlParser(SqlDialect dialect) {
        this.dialect = dialect;
    }

    /**
     * 解析 DDL 文本并返回 DDL 结构事件。
     *
     * <p>EN: Parses DDL text and returns DDL structured events.
     */
    @Override
    public StructuredParseResult parseDdl(String ddl, String sourceName, AdaptorContext context) {
        SyntaxErrorCounter errors = new SyntaxErrorCounter();
        CommonRelationSqlLexer lexer = new CommonRelationSqlLexer(CharStreams.fromString(ddl));
        lexer.removeErrorListeners();
        lexer.addErrorListener(errors);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        CommonRelationSqlParser.ScriptContext root = AntlrSllParseSupport.parse(
                tokens, CommonRelationSqlParser::new, CommonRelationSqlParser::script, errors).root();
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
                ? new CommonTokenEventParseTreeVisitor(statement).collect(root).stream()
                        .filter(event -> event.type().name().startsWith("DDL_"))
                        .toList()
                : List.of();

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("ddlMode", true);
        attributes.put("grammar", "CommonRelationSql");
        attributes.put("parser", CommonRelationSqlParser.class.getSimpleName());
        attributes.put("lexer", CommonRelationSqlLexer.class.getSimpleName());
        attributes.put("eventBuilder", CommonTokenEventParseTreeVisitor.class.getSimpleName());
        attributes.put("backend", "ANTLR_TOKEN_EVENT_DDL");
        attributes.put("tokenEvent", true);
        attributes.put("tokenEventPrimary", true);
        attributes.put("syntaxErrors", errors.count());
        attributes.put("tokenCount", visibleTokens.size());
        return new StructuredParseResult("ANTLR_TOKEN_EVENT_DDL", dialect.name(), sourceName,
                events, new ArrayList<>(), attributes);
    }
}
