package com.relationdetector.mysql.fullgrammer.v5_7;

import com.relationdetector.mysql.fullgrammer.common.MySqlFullGrammerExpressionAnalyzer;

/** MySQL 5.7 generated-context bridge for shared expression semantics. */
final class MySqlExpressionAnalyzer extends MySqlFullGrammerExpressionAnalyzer {
    MySqlExpressionAnalyzer() {
        super(new MySql57ParseTreeAdapter());
    }
}
