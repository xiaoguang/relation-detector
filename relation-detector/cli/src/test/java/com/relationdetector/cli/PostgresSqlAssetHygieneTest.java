package com.relationdetector.cli;

import org.junit.jupiter.api.Test;

class PostgresSqlAssetHygieneTest {
    @Test
    void assetsDoNotContainCrossDialectResidue() throws Exception {
        DialectSqlAssetHygieneSupport.postgresSqlAssetsDoNotContainMysqlOrOracleDialectResidue();
    }
}
