package com.relationdetector.oracle.fullgrammer.v12c;

import java.util.Set;

import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.core.fullgrammer.SqlGrammarProfile;
import com.relationdetector.oracle.fullgrammer.common.AbstractOracleFullGrammerDialectModule;

/** ServiceLoader entry for the oracle-12c full-grammer profile. */
public final class OracleFullGrammerDialectModule extends AbstractOracleFullGrammerDialectModule {
    private static final SqlGrammarProfile PROFILE = new SqlGrammarProfile(
            "oracle-12c",
            DatabaseType.ORACLE,
            12,
            2,
            Set.of("plsql", "identity_columns", "sql_json"));

    public OracleFullGrammerDialectModule() {
        super(PROFILE, new OracleFullGrammerBinding(), new OracleFullGrammerBinding());
    }
}
