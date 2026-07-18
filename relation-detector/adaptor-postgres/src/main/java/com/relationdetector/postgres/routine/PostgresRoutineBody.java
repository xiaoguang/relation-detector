package com.relationdetector.postgres.routine;

/**
 * CN: PostgreSQL outer grammar 从 routine declaration 中选择的 sealed body 模型，区分 PL/pgSQL string、SQL string、SQL atomic 与 unsupported language；dispatcher 只按该类型路由，不扫描 raw text。
 * EN: Sealed body model selected by the PostgreSQL outer grammar, distinguishing PL/pgSQL strings, SQL strings, SQL atomic bodies, and unsupported languages. Dispatch uses this type without raw-text scanning.
 */
public sealed interface PostgresRoutineBody permits
        PlPgSqlStringBody, SqlStringBody, SqlAtomicBody, UnsupportedRoutineBody {
    int startLine();
}
