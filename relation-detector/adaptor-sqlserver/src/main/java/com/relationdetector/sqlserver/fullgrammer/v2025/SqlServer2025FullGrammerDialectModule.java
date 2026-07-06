package com.relationdetector.sqlserver.fullgrammer.v2025;

import java.util.Set;

import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.core.fullgrammer.SqlGrammarProfile;
import com.relationdetector.sqlserver.fullgrammer.common.AbstractSqlServerFullGrammerDialectModule;

public final class SqlServer2025FullGrammerDialectModule extends AbstractSqlServerFullGrammerDialectModule {
    public SqlServer2025FullGrammerDialectModule() {
        super(new SqlGrammarProfile("sqlserver-2025", DatabaseType.SQLSERVER, 2025, 0,
                Set.of("compatibility-level-170", "tsql")), new SqlServerFullGrammerBinding());
    }
}
