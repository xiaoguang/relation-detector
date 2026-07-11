package com.relationdetector.oracle.fullgrammer.v21c;

import java.util.List;

import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.oracle.fullgrammer.common.OracleFullGrammerParseTreeEventCollector;

/**
 * Oracle 21c generated-parser bridge to the shared full-grammer event collector.
 */
public final class OracleFullGrammerParseTreeVisitor {
    private final OracleFullGrammerParseTreeEventCollector collector;

    public OracleFullGrammerParseTreeVisitor(SqlStatementRecord statement) {
        this.collector = new OracleFullGrammerParseTreeEventCollector(statement, new Oracle21cParseTreeAdapter());
    }

    public List<StructuredSqlEvent> collect(OracleFullGrammerParser.Sql_scriptContext root) {
        return collector.collect(root);
    }
}
