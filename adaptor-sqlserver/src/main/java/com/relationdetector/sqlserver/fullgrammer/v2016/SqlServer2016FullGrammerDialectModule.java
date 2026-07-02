package com.relationdetector.sqlserver.fullgrammer.v2016;

import java.util.Set;

import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.core.fullgrammer.SqlGrammarProfile;
import com.relationdetector.sqlserver.fullgrammer.common.AbstractSqlServerFullGrammerDialectModule;

public final class SqlServer2016FullGrammerDialectModule extends AbstractSqlServerFullGrammerDialectModule {
    public SqlServer2016FullGrammerDialectModule() {
        super(new SqlGrammarProfile("sqlserver-2016", DatabaseType.SQLSERVER, 2016, 0,
                Set.of("compatibility-level-130", "tsql")), new SqlServerFullGrammerBinding());
    }
}
