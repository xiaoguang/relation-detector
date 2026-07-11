package com.relationdetector.cli;

import org.junit.jupiter.api.Test;

class CommonNaturalTypedParserAcceptanceTest {
    @Test void allStatementsParseWithoutSkippedWarnings() throws Exception { SampleDataSchemaConsistencySupport.allCommonNaturalStatementsUseTypedParsersWithoutSkippedWarnings(); }
    @Test void receiptProcessHasBalancedMappingsAndNaturalEndpoints() throws Exception { SampleDataSchemaConsistencySupport.receiptAwarePaymentProcessHasBalancedTypedMappingsAndNaturalEndpoints(); }
    @Test void identityDdlParsesWithoutDiagnostics() throws Exception { SampleDataSchemaConsistencySupport.commonNaturalIdentityDdlIsTypedWithoutDiagnostics(); }
}
