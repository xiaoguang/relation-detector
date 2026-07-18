package com.relationdetector.core.scan;

import java.util.List;

import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.parse.SqlStatementRecord;

/**
 * CN: 根据 Script Framer 给出的 typed source type 将 statements 分发到 DDL 与 query-body 链路，不检查 raw SQL text。
 * EN: Routes framed statements to DDL and query-body paths from typed source metadata without inspecting raw SQL text.
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
