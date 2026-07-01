package com.relationdetector.oracle.fullgrammer.common;

import com.relationdetector.contracts.spi.Collectors.StructuredDdlParser;
import com.relationdetector.contracts.spi.Collectors.StructuredSqlParser;
import com.relationdetector.core.fullgrammer.FullGrammerDialectModule;
import com.relationdetector.core.fullgrammer.SqlGrammarProfile;

/**
 * Shared Oracle full-grammer module implementation.
 *
 * <p>CN: version package 负责 profile，公共层负责 SQL/DDL parser 构造与
 * implementationName。这样 12c/19c/21c/26ai 的 ServiceLoader 入口形态和
 * PostgreSQL v16/v17/v18 保持一致。
 *
 * <p>EN: Shared Oracle full-grammer module implementation. Version packages
 * own profiles while this class centralizes parser construction.
 */
public abstract class AbstractOracleFullGrammerDialectModule implements FullGrammerDialectModule {
    private final SqlGrammarProfile profile;
    private final OracleFullGrammerSqlBinding sqlBinding;
    private final OracleFullGrammerDdlBinding ddlBinding;

    protected AbstractOracleFullGrammerDialectModule(
            SqlGrammarProfile profile,
            OracleFullGrammerSqlBinding sqlBinding,
            OracleFullGrammerDdlBinding ddlBinding
    ) {
        this.profile = profile;
        this.sqlBinding = sqlBinding;
        this.ddlBinding = ddlBinding;
    }

    @Override
    public SqlGrammarProfile profile() {
        return profile;
    }

    @Override
    public String implementationName() {
        return "ORACLE_" + profile.majorVersion() + "_FULL_GRAMMER_TYPED_VISITOR";
    }

    @Override
    public StructuredSqlParser sqlParser() {
        return new OracleFullGrammerStructuredSqlParser(profile, sqlBinding);
    }

    @Override
    public StructuredDdlParser structuredDdlParser() {
        return new OracleFullGrammerStructuredDdlParser(profile, ddlBinding, sqlBinding);
    }
}
