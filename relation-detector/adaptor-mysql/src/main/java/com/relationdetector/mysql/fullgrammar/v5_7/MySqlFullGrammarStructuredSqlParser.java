package com.relationdetector.mysql.fullgrammar.v5_7;

import com.relationdetector.mysql.fullgrammar.common.AbstractMySqlFullGrammarStructuredSqlParser;

/**
 * MySQL 5.7 full-grammar SQL parser thin bridge.
 */
final class MySqlFullGrammarStructuredSqlParser extends AbstractMySqlFullGrammarStructuredSqlParser {
    MySqlFullGrammarStructuredSqlParser() {
        super(new FullGrammarBinding());
    }
}
