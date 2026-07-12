package com.relationdetector.postgres.routine;

public enum PostgresRoutineBodyKind {
    PLPGSQL,
    SQL_STRING,
    SQL_ATOMIC,
    UNSUPPORTED_LANGUAGE
}
