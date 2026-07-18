package com.relationdetector.oracle.fullgrammar.v19c;

import java.util.List;

import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.oracle.fullgrammar.common.OracleFullGrammarParseTreeEventCollector;

/**
 * CN: 将 Oracle 19c generated root 与 19c typed adapter 交给共享 per-parse collector，返回 structured events；只桥接本版本，不解释 SQL 文本。
 * EN: Connects an Oracle 19c generated root and 19c typed adapter to the shared per-parse collector and returns structured events, bridging only this version without interpreting SQL text.
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
