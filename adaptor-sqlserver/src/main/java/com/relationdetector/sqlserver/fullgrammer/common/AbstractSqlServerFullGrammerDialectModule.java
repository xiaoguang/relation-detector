package com.relationdetector.sqlserver.fullgrammer.common;

import com.relationdetector.contracts.spi.Collectors.StructuredDdlParser;
import com.relationdetector.contracts.spi.Collectors.StructuredSqlParser;
import com.relationdetector.core.fullgrammer.FullGrammerDialectModule;
import com.relationdetector.core.fullgrammer.SqlGrammarProfile;

public abstract class AbstractSqlServerFullGrammerDialectModule implements FullGrammerDialectModule {
    private final SqlGrammarProfile profile;
    private final SqlServerFullGrammerSqlBinding binding;

    protected AbstractSqlServerFullGrammerDialectModule(
            SqlGrammarProfile profile,
            SqlServerFullGrammerSqlBinding binding
    ) {
        this.profile = profile;
        this.binding = binding;
    }

    @Override
    public SqlGrammarProfile profile() {
        return profile;
    }

    @Override
    public String implementationName() {
        return "SQLSERVER_FULL_GRAMMER_" + profile.id();
    }

    @Override
    public StructuredSqlParser sqlParser() {
        return new SqlServerFullGrammerStructuredSqlParser(profile, binding);
    }

    @Override
    public StructuredDdlParser structuredDdlParser() {
        return new SqlServerFullGrammerStructuredDdlParser(profile, binding);
    }
}
