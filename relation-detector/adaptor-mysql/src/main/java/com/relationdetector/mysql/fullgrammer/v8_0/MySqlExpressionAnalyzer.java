package com.relationdetector.mysql.fullgrammer.v8_0;

import com.relationdetector.mysql.fullgrammer.common.MySqlFullGrammerExpressionAnalyzer;

/** MySQL 8.0 generated-context bridge for shared expression semantics. */
final class MySqlExpressionAnalyzer extends MySqlFullGrammerExpressionAnalyzer {
    MySqlExpressionAnalyzer() {
        super(new MySql80ParseTreeAdapter());
    }
}
