package com.relationdetector.oracle.fullgrammar.v19c;

import java.util.List;

import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.oracle.fullgrammar.common.OracleFullGrammarParseTreeEventCollector;

/**
 *
 * Oracle 19c generated-parser bridge to the shared full-grammar event collector.
 */
public final class OracleFullGrammarParseTreeVisitor {
    private final OracleFullGrammarParseTreeEventCollector collector;

    public OracleFullGrammarParseTreeVisitor(SqlStatementRecord statement) {
        this.collector = new OracleFullGrammarParseTreeEventCollector(statement, new OracleParseTreeAdapter());
    }

    public List<StructuredSqlEvent> collect(OracleFullGrammarParser.Sql_scriptContext root) {
        return collector.collect(root);
    }
}
