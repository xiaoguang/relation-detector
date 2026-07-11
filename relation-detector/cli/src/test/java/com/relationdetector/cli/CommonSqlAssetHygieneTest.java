package com.relationdetector.cli;

import org.junit.jupiter.api.Test;

class CommonSqlAssetHygieneTest {
    @Test void naturalAssetsExcludeParserCoverageBodies() throws Exception { DialectSqlAssetHygieneSupport.commonNaturalSampleDataDoesNotContainParserCoverageBodies(); }
    @Test void cliUsesTheNaturalCommonRoot() throws Exception { DialectSqlAssetHygieneSupport.sampleDataParserCliRunsCommonNaturalRoot(); }
    @Test void semanticEquivalentRetainsTheRelationProbeBenchmark() throws Exception { DialectSqlAssetHygieneSupport.semanticEquivalentContainsRelationProbeBenchmark(); }
}
