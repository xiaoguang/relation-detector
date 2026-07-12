package com.relationdetector.postgres.routine;

import java.util.Map;

public record PostgresRoutineDescriptor(
        String declaredLanguage,
        PostgresRoutineBody body,
        String sourceObjectType,
        String sourceObjectName,
        Map<String, Object> provenance
) {
    public PostgresRoutineDescriptor(String declaredLanguage, PostgresRoutineBody body,
            String sourceObjectType, String sourceObjectName) {
        this(declaredLanguage, body, sourceObjectType, sourceObjectName, Map.of());
    }

    public PostgresRoutineDescriptor {
        declaredLanguage = declaredLanguage == null ? "" : declaredLanguage;
        body = body == null ? new UnsupportedRoutineBody(declaredLanguage, 1) : body;
        sourceObjectType = sourceObjectType == null ? "" : sourceObjectType;
        sourceObjectName = sourceObjectName == null ? "" : sourceObjectName;
        provenance = provenance == null ? Map.of() : Map.copyOf(provenance);
    }
}
