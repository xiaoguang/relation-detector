package com.relationdetector.mysql.fullgrammar.v8_0;

import com.relationdetector.mysql.fullgrammar.common.MySqlFullGrammarExpressionAnalyzer;

/**
 * CN: 把 MySQL 8.0 MySqlParseTreeAdapter 注入共享 expression analyzer，保证 generated context 留在 v8_0 package；不调用 5.7 或 token-event parser。
 * EN: Injects the MySQL 8.0 parse-tree adapter into shared expression semantics, keeping generated contexts in the v8_0 package without delegating to 5.7 or token-event parsers.
 */
final class MySqlExpressionAnalyzer extends MySqlFullGrammarExpressionAnalyzer {
    MySqlExpressionAnalyzer() {
        super(new MySqlParseTreeAdapter());
    }
}
