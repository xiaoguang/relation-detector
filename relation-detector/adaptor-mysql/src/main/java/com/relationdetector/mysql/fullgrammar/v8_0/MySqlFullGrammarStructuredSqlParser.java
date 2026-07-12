package com.relationdetector.mysql.fullgrammar.v8_0;

import com.relationdetector.mysql.fullgrammar.common.AbstractMySqlFullGrammarStructuredSqlParser;

/**
 * MySQL 8.0 full-grammar SQL parser thin bridge.
 */
final class MySqlFullGrammarStructuredSqlParser extends AbstractMySqlFullGrammarStructuredSqlParser {
    MySqlFullGrammarStructuredSqlParser() {
        super(new FullGrammarBinding());
    }
}
