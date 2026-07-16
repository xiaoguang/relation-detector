package com.relationdetector.core.scan;

import java.util.List;

import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.parse.SqlStatementRecord;

/**
 *
 * Routes script-framed statements without inspecting SQL text.
 */
final class StatementDispatchService {
    DdlFileDispatch dispatchDdlFile(List<SqlStatementRecord> statements, DatabaseType databaseType) {
        List<SqlStatementRecord> safe = statements == null ? List.of() : List.copyOf(statements);
        List<SqlStatementRecord> queries = safe.stream()
                .filter(statement -> hasSqlBody(statement, databaseType))
                .toList();
        return new DdlFileDispatch(safe, queries);
    }

    private boolean hasSqlBody(SqlStatementRecord statement, DatabaseType databaseType) {
        return statement.sourceType() == StatementSourceType.VIEW
                || statement.sourceType() == StatementSourceType.MATERIALIZED_VIEW
                || (statement.sourceType() == StatementSourceType.TRIGGER
                && (databaseType == DatabaseType.ORACLE || databaseType == DatabaseType.SQLSERVER));
    }

    record DdlFileDispatch(
            List<SqlStatementRecord> ddlStatements,
            List<SqlStatementRecord> queryStatements
    ) {
        DdlFileDispatch {
            ddlStatements = List.copyOf(ddlStatements);
            queryStatements = List.copyOf(queryStatements);
        }
    }
}
