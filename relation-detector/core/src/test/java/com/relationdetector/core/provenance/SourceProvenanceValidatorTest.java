package com.relationdetector.core.provenance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.parse.SourceProvenance;
import com.relationdetector.contracts.parse.SqlStatementRecord;

class SourceProvenanceValidatorTest {
    private final SourceProvenanceValidator validator = new SourceProvenanceValidator();

    @Test
    void acceptsAbsoluteEventLineInsideStatementAndFile() {
        SqlStatementRecord statement = statement(20, 28, "sample-data/mysql/8.0/input.sql");
        SourceProvenance provenance = SourceProvenance.source(statement.sourceName(), 24);

        assertEquals(java.util.List.of(), validator.validate(statement, provenance, 100));
    }

    @Test
    void rejectsRelativeOrDoublyOffsetLineAndAbsoluteFile() {
        SqlStatementRecord statement = statement(20, 28, "/Users/me/input.sql");

        var violations = validator.validate(
                statement,
                SourceProvenance.source(statement.sourceName(), 43),
                40);

        assertTrue(violations.stream().anyMatch(v -> v.code().equals("SOURCE_LINE_OUTSIDE_STATEMENT")));
        assertTrue(violations.stream().anyMatch(v -> v.code().equals("SOURCE_LINE_OUTSIDE_FILE")));
        assertTrue(violations.stream().anyMatch(v -> v.code().equals("ABSOLUTE_SOURCE_FILE")));
    }

    private SqlStatementRecord statement(long start, long end, String sourceFile) {
        return new SqlStatementRecord("SELECT 1", StatementSourceType.PLAIN_SQL, sourceFile,
                start, end, Map.of(
                        "sourceFile", sourceFile,
                        "sourceStatementId", sourceFile + ":" + start + "-" + end));
    }
}
