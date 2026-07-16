package com.relationdetector.mysql.fullgrammar.v8_0;

import com.relationdetector.mysql.fullgrammar.common.AbstractMySqlFullGrammarStructuredDdlParser;

/**
 *
 * MySQL 8.0 full-grammar DDL parser thin bridge.
 */
final class MySqlFullGrammarStructuredDdlParser extends AbstractMySqlFullGrammarStructuredDdlParser {
    MySqlFullGrammarStructuredDdlParser() {
        super(new FullGrammarBinding());
    }
}
