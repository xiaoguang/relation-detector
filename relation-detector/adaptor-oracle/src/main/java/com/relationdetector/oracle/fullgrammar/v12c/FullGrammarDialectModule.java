package com.relationdetector.oracle.fullgrammar.v12c;

import java.util.Set;

import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.core.fullgrammar.SqlGrammarProfile;
import com.relationdetector.oracle.fullgrammar.common.AbstractFullGrammarDialectModule;

/**
 *
 * ServiceLoader entry for the oracle-12c full-grammar profile.
 */
public final class FullGrammarDialectModule extends AbstractFullGrammarDialectModule {
    private static final SqlGrammarProfile PROFILE = new SqlGrammarProfile(
            "oracle-12c",
            DatabaseType.ORACLE,
            12,
            2,
            Set.of("plsql", "identity_columns", "sql_json"));

    public FullGrammarDialectModule() {
        super(PROFILE, new FullGrammarBinding(), new FullGrammarBinding());
    }
}
