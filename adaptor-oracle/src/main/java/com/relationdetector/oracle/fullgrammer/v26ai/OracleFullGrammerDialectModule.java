package com.relationdetector.oracle.fullgrammer.v26ai;

import java.util.Set;

import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.core.fullgrammer.SqlGrammarProfile;
import com.relationdetector.oracle.fullgrammer.common.AbstractOracleFullGrammerDialectModule;

/** ServiceLoader entry for the oracle-26ai full-grammer profile. */
public final class OracleFullGrammerDialectModule extends AbstractOracleFullGrammerDialectModule {
    private static final SqlGrammarProfile PROFILE = new SqlGrammarProfile(
            "oracle-26ai",
            DatabaseType.ORACLE,
            26,
            0,
            Set.of("plsql", "vector", "ai"));

    public OracleFullGrammerDialectModule() {
        super(PROFILE);
    }
}
