package com.relationdetector.cli;

import org.junit.jupiter.api.Test;

class SqlServerSqlAssetHygieneTest {
    @Test void assetsDoNotContainCrossDialectResidue() throws Exception { DialectSqlAssetHygieneSupport.sqlServerSqlAssetsDoNotContainOtherDialectResidue(); }
    @Test void assetsUseCanonicalSchemaQualifiedReferences() throws Exception { DialectSqlAssetHygieneSupport.sqlServerSqlAssetsUseCanonicalSchemaQualifiedTableReferences(); }
    @Test void allSampleDataFilesAreCorrectnessCovered() throws Exception { DialectSqlAssetHygieneSupport.sqlServerSampleDataFilesAreCoveredByRootAndVersionedCorrectnessFixtures(); }
    @Test void sampleDataKeepsComparableErpDensity() throws Exception { DialectSqlAssetHygieneSupport.sqlServerSampleDataKeepsComparableErpSemanticDensity(); }
    @Test void deepScenarioIncludesBusinessFlowProcedures() throws Exception { DialectSqlAssetHygieneSupport.sqlServerDeepScenarioIncludesInventoryMrpAndCostFlowProcedures(); }
    @Test void sampleDataHasNoRelationProbeTemplates() throws Exception { DialectSqlAssetHygieneSupport.sqlServerSampleDataDoesNotCarryRelationProbeBenchmarkTemplates(); }
    @Test void dataFilesHaveDistinctBusinessContents() throws Exception { DialectSqlAssetHygieneSupport.sqlServerDataFilesHaveDistinctBusinessContentsWithinEachVersion(); }
    @Test void proceduresHaveNoNumberedProbeResidue() throws Exception { DialectSqlAssetHygieneSupport.sqlServerSampleDataDoesNotUseNumberedRelationProbeProcedures(); }
    @Test void naturalKpiAndTriggerAssetsAreConsistent() throws Exception { DialectSqlAssetHygieneSupport.sqlServerNaturalAssetsUseRoleBasedKpiJoinAndSetBasedTriggers(); }
}
