package com.relationdetector.mysql.fullgrammar.v8_0;

import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.mysql.fullgrammar.common.MySqlFullGrammarDdlBinding;
import com.relationdetector.mysql.fullgrammar.common.MySqlFullGrammarParseSupport;
import com.relationdetector.mysql.fullgrammar.common.MySqlFullGrammarParseSupport.FullGrammarDdlParse;
import com.relationdetector.mysql.fullgrammar.common.MySqlFullGrammarParseSupport.FullGrammarSqlParse;
import com.relationdetector.mysql.fullgrammar.common.MySqlFullGrammarSqlBinding;
import java.util.List;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;

/**
 * MySQL 8.0 generated grammar binding.
 */
final class FullGrammarBinding implements MySqlFullGrammarSqlBinding, MySqlFullGrammarDdlBinding {
    private final MySqlFullGrammarDdlEventCollector ddlCollector = new MySqlFullGrammarDdlEventCollector();

    @Override
    public String lexerName() {
        return MySqlFullGrammarLexer.class.getSimpleName();
    }

    @Override
    public String parserName() {
        return MySqlFullGrammarParser.class.getSimpleName();
    }

    @Override
    public FullGrammarSqlParse parseSql(String sql) {
        return MySqlFullGrammarParseSupport.parseSql(
                sql,
                MySqlFullGrammarLexer::new,
                MySqlFullGrammarParser::new,
                MySqlFullGrammarParser::queries);
    }

    @Override
    public List<StructuredSqlEvent> extractEvents(
            SqlStatementRecord statement,
            List<Token> visibleTokens,
            ParserRuleContext root
    ) {
        return new MySqlFullGrammarParseTreeVisitor(statement, visibleTokens).extract(root);
    }

    @Override
    public String collectorName() {
        return ddlCollector.getClass().getSimpleName();
    }

    @Override
    public FullGrammarDdlParse parseDdl(String ddl) {
        return MySqlFullGrammarParseSupport.parseDdl(
                ddl,
                MySqlFullGrammarLexer::new,
                MySqlFullGrammarParser::new,
                MySqlFullGrammarParser::queries);
    }

    @Override
    public List<StructuredSqlEvent> collectEvents(String sourceName, ParserRuleContext root) {
        return ddlCollector.collect(sourceName, root);
    }
}
