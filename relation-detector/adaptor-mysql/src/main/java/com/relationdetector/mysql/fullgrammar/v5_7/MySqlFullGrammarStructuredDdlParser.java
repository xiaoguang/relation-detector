package com.relationdetector.mysql.fullgrammar.v5_7;

import com.relationdetector.mysql.fullgrammar.common.AbstractMySqlFullGrammarStructuredDdlParser;

/**
 * MySQL 5.7 full-grammar DDL parser thin bridge.
 */
final class MySqlFullGrammarStructuredDdlParser extends AbstractMySqlFullGrammarStructuredDdlParser {
    MySqlFullGrammarStructuredDdlParser() {
        super(new FullGrammarBinding());
    }
}
