package com.relationdetector.oracle.fullgrammar.v19c;

import java.util.Set;

import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.core.fullgrammar.SqlGrammarProfile;
import com.relationdetector.oracle.fullgrammar.common.AbstractFullGrammarDialectModule;

/**
 * CN: Oracle 19c full-grammar profile 的 ServiceLoader 入口，绑定 19c lexer/parser、version policy 和 typed adapters；不加载其它 Oracle 版本。
 * EN: ServiceLoader entry for the Oracle 19c full-grammar profile, binding only 19c lexer/parser classes, version policy, and typed adapters without loading another version.
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
