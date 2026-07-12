package com.relationdetector.postgres.fullgrammar.v16;

import java.util.Set;

import com.relationdetector.core.fullgrammar.SqlGrammarProfile;
import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.postgres.fullgrammar.common.AbstractFullGrammarDialectModule;

/**
 * PostgreSQL 16 full-grammar module 注册入口。
 *
 * <p>CN: 通过 ServiceLoader 暴露 postgresql-16 profile 及其 SQL/DDL parser。core
 * 只看 FullGrammarDialectModule 接口，不直接依赖本类。
 *
 * <p>EN: PostgreSQL 16 full-grammar module entry point registered through
 * ServiceLoader. It exposes the postgresql-16 profile and SQL/DDL parsers while
 * core depends only on FullGrammarDialectModule.
 */
public final class FullGrammarDialectModule extends AbstractFullGrammarDialectModule {
    private static final SqlGrammarProfile PROFILE = new SqlGrammarProfile(
            "postgresql-16",
            DatabaseType.POSTGRESQL,
            16,
            0,
            Set.of("merge", "materialized_cte", "lateral", "set_returning_functions"));

    public FullGrammarDialectModule() {
        this(new FullGrammarBinding());
    }

    private FullGrammarDialectModule(FullGrammarBinding binding) {
        super(PROFILE, binding, binding);
    }
}
