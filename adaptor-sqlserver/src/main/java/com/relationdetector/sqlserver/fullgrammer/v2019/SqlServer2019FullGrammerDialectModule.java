package com.relationdetector.sqlserver.fullgrammer.v2019;

import java.util.Set;

import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.core.fullgrammer.SqlGrammarProfile;
import com.relationdetector.sqlserver.fullgrammer.common.AbstractSqlServerFullGrammerDialectModule;

public final class SqlServer2019FullGrammerDialectModule extends AbstractSqlServerFullGrammerDialectModule {
    public SqlServer2019FullGrammerDialectModule() {
        super(new SqlGrammarProfile("sqlserver-2019", DatabaseType.SQLSERVER, 2019, 0,
                Set.of("compatibility-level-150", "tsql")), new SqlServerFullGrammerBinding());
    }
}
