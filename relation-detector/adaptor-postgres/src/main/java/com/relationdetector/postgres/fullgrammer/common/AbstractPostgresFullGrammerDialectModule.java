package com.relationdetector.postgres.fullgrammer.common;

import com.relationdetector.contracts.spi.Collectors.StructuredDdlParser;
import com.relationdetector.contracts.spi.Collectors.StructuredSqlParser;
import com.relationdetector.core.fullgrammer.FullGrammerDialectModule;
import com.relationdetector.core.fullgrammer.SqlGrammarProfile;

/**
 * Shared PostgreSQL full-grammer module implementation.
 *
 * <p>CN: v16/v17/v18 module 只声明 profile 与 version binding；SQL/DDL parser
 * 构造和 implementationName 样板逻辑集中在这里。
 *
 * <p>EN: Shared PostgreSQL full-grammer module implementation. Version modules
 * only declare their profile and generated binding while parser construction is
 * centralized here.
 */
public abstract class AbstractPostgresFullGrammerDialectModule implements FullGrammerDialectModule {
    private final SqlGrammarProfile profile;
    private final PostgresFullGrammerSqlBinding sqlBinding;
    private final PostgresFullGrammerDdlBinding ddlBinding;
    private final String implementationName;

    protected AbstractPostgresFullGrammerDialectModule(
            SqlGrammarProfile profile,
            PostgresFullGrammerSqlBinding sqlBinding,
            PostgresFullGrammerDdlBinding ddlBinding
    ) {
        this.profile = profile;
        this.sqlBinding = sqlBinding;
        this.ddlBinding = ddlBinding;
        this.implementationName = "POSTGRESQL_" + profile.majorVersion() + "_FULL_GRAMMER_PARSE_TREE_VISITOR";
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
        return new PostgresFullGrammerStructuredSqlParser(sqlBinding);
    }

    @Override
    public StructuredDdlParser structuredDdlParser() {
        return new PostgresFullGrammerStructuredDdlParser(ddlBinding);
    }
}
