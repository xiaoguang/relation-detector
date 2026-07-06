package com.relationdetector.mysql.fullgrammer.v5_7;

import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.mysql.fullgrammer.common.MySqlFullGrammerDdlBinding;
import com.relationdetector.mysql.fullgrammer.common.MySqlFullGrammerParseSupport;
import com.relationdetector.mysql.fullgrammer.common.MySqlFullGrammerParseSupport.FullGrammerDdlParse;
import com.relationdetector.mysql.fullgrammer.common.MySqlFullGrammerParseSupport.FullGrammerSqlParse;
import com.relationdetector.mysql.fullgrammer.common.MySqlFullGrammerSqlBinding;
import java.util.List;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;

/**
 * MySQL 5.7 generated grammar binding.
 */
final class MySqlFullGrammerVersionBinding implements MySqlFullGrammerSqlBinding, MySqlFullGrammerDdlBinding {
    private final MySqlFullGrammerDdlEventCollector ddlCollector = new MySqlFullGrammerDdlEventCollector();

    @Override
    public String lexerName() {
        return MySqlFullGrammerLexer.class.getSimpleName();
    }

    @Override
    public String parserName() {
        return MySqlFullGrammerParser.class.getSimpleName();
    }

    @Override
    public FullGrammerSqlParse parseSql(String sql) {
        return MySqlFullGrammerParseSupport.parseSql(
                sql,
                MySqlFullGrammerLexer::new,
                MySqlFullGrammerParser::new,
                MySqlFullGrammerParser::queries);
    }

    @Override
    public List<StructuredSqlEvent> extractEvents(
            SqlStatementRecord statement,
            List<Token> visibleTokens,
            ParserRuleContext root
    ) {
        return new MySqlFullGrammerParseTreeVisitor(statement, visibleTokens).extract(root);
    }

    @Override
    public String collectorName() {
        return ddlCollector.getClass().getSimpleName();
    }

    @Override
    public FullGrammerDdlParse parseDdl(String ddl) {
        return MySqlFullGrammerParseSupport.parseDdl(
                ddl,
                MySqlFullGrammerLexer::new,
                MySqlFullGrammerParser::new,
                MySqlFullGrammerParser::queries);
    }

    @Override
    public List<StructuredSqlEvent> collectEvents(String sourceName, ParserRuleContext root) {
        return ddlCollector.collect(sourceName, root);
    }
}
