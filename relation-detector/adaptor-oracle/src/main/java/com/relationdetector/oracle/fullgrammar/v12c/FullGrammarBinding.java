package com.relationdetector.oracle.fullgrammar.v12c;

import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.oracle.fullgrammar.common.OracleFullGrammarDdlBinding;
import com.relationdetector.oracle.fullgrammar.common.OracleFullGrammarParseSupport;
import com.relationdetector.oracle.fullgrammar.common.OracleFullGrammarSqlBinding;

final class FullGrammarBinding implements OracleFullGrammarSqlBinding, OracleFullGrammarDdlBinding {
    @Override
    public String lexerName() {
        return OracleFullGrammarLexer.class.getSimpleName();
    }

    @Override
    public String parserName() {
        return OracleFullGrammarParser.class.getSimpleName();
    }

    @Override
    public String visitorName() {
        return OracleFullGrammarParseTreeVisitor.class.getSimpleName();
    }

    @Override
    public OracleFullGrammarParseSupport.ParsedEvents parseSql(SqlStatementRecord statement) {
        return parse(statement);
    }

    @Override
    public OracleFullGrammarParseSupport.ParsedEvents parseDdl(SqlStatementRecord statement) {
        return parse(statement, true);
    }

    private OracleFullGrammarParseSupport.ParsedEvents parse(SqlStatementRecord statement) {
        return parse(statement, false);
    }

    private OracleFullGrammarParseSupport.ParsedEvents parse(SqlStatementRecord statement, boolean collectWithSyntaxErrors) {
        return OracleFullGrammarParseSupport.parse(statement,
                OracleFullGrammarLexer::new,
                OracleFullGrammarParser::new,
                OracleFullGrammarParser::sql_script,
                root -> new OracleFullGrammarParseTreeVisitor(statement).collect(root),
                collectWithSyntaxErrors);
    }
}
