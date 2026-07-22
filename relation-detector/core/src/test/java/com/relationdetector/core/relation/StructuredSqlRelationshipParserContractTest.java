package com.relationdetector.core.relation;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.Enums.WarningType;
import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.contracts.parse.RowsetEvent;
import com.relationdetector.contracts.parse.SourceProvenance;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredParseResult;
import com.relationdetector.contracts.spi.AdaptorContext;
import com.relationdetector.core.scan.AdaptorContractException;

class StructuredSqlRelationshipParserContractTest {
    @Test
    void directFacadeRejectsInvalidStructuredResultWithoutForwardingWarnings() {
        List<WarningMessage> warnings = new ArrayList<>();
        SqlStatementRecord statement = new SqlStatementRecord(
                "SELECT * FROM orders", StatementSourceType.PLAIN_SQL,
                "input.sql", 1, 1, Map.of());
        StructuredSqlRelationshipParser parser = new StructuredSqlRelationshipParser((record, context) -> {
            context.warn(WarningMessage.warn(
                    WarningType.PARSE_WARNING, "PLUGIN_WARNING", "must remain detached",
                    record.sourceName(), record.startLine()));
            return new StructuredParseResult(
                    "plugin", "mysql", record.sourceName(),
                    List.of(new RowsetEvent(
                            StructuredParseEventType.ROWSET_REFERENCE,
                            SourceProvenance.source(record.sourceName(), record.startLine()),
                            "FROM", "", "", "", "", "", "")),
                    List.of(), Map.of());
        });

        assertThrows(AdaptorContractException.class,
                () -> parser.parse(statement, new AdaptorContext(null, Map.of(), warnings::add)));
        assertTrue(warnings.isEmpty());
    }
}
