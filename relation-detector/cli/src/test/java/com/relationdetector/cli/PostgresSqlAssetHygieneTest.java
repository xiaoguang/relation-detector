package com.relationdetector.cli;

import org.junit.jupiter.api.Test;

class PostgresSqlAssetHygieneTest {
    @Test
    void assetsDoNotContainCrossDialectResidue() throws Exception {
        DialectSqlAssetHygieneSupport.postgresSqlAssetsDoNotContainMysqlOrOracleDialectResidue();
    }

    @Test
    void proceduresDoNotReturnQueryResults() throws Exception {
        DialectSqlAssetHygieneSupport.postgresProceduresDoNotReturnQueryResults();
    }

    @Test
    void submitApprovalCallsPassOutVariables() throws Exception {
        DialectSqlAssetHygieneSupport.postgresSubmitApprovalCallsPassOutVariables();
    }

    @Test
    void setReturningFunctionsQualifyOutputNameCollisions() throws Exception {
        DialectSqlAssetHygieneSupport.postgresSetReturningFunctionsQualifyOutputNameCollisions();
    }
}
