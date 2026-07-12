package com.relationdetector.postgres.routine;

/** Typed body selected from a PostgreSQL routine declaration context. */
public sealed interface PostgresRoutineBody permits
        PlPgSqlStringBody, SqlStringBody, SqlAtomicBody, UnsupportedRoutineBody {
    int startLine();
}
