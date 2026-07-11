package com.relationdetector.core.lineage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.Enums.LineageFlowKind;
import com.relationdetector.contracts.Enums.LineageTransformType;
import com.relationdetector.contracts.parse.ExpressionTrace;
import com.relationdetector.contracts.parse.RowsetEvent;
import com.relationdetector.contracts.parse.SourceProvenance;
import com.relationdetector.contracts.parse.WriteEvent;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredParseResult;
import com.relationdetector.contracts.parse.StructuredSqlEvent;

class StructuredDataLineageExtractorTest {
    @Test
    void copiesTypedEventLineIntoCandidateAndRawEvidence() {
        SqlStatementRecord statement = new SqlStatementRecord(
                "INSERT INTO sales_fact(order_id) SELECT o.id FROM sales_orders o",
                StatementSourceType.PLAIN_SQL,
                "sample-data/mysql/8.0/03-data/07.sql",
                20,
                28,
                Map.of(
                        "sourceFile", "sample-data/mysql/8.0/03-data/07.sql",
                        "sourceStatementId", "sample-data/mysql/8.0/03-data/07.sql:20-28",
                        "sourceObjectType", "SQL_WRITE"));
        StructuredParseResult structured = new StructuredParseResult(
                "TEST",
                "mysql",
                statement.sourceName(),
                List.of(
                        new RowsetEvent(StructuredParseEventType.ROWSET_REFERENCE, provenance(21),
                                "FROM", "sales_orders", "sales_orders", "o", "", "", ""),
                        new WriteEvent(StructuredParseEventType.WRITE_TARGET, provenance(20),
                                "sales_fact", "sales_fact", "sales_fact", "", "", "", "",
                                ExpressionTrace.empty()),
                        new WriteEvent(StructuredParseEventType.INSERT_SELECT_MAPPING, provenance(5),
                                "", "", "", "", "sales_fact", "order_id", "INSERT_SELECT",
                                ExpressionTrace.of(List.of("o"), List.of("id"),
                                        LineageFlowKind.VALUE, LineageTransformType.DIRECT))),
                List.of(),
                Map.of());

        var lineage = new StructuredDataLineageExtractor().extract(statement, structured).get(0);

        assertEquals(24L, lineage.attributes().get("sourceLine"));
        assertEquals(24L, lineage.evidence().get(0).attributes().get("sourceLine"));
    }

    private SourceProvenance provenance(long line) {
        return SourceProvenance.source("sample-data/mysql/8.0/03-data/07.sql", line);
    }
}
