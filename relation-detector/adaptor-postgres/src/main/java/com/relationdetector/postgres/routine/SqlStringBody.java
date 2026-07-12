package com.relationdetector.postgres.routine;

public record SqlStringBody(String text, int startLine) implements PostgresRoutineBody {
    public SqlStringBody {
        text = text == null ? "" : text;
        startLine = Math.max(1, startLine);
    }
}
