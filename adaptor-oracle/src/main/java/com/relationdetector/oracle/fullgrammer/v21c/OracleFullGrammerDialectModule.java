package com.relationdetector.oracle.fullgrammer.v21c;

import java.util.Set;

import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.core.fullgrammer.SqlGrammarProfile;
import com.relationdetector.oracle.fullgrammer.common.AbstractOracleFullGrammerDialectModule;

/** ServiceLoader entry for the oracle-21c full-grammer profile. */
public final class OracleFullGrammerDialectModule extends AbstractOracleFullGrammerDialectModule {
    private static final SqlGrammarProfile PROFILE = new SqlGrammarProfile(
            "oracle-21c",
            DatabaseType.ORACLE,
            21,
            0,
            Set.of("plsql", "sql_macros", "native_json"));

    public OracleFullGrammerDialectModule() {
        super(PROFILE);
    }
}
