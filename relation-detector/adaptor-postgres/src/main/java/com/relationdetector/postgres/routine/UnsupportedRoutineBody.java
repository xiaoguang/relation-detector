package com.relationdetector.postgres.routine;

public record UnsupportedRoutineBody(String declaredLanguage, int startLine) implements PostgresRoutineBody {
    public UnsupportedRoutineBody {
        declaredLanguage = declaredLanguage == null ? "" : declaredLanguage;
        startLine = Math.max(1, startLine);
    }
}
