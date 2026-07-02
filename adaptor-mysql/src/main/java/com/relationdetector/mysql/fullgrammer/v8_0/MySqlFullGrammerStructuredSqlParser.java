package com.relationdetector.mysql.fullgrammer.v8_0;

import com.relationdetector.mysql.fullgrammer.common.AbstractMySqlFullGrammerStructuredSqlParser;

/**
 * MySQL 8.0 full-grammer SQL parser thin bridge.
 */
final class MySqlFullGrammerStructuredSqlParser extends AbstractMySqlFullGrammerStructuredSqlParser {
    MySqlFullGrammerStructuredSqlParser() {
        super(new MySqlFullGrammerVersionBinding());
    }
}
