package com.relationdetector.oracle.fullgrammar.v19c;

import java.util.Set;

import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.core.fullgrammar.SqlGrammarProfile;
import com.relationdetector.oracle.fullgrammar.common.AbstractFullGrammarDialectModule;

/**
 *
 * ServiceLoader entry for the oracle-19c full-grammar profile.
 */
public final class FullGrammarDialectModule extends AbstractFullGrammarDialectModule {
    private static final SqlGrammarProfile PROFILE = new SqlGrammarProfile(
            "oracle-19c",
            DatabaseType.ORACLE,
            19,
            0,
            Set.of("plsql", "sql_json", "listagg_distinct"));

    public FullGrammarDialectModule() {
        super(PROFILE, new FullGrammarBinding(), new FullGrammarBinding());
    }
}
