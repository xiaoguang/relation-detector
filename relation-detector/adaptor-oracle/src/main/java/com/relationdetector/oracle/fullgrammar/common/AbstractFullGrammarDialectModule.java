package com.relationdetector.oracle.fullgrammar.common;

import com.relationdetector.contracts.spi.Collectors.StructuredDdlParser;
import com.relationdetector.contracts.spi.Collectors.StructuredSqlParser;
import com.relationdetector.core.fullgrammar.FullGrammarDialectModule;
import com.relationdetector.core.fullgrammar.SqlGrammarProfile;

/**
 * Shared Oracle full-grammar module implementation.
 *
 * <p>CN: version package 负责 profile，公共层负责 SQL/DDL parser 构造与
 * implementationName。这样 12c/19c/21c/26ai 的 ServiceLoader 入口形态和
 * PostgreSQL v16/v17/v18 保持一致。
 *
 * <p>EN: Shared Oracle full-grammar module implementation. Version packages
 * own profiles while this class centralizes parser construction.
 */
public abstract class AbstractFullGrammarDialectModule implements FullGrammarDialectModule {
    private final SqlGrammarProfile profile;
    private final OracleFullGrammarSqlBinding sqlBinding;
    private final OracleFullGrammarDdlBinding ddlBinding;

    protected AbstractFullGrammarDialectModule(
            SqlGrammarProfile profile,
            OracleFullGrammarSqlBinding sqlBinding,
            OracleFullGrammarDdlBinding ddlBinding
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
        return "ORACLE_" + profile.majorVersion() + "_FULL_GRAMMAR_TYPED_VISITOR";
    }

    @Override
    public StructuredSqlParser sqlParser() {
        return new OracleFullGrammarStructuredSqlParser(profile, sqlBinding);
    }

    @Override
    public StructuredDdlParser structuredDdlParser() {
        return new OracleFullGrammarStructuredDdlParser(profile, ddlBinding, sqlBinding);
    }
}
