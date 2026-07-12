package com.relationdetector.postgres.fullgrammar.common;

import com.relationdetector.contracts.spi.Collectors.StructuredDdlParser;
import com.relationdetector.contracts.spi.Collectors.StructuredSqlParser;
import com.relationdetector.core.fullgrammar.FullGrammarDialectModule;
import com.relationdetector.core.fullgrammar.SqlGrammarProfile;

/**
 * Shared PostgreSQL full-grammar module implementation.
 *
 * <p>CN: v16/v17/v18 module 只声明 profile 与 version binding；SQL/DDL parser
 * 构造和 implementationName 样板逻辑集中在这里。
 *
 * <p>EN: Shared PostgreSQL full-grammar module implementation. Version modules
 * only declare their profile and generated binding while parser construction is
 * centralized here.
 */
public abstract class AbstractFullGrammarDialectModule implements FullGrammarDialectModule {
    private final SqlGrammarProfile profile;
    private final PostgresFullGrammarSqlBinding sqlBinding;
    private final PostgresFullGrammarDdlBinding ddlBinding;
    private final String implementationName;

    protected AbstractFullGrammarDialectModule(
            SqlGrammarProfile profile,
            PostgresFullGrammarSqlBinding sqlBinding,
            PostgresFullGrammarDdlBinding ddlBinding
    ) {
        this.profile = profile;
        this.sqlBinding = sqlBinding;
        this.ddlBinding = ddlBinding;
        this.implementationName = "POSTGRESQL_" + profile.majorVersion() + "_FULL_GRAMMAR_PARSE_TREE_VISITOR";
    }

    @Override
    public SqlGrammarProfile profile() {
        return profile;
    }

    @Override
    public String implementationName() {
        return implementationName;
    }

    @Override
    public StructuredSqlParser sqlParser() {
        return new PostgresFullGrammarStructuredSqlParser(sqlBinding);
    }

    @Override
    public StructuredDdlParser structuredDdlParser() {
        return new PostgresFullGrammarStructuredDdlParser(ddlBinding);
    }
}
