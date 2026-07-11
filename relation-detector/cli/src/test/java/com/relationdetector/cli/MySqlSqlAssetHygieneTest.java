package com.relationdetector.cli;

import org.junit.jupiter.api.Test;

class MySqlSqlAssetHygieneTest {
    @Test
    void assetsDoNotContainCrossDialectResidue() throws Exception {
        DialectSqlAssetHygieneSupport.mysqlSqlAssetsDoNotContainPostgresOrOracleDialectResidue();
    }

    @Test
    void mysql57AssetsDoNotContainMysql80Syntax() throws Exception {
        DialectSqlAssetHygieneSupport.mysql57SampleDataDoesNotContainMysql80OnlySyntax();
    }
}
