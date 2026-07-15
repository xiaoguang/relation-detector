package com.relationdetector.sqlserver.tokenevent;

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

public final class SqlServerTokenEventStructuredDdlParser implements StructuredDdlParser {
    @Override
    public StructuredParseResult parseDdl(String ddl, String sourceName, AdaptorContext context) {
        SyntaxErrorCounter errors = new SyntaxErrorCounter();
        SqlServerRelationSqlLexer lexer = new SqlServerRelationSqlLexer(CharStreams.fromString(ddl));
        lexer.removeErrorListeners();
        lexer.addErrorListener(errors);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        SqlServerRelationSqlParser.Tsql_fileContext root = AntlrSllParseSupport.parse(
                tokens, SqlServerRelationSqlParser::new, SqlServerRelationSqlParser::tsql_file, errors).root();
        SqlStatementRecord statement = new SqlStatementRecord(ddl, StatementSourceType.DDL_FILE, sourceName, 1, 1, Map.of());
        List<StructuredSqlEvent> events = errors.count() == 0
                ? new SqlServerTokenEventParseTreeVisitor(statement, true).collect(root)
                : List.of();
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("ddlMode", true);
        attributes.put("grammar", "SqlServerRelationSql");
        attributes.put("parser", SqlServerRelationSqlParser.class.getSimpleName());
        attributes.put("lexer", SqlServerRelationSqlLexer.class.getSimpleName());
        attributes.put("eventBuilder", SqlServerTokenEventParseTreeVisitor.class.getSimpleName());
        attributes.put("backend", "ANTLR_TOKEN_EVENT_DDL");
        attributes.put("tokenEvent", true);
        attributes.put("tokenEventPrimary", true);
        attributes.put("syntaxErrors", errors.count());
        attributes.put("tokenCount", tokens.getTokens().stream()
                .filter(token -> token.getType() != Token.EOF)
                .filter(token -> token.getChannel() == Token.DEFAULT_CHANNEL)
                .count());
        return new StructuredParseResult("ANTLR_TOKEN_EVENT_DDL",
                SqlDialect.SQLSERVER.name(),
                sourceName,
                events,
                new ArrayList<>(),
                attributes);
    }
}
