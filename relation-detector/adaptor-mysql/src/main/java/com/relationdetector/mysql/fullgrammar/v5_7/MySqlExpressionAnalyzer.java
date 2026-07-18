package com.relationdetector.mysql.fullgrammar.v5_7;

import com.relationdetector.mysql.fullgrammar.common.MySqlFullGrammarExpressionAnalyzer;

/**
 * CN: 把 MySQL 5.7 MySqlParseTreeAdapter 注入共享 expression analyzer，保证 generated context 留在 v5_7 package；不调用 8.0 或 token-event parser。
 * EN: Injects the MySQL 5.7 parse-tree adapter into shared expression semantics, keeping generated contexts in the v5_7 package without delegating to 8.0 or token-event parsers.
 */
final class MySqlExpressionAnalyzer extends MySqlFullGrammarExpressionAnalyzer {
    MySqlExpressionAnalyzer() {
        super(new MySqlParseTreeAdapter());
    }
}
