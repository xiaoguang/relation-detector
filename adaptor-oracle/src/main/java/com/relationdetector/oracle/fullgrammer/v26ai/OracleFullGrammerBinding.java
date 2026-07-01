package com.relationdetector.oracle.fullgrammer.v26ai;

import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.oracle.fullgrammer.common.OracleFullGrammerDdlBinding;
import com.relationdetector.oracle.fullgrammer.common.OracleFullGrammerParseSupport;
import com.relationdetector.oracle.fullgrammer.common.OracleFullGrammerSqlBinding;

final class OracleFullGrammerBinding implements OracleFullGrammerSqlBinding, OracleFullGrammerDdlBinding {
    @Override
    public String lexerName() {
        return OracleFullGrammerLexer.class.getSimpleName();
    }

    @Override
    public String parserName() {
        return OracleFullGrammerParser.class.getSimpleName();
    }

    @Override
    public String visitorName() {
        return OracleFullGrammerParseTreeVisitor.class.getSimpleName();
    }

    @Override
    public OracleFullGrammerParseSupport.ParsedEvents parseSql(SqlStatementRecord statement) {
        return parse(statement);
    }

    @Override
    public OracleFullGrammerParseSupport.ParsedEvents parseDdl(SqlStatementRecord statement) {
        return parse(statement, true);
    }

    private OracleFullGrammerParseSupport.ParsedEvents parse(SqlStatementRecord statement) {
        return parse(statement, false);
    }

    private OracleFullGrammerParseSupport.ParsedEvents parse(SqlStatementRecord statement, boolean collectWithSyntaxErrors) {
        return OracleFullGrammerParseSupport.parse(statement,
                OracleFullGrammerLexer::new,
                OracleFullGrammerParser::new,
                OracleFullGrammerParser::script,
                root -> new OracleFullGrammerParseTreeVisitor(statement).collect(root),
                collectWithSyntaxErrors);
    }
}
