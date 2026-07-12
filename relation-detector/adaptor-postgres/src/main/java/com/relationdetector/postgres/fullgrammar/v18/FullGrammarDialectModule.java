package com.relationdetector.postgres.fullgrammar.v18;

import java.util.Set;

import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.core.fullgrammar.SqlGrammarProfile;
import com.relationdetector.postgres.fullgrammar.common.AbstractFullGrammarDialectModule;

/**
 * PostgreSQL 18 full-grammar module 注册入口。
 *
 * <p>CN: 版本由 package/profile 表达。该 module 只返回 v18 package 内的 parser，
 * 避免 18.x correctness 误用较低版本 full-grammar。
 *
 * <p>EN: PostgreSQL 18 full-grammar module entry point. The package/profile carry
 * the version boundary and return only parsers from the v18 package so 18.x
 * correctness cannot silently reuse a lower-version full-grammar implementation.
 */
public final class FullGrammarDialectModule extends AbstractFullGrammarDialectModule {
    private static final SqlGrammarProfile PROFILE = new SqlGrammarProfile(
            "postgresql-18",
            DatabaseType.POSTGRESQL,
            18,
            0,
            Set.of("returning_old_new", "virtual_generated_columns", "temporal_constraints",
                    "merge_returning", "json_table", "sql_json",
                    "materialized_cte", "lateral", "set_returning_functions"));

    public FullGrammarDialectModule() {
        this(new FullGrammarBinding());
    }

    private FullGrammarDialectModule(FullGrammarBinding binding) {
        super(PROFILE, binding, binding);
    }
}
