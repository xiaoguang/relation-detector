package com.relationdetector.oracle.fullgrammer.v19c;

import java.util.Set;

import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.core.fullgrammer.SqlGrammarProfile;
import com.relationdetector.oracle.fullgrammer.common.AbstractOracleFullGrammerDialectModule;

/** ServiceLoader entry for the oracle-19c full-grammer profile. */
public final class OracleFullGrammerDialectModule extends AbstractOracleFullGrammerDialectModule {
    private static final SqlGrammarProfile PROFILE = new SqlGrammarProfile(
            "oracle-19c",
            DatabaseType.ORACLE,
            19,
            0,
            Set.of("plsql", "sql_json", "listagg_distinct"));

    public OracleFullGrammerDialectModule() {
        super(PROFILE);
    }
}
