package com.relationdetector.postgres.routine;

import java.util.List;
import java.util.Set;

/**
 * CN: PL/pgSQL shell parser 的 typed 输出，包含 symbols、静态 SQL boundaries 和 unsupported diagnostics；它不复制 PostgreSQL SQL 语义，静态片段由当前 mode parser 处理。
 * EN: Typed output of the PL/pgSQL shell parser containing symbols, static-SQL boundaries, and unsupported diagnostics. It does not duplicate PostgreSQL SQL semantics; the active mode parses static fragments.
 */
public record PlPgSqlBodyStructure(
        List<StaticSqlStatement> staticStatements,
        List<Integer> dynamicSqlLines,
        List<Integer> unsupportedLines,
        Set<String> localIdentifiers,
        int statementCount
) {
    public PlPgSqlBodyStructure {
        staticStatements = staticStatements == null ? List.of() : List.copyOf(staticStatements);
        dynamicSqlLines = dynamicSqlLines == null ? List.of() : List.copyOf(dynamicSqlLines);
        unsupportedLines = unsupportedLines == null ? List.of() : List.copyOf(unsupportedLines);
        localIdentifiers = localIdentifiers == null ? Set.of() : Set.copyOf(localIdentifiers);
        statementCount = Math.max(0, statementCount);
    }

    public record StaticSqlStatement(String sql, int startLine, int endLine) {
        public StaticSqlStatement {
            sql = sql == null ? "" : sql;
            startLine = Math.max(1, startLine);
            endLine = Math.max(startLine, endLine);
        }
    }
}
