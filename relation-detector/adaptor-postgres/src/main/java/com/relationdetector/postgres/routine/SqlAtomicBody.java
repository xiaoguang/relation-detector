package com.relationdetector.postgres.routine;

import java.util.List;

import com.relationdetector.contracts.parse.SqlStatementRecord;

public record SqlAtomicBody(List<SqlStatementRecord> statements, int startLine) implements PostgresRoutineBody {
    public SqlAtomicBody {
        statements = statements == null ? List.of() : List.copyOf(statements);
        startLine = Math.max(1, startLine);
    }
}
