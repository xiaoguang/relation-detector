package com.relationdetector.postgres.routine;

/**
 * CN: 记录 outer grammar 已识别但当前不解析的 routine language 和行号；dispatcher 只产生精确 diagnostic，不扫描 body 或生成物理事实。
 * EN: Records a typed routine language that is unsupported by the current parser plus its source line. Dispatch emits a precise diagnostic without scanning the body or creating physical facts.
 */
public record UnsupportedRoutineBody(String declaredLanguage, int startLine) implements PostgresRoutineBody {
    public UnsupportedRoutineBody {
        declaredLanguage = declaredLanguage == null ? "" : declaredLanguage;
        startLine = Math.max(1, startLine);
    }
}
