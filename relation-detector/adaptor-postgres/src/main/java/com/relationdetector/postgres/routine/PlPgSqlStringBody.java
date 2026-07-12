package com.relationdetector.postgres.routine;

public record PlPgSqlStringBody(String text, int startLine) implements PostgresRoutineBody {
    public PlPgSqlStringBody {
        text = text == null ? "" : text;
        startLine = Math.max(1, startLine);
    }
}
