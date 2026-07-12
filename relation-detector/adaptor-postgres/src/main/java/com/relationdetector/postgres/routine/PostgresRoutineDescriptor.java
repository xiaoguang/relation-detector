package com.relationdetector.postgres.routine;

public record PostgresRoutineDescriptor(
        PostgresRoutineBodyKind kind,
        String declaredLanguage,
        String body,
        int bodyStartLine,
        String sourceObjectType,
        String sourceObjectName
) {
    public PostgresRoutineDescriptor {
        kind = kind == null ? PostgresRoutineBodyKind.UNSUPPORTED_LANGUAGE : kind;
        declaredLanguage = declaredLanguage == null ? "" : declaredLanguage;
        body = body == null ? "" : body;
        bodyStartLine = Math.max(1, bodyStartLine);
        sourceObjectType = sourceObjectType == null ? "" : sourceObjectType;
        sourceObjectName = sourceObjectName == null ? "" : sourceObjectName;
    }
}
