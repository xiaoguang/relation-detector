package com.relationdetector.sqlserver.fullgrammer.v2022;

import java.util.Set;

import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.core.fullgrammer.SqlGrammarProfile;
import com.relationdetector.sqlserver.fullgrammer.common.AbstractSqlServerFullGrammerDialectModule;

public final class SqlServer2022FullGrammerDialectModule extends AbstractSqlServerFullGrammerDialectModule {
    public SqlServer2022FullGrammerDialectModule() {
        super(new SqlGrammarProfile("sqlserver-2022", DatabaseType.SQLSERVER, 2022, 0,
                Set.of("compatibility-level-160", "tsql")), new SqlServerFullGrammerBinding());
    }
}
