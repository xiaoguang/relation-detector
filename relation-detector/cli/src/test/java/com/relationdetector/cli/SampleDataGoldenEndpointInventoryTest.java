package com.relationdetector.cli;

import org.junit.jupiter.api.Test;

class SampleDataGoldenEndpointInventoryTest {
    @Test void rootFallbackUsesStrictVersionDdlInventory() throws Exception { SampleDataSchemaConsistencySupport.rootFallbackOutputsAreValidatedAgainstStrictVersionDdlInventory(); }
    @Test void goldenEndpointsExistInDialectDdlInventory() throws Exception { SampleDataSchemaConsistencySupport.sampleDataGoldenEndpointsExistInTheirDialectDdlInventory(); }
}
