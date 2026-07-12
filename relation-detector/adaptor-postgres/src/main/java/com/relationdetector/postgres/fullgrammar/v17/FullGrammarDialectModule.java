package com.relationdetector.postgres.fullgrammar.v17;

import java.util.Set;

import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.core.fullgrammar.SqlGrammarProfile;
import com.relationdetector.postgres.fullgrammar.common.AbstractFullGrammarDialectModule;

/**
 * PostgreSQL 17 full-grammar module 注册入口。
 *
 * <p>CN: 版本由 package/profile 表达。该 module 只返回 v17 package 内的 parser，
 * 避免 17.x correctness 误用 v16 full-grammar。
 *
 * <p>EN: PostgreSQL 17 full-grammar module entry point. The version is carried by
 * the package/profile; it returns only parsers from the v17 package so 17.x
 * correctness cannot silently reuse the v16 full-grammar implementation.
 */
public final class FullGrammarDialectModule extends AbstractFullGrammarDialectModule {
    private static final SqlGrammarProfile PROFILE = new SqlGrammarProfile(
            "postgresql-17",
            DatabaseType.POSTGRESQL,
            17,
            0,
            Set.of("merge", "merge_returning", "json_table", "sql_json",
                    "materialized_cte", "lateral", "set_returning_functions"));

    public FullGrammarDialectModule() {
        this(new FullGrammarBinding());
    }

    private FullGrammarDialectModule(FullGrammarBinding binding) {
        super(PROFILE, binding, binding);
    }
}
