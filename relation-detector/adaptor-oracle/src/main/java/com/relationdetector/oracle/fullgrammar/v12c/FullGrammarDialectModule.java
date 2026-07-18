package com.relationdetector.oracle.fullgrammar.v12c;

import java.util.Set;

import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.core.fullgrammar.SqlGrammarProfile;
import com.relationdetector.oracle.fullgrammar.common.AbstractFullGrammarDialectModule;

/**
 * CN: Oracle 12c full-grammar profile 的 ServiceLoader 入口，绑定 12c lexer/parser、version policy 和 typed adapters；不加载其它 Oracle 版本。
 * EN: ServiceLoader entry for the Oracle 12c full-grammar profile, binding only 12c lexer/parser classes, version policy, and typed adapters without loading another version.
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
