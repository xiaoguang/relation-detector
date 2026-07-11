package com.relationdetector.oracle.fullgrammer.v26ai;

import java.util.List;

import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.oracle.fullgrammer.common.OracleFullGrammerParseTreeEventCollector;

/**
 * Oracle 26ai generated-parser bridge to the shared full-grammer event collector.
 */
public final class OracleFullGrammerParseTreeVisitor {
    private final OracleFullGrammerParseTreeEventCollector collector;

    public OracleFullGrammerParseTreeVisitor(SqlStatementRecord statement) {
        this.collector = new OracleFullGrammerParseTreeEventCollector(statement, new Oracle26aiParseTreeAdapter());
    }

    public List<StructuredSqlEvent> collect(OracleFullGrammerParser.Sql_scriptContext root) {
        return collector.collect(root);
    }
}
