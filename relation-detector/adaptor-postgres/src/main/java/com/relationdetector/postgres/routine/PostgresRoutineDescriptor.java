package com.relationdetector.postgres.routine;

import java.util.Map;

/**
 * CN: 保存 typed routine declaration 的语言、body、对象 identity 和 source provenance，供 language dispatcher 选择当前 parser mode 的 body parser；不携带推测字段。
 * EN: Carries the typed routine language, body, object identity, and source provenance used by the language dispatcher to select the current parser-mode body parser, with no inferred fields.
 */
public record PostgresRoutineDescriptor(
        String declaredLanguage,
        PostgresRoutineBody body,
        String sourceObjectType,
        String sourceObjectName,
        String sourceObjectIdentity,
        Map<String, Object> provenance
) {
    public PostgresRoutineDescriptor(String declaredLanguage, PostgresRoutineBody body,
            String sourceObjectType, String sourceObjectName) {
        this(declaredLanguage, body, sourceObjectType, sourceObjectName, sourceObjectName, Map.of());
    }

    public PostgresRoutineDescriptor(String declaredLanguage, PostgresRoutineBody body,
            String sourceObjectType, String sourceObjectName, Map<String, Object> provenance) {
        this(declaredLanguage, body, sourceObjectType, sourceObjectName, sourceObjectName, provenance);
    }

    public PostgresRoutineDescriptor {
        declaredLanguage = declaredLanguage == null ? "" : declaredLanguage;
        body = body == null ? new UnsupportedRoutineBody(declaredLanguage, 1) : body;
        sourceObjectType = sourceObjectType == null ? "" : sourceObjectType;
        sourceObjectName = sourceObjectName == null ? "" : sourceObjectName;
        provenance = provenance == null ? Map.of() : Map.copyOf(provenance);
        Object inputObjectName = provenance.get("sourceObjectName");
        if (inputObjectName != null && !String.valueOf(inputObjectName).isBlank()) {
            sourceObjectName = String.valueOf(inputObjectName);
        }
        sourceObjectIdentity = sourceObjectIdentity == null || sourceObjectIdentity.isBlank()
                ? sourceObjectName : sourceObjectIdentity;
    }
}
