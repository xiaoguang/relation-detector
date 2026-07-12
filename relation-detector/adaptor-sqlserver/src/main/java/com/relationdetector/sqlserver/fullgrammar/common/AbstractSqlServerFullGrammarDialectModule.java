package com.relationdetector.sqlserver.fullgrammar.common;

import com.relationdetector.contracts.spi.Collectors.StructuredDdlParser;
import com.relationdetector.contracts.spi.Collectors.StructuredSqlParser;
import com.relationdetector.core.fullgrammar.FullGrammarDialectModule;
import com.relationdetector.core.fullgrammar.SqlGrammarProfile;

public abstract class AbstractSqlServerFullGrammarDialectModule implements FullGrammarDialectModule {
    private final SqlGrammarProfile profile;
    private final SqlServerFullGrammarSqlBinding binding;

    protected AbstractSqlServerFullGrammarDialectModule(
            SqlGrammarProfile profile,
            SqlServerFullGrammarSqlBinding binding
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
        return "SQLSERVER_FULL_GRAMMAR_" + profile.id();
    }

    @Override
    public StructuredSqlParser sqlParser() {
        return new SqlServerFullGrammarStructuredSqlParser(profile, binding);
    }

    @Override
    public StructuredDdlParser structuredDdlParser() {
        return new SqlServerFullGrammarStructuredDdlParser(profile, binding);
    }
}
