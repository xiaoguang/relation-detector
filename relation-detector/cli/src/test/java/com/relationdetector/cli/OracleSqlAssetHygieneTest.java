package com.relationdetector.cli;

import org.junit.jupiter.api.Test;

class OracleSqlAssetHygieneTest {
    @Test
    void assetsDoNotContainCrossDialectResidue() throws Exception {
        DialectSqlAssetHygieneSupport.oracleSqlAssetsDoNotContainPostgresOrMysqlDialectResidue();
    }
}
