package com.relationdetector.sqlserver.fullgrammar.v2017;

import java.util.Set;

import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.core.fullgrammar.SqlGrammarProfile;
import com.relationdetector.sqlserver.fullgrammar.common.AbstractSqlServerFullGrammarDialectModule;

public final class FullGrammarDialectModule extends AbstractSqlServerFullGrammarDialectModule {
    public FullGrammarDialectModule() {
        super(new SqlGrammarProfile("sqlserver-2017", DatabaseType.SQLSERVER, 2017, 0,
                Set.of("compatibility-level-140", "tsql")), new FullGrammarBinding());
    }
}
