package com.relationdetector.postgres.routine;

import java.util.List;
import java.util.Set;

/** Typed PL/pgSQL shell output. Static SQL is parsed by the active SQL parser. */
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
