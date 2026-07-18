package com.relationdetector.postgres.routine;

import java.util.List;

import com.relationdetector.contracts.parse.SqlStatementRecord;

/**
 * CN: 表示 outer grammar 已 typed 提取的 BEGIN ATOMIC 静态 SQL statements；dispatcher 将其直接交给当前 PostgreSQL SQL parser，不经过 PL/pgSQL shell。
 * EN: Represents BEGIN ATOMIC static SQL statements extracted by the typed outer grammar. The dispatcher sends them directly to the active PostgreSQL SQL parser, bypassing the PL/pgSQL shell.
 */
public record SqlAtomicBody(List<SqlStatementRecord> statements, int startLine) implements PostgresRoutineBody {
    public SqlAtomicBody {
        statements = statements == null ? List.of() : List.copyOf(statements);
        startLine = Math.max(1, startLine);
    }
}
