package com.relationdetector.core.scan;

import java.util.List;

import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.parse.SqlStatementRecord;

/** Routes script-framed statements without inspecting SQL text. */
final class StatementDispatchService {
    DdlFileDispatch dispatchDdlFile(List<SqlStatementRecord> statements) {
        List<SqlStatementRecord> safe = statements == null ? List.of() : List.copyOf(statements);
        List<SqlStatementRecord> queries = safe.stream()
                .filter(this::isView)
                .toList();
        return new DdlFileDispatch(safe, queries);
    }

    private boolean isView(SqlStatementRecord statement) {
        return statement.sourceType() == StatementSourceType.VIEW
                || statement.sourceType() == StatementSourceType.MATERIALIZED_VIEW;
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
