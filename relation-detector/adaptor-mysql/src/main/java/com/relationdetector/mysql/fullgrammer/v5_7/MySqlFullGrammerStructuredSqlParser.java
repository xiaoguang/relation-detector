package com.relationdetector.mysql.fullgrammer.v5_7;

import com.relationdetector.mysql.fullgrammer.common.AbstractMySqlFullGrammerStructuredSqlParser;

/**
 * MySQL 5.7 full-grammer SQL parser thin bridge.
 */
final class MySqlFullGrammerStructuredSqlParser extends AbstractMySqlFullGrammerStructuredSqlParser {
    MySqlFullGrammerStructuredSqlParser() {
        super(new MySqlFullGrammerVersionBinding());
    }
}
