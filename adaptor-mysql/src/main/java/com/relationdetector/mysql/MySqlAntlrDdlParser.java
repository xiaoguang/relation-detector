package com.relationdetector.mysql;

import com.relationdetector.core.AntlrStructuredDdlParser;
import com.relationdetector.core.SqlDialect;

/**
 * MySQL dialect selection for structured DDL shadow parsing.
 *
 * <p>Relationship-producing DDL parsing still uses {@link MySqlDdlParser};
 * this ANTLR entry point exists so DDL can enter the same shadow diagnostics
 * pipeline as SQL while the dedicated DDL visitor matures.
 */
public final class MySqlAntlrDdlParser extends AntlrStructuredDdlParser {
    public MySqlAntlrDdlParser() {
        super(SqlDialect.MYSQL);
    }
}
