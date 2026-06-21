package com.relationdetector.mysql.tokenevent;

import com.relationdetector.core.parse.SqlDialect;
import com.relationdetector.core.tokenevent.TokenEventStructuredDdlParser;

/**
 * MySQL DDL token-event 入口。
 *
 * <p>CN: adaptor 暴露该类，让 SQL 和 DDL 都有明确的 token-event parser 对象。
 * MySQL DDL 语义仍由 TokenEventStructuredDdlParser 选择的 MySQL DDL event visitor 承接。
 *
 * <p>EN: MySQL DDL token-event entry point. Adaptors expose this class so SQL
 * and DDL both have explicit token-event parser objects; MySQL DDL semantics
 * remain inside the MySQL DDL event visitor selected by the base parser.
 */
public final class MySqlTokenEventStructuredDdlParser extends TokenEventStructuredDdlParser {
    public MySqlTokenEventStructuredDdlParser() {
        super(SqlDialect.MYSQL);
    }
}
