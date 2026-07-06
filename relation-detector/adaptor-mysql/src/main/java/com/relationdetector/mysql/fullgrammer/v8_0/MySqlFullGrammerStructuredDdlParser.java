package com.relationdetector.mysql.fullgrammer.v8_0;

import com.relationdetector.mysql.fullgrammer.common.AbstractMySqlFullGrammerStructuredDdlParser;

/**
 * MySQL 8.0 full-grammer DDL parser thin bridge.
 */
final class MySqlFullGrammerStructuredDdlParser extends AbstractMySqlFullGrammerStructuredDdlParser {
    MySqlFullGrammerStructuredDdlParser() {
        super(new MySqlFullGrammerVersionBinding());
    }
}
