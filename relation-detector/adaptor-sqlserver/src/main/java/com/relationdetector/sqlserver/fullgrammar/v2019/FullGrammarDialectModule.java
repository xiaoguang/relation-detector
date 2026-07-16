package com.relationdetector.sqlserver.fullgrammar.v2019;

import java.util.Set;

import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.core.fullgrammar.SqlGrammarProfile;
import com.relationdetector.sqlserver.fullgrammar.common.AbstractSqlServerFullGrammarDialectModule;

/** CN: 注册 SQL Server 2019 full-grammar profile 与版本 binding。 EN: Registers the SQL Server 2019 full-grammar profile and version binding. */
public final class FullGrammarDialectModule extends AbstractSqlServerFullGrammarDialectModule {
    public FullGrammarDialectModule() {
        super(new SqlGrammarProfile("sqlserver-2019", DatabaseType.SQLSERVER, 2019, 0,
                Set.of("compatibility-level-150", "tsql")), new FullGrammarBinding());
    }
}
