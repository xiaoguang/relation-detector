package com.relationdetector.sqlserver.fullgrammar.v2016;

import java.util.Set;

import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.core.fullgrammar.SqlGrammarProfile;
import com.relationdetector.sqlserver.fullgrammar.common.AbstractSqlServerFullGrammarDialectModule;

/** CN: 注册 SQL Server 2016 compatibility-level 130 full-grammar profile。 EN: Registers the SQL Server 2016 compatibility-level 130 full-grammar profile. */
public final class FullGrammarDialectModule extends AbstractSqlServerFullGrammarDialectModule {
    public FullGrammarDialectModule() {
        super(new SqlGrammarProfile("sqlserver-2016", DatabaseType.SQLSERVER, 2016, 0,
                Set.of("compatibility-level-130", "tsql")), new FullGrammarBinding());
    }
}
