package com.relationdetector.oracle.fullgrammar.v26ai;

import java.util.Set;

import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.core.fullgrammar.SqlGrammarProfile;
import com.relationdetector.oracle.fullgrammar.common.AbstractFullGrammarDialectModule;

/**
 *
 * ServiceLoader entry for the oracle-26ai full-grammar profile.
 */
public final class FullGrammarDialectModule extends AbstractFullGrammarDialectModule {
    private static final SqlGrammarProfile PROFILE = new SqlGrammarProfile(
            "oracle-26ai",
            DatabaseType.ORACLE,
            26,
            0,
            Set.of("plsql", "vector", "ai"));

    public FullGrammarDialectModule() {
        super(PROFILE, new FullGrammarBinding(), new FullGrammarBinding());
    }
}
