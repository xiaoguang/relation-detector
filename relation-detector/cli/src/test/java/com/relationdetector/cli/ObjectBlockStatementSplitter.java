package com.relationdetector.cli;

import java.util.List;

import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.parse.ScriptFrameRequest;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.spi.DatabaseAdaptor;
import com.relationdetector.core.common.CommonDatabaseAdaptor;
import com.relationdetector.mysql.MySqlDatabaseAdaptor;
import com.relationdetector.oracle.OracleDatabaseAdaptor;
import com.relationdetector.postgres.PostgresDatabaseAdaptor;
import com.relationdetector.sqlserver.SqlServerDatabaseAdaptor;

/** Test facade over the same dialect script framer used by production scans. */
final class ObjectBlockStatementSplitter {
    private ObjectBlockStatementSplitter() {
    }

    static List<SqlStatementRecord> parse(
            String text,
            StatementSourceType sourceType,
            String sourceFile,
            DatabaseType databaseType
    ) {
        return parse(text, sourceType, sourceFile, databaseType, "");
    }

    static List<SqlStatementRecord> parse(
            String text,
            StatementSourceType sourceType,
            String sourceFile,
            DatabaseType databaseType,
            String objectSourceFilter
    ) {
        List<SqlStatementRecord> statements = adaptor(databaseType).parsers().scriptFramer()
                .frame(new ScriptFrameRequest(text, sourceFile, sourceType))
                .statements();
        String filter = objectSourceFilter == null ? "" : objectSourceFilter.strip();
        if (filter.isBlank()) {
            return statements;
        }
        List<SqlStatementRecord> selected = statements.stream()
                .filter(statement -> filter.equals(statement.sourceName())
                        || filter.equals(String.valueOf(statement.attributes().get("sourceBlockId"))))
                .toList();
        if (selected.isEmpty()) {
            throw new IllegalArgumentException(
                    "No relation-detector-fixture-source matched objectSourceFilter "
                            + filter + " in " + sourceFile);
        }
        return selected;
    }

    private static DatabaseAdaptor adaptor(DatabaseType databaseType) {
        return switch (databaseType) {
            case COMMON -> new CommonDatabaseAdaptor();
            case MYSQL -> new MySqlDatabaseAdaptor();
            case POSTGRESQL -> new PostgresDatabaseAdaptor();
            case ORACLE -> new OracleDatabaseAdaptor();
            case SQLSERVER -> new SqlServerDatabaseAdaptor();
        };
    }
}
