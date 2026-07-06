package com.relationdetector.mysql.fullgrammer.v5_7;

import com.relationdetector.mysql.fullgrammer.common.AbstractMySqlFullGrammerStructuredDdlParser;

/**
 * MySQL 5.7 full-grammer DDL parser thin bridge.
 */
final class MySqlFullGrammerStructuredDdlParser extends AbstractMySqlFullGrammerStructuredDdlParser {
    MySqlFullGrammerStructuredDdlParser() {
        super(new MySqlFullGrammerVersionBinding());
    }
}
