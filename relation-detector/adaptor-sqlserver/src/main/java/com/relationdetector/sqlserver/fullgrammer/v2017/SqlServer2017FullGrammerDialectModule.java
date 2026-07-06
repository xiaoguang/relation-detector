package com.relationdetector.sqlserver.fullgrammer.v2017;

import java.util.Set;

import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.core.fullgrammer.SqlGrammarProfile;
import com.relationdetector.sqlserver.fullgrammer.common.AbstractSqlServerFullGrammerDialectModule;

public final class SqlServer2017FullGrammerDialectModule extends AbstractSqlServerFullGrammerDialectModule {
    public SqlServer2017FullGrammerDialectModule() {
        super(new SqlGrammarProfile("sqlserver-2017", DatabaseType.SQLSERVER, 2017, 0,
                Set.of("compatibility-level-140", "tsql")), new SqlServerFullGrammerBinding());
    }
}
