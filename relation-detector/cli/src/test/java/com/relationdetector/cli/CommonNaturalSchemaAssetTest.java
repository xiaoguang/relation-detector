package com.relationdetector.cli;

import org.junit.jupiter.api.Test;

class CommonNaturalSchemaAssetTest {
    @Test void declaresEachPhysicalTableOnce() throws Exception { SampleDataSchemaConsistencySupport.commonNaturalDeclaresEachPhysicalTableOnce(); }
    @Test void declaresEachRoutineOnce() throws Exception { SampleDataSchemaConsistencySupport.commonNaturalDeclaresEachRoutineNameOnce(); }
    @Test void usesByDefaultIdentityForSurrogateKeys() throws Exception { SampleDataSchemaConsistencySupport.commonNaturalSurrogateKeysUseByDefaultIdentity(); }
    @Test void acceptsBusinessReceiptDiscriminators() throws Exception { SampleDataSchemaConsistencySupport.commonNaturalPaymentReceiptDiscriminatorsAcceptBusinessCodes(); }
    @Test void retainsCanonicalPaymentColumns() throws Exception { SampleDataSchemaConsistencySupport.commonNaturalPaymentsKeepsCanonicalConstraintsAndTypes(); }
    @Test void writesDoNotCopyBusinessKeysIntoGeneratedIds() throws Exception { SampleDataSchemaConsistencySupport.commonNaturalWritesDoNotCopyBusinessKeysIntoGeneratedIds(); }
}
