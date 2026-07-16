package com.relationdetector.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;

import org.junit.jupiter.api.Test;

class FixtureFingerprintNormalizerTest {
    @Test
    void stripsConfiguredNamespaceUsingIdentifierCaseSemantics() {
        assertEquals(
                "naming:orders.customer_id->customers.id:TABLE_ID:NAMING_MATCH",
                FixtureFingerprintNormalizer.preferExpectedNamespaceForm(
                        "naming:ORACLE.ORDERS.customer_id->ORACLE.CUSTOMERS.id:TABLE_ID:NAMING_MATCH",
                        "oracle",
                        Set.of("naming:orders.customer_id->customers.id:TABLE_ID:NAMING_MATCH")));
    }

    @Test
    void preservesNamespaceWhenTheGoldenExplicitlyRequiresIt() {
        String fingerprint = "FK_LIKE:dbo.orders.customer_id->dbo.customers.id:SQL_LOG_JOIN";
        assertEquals(fingerprint, FixtureFingerprintNormalizer.preferExpectedNamespaceForm(
                fingerprint, "dbo", Set.of(fingerprint)));
    }

    @Test
    void preservesTheGoldenFormWhenOnlyOneEndpointIsExplicitlyQualified() {
        assertEquals(
                "FK_LIKE:invoices.order_id->public.orders.id:SQL_LOG_JOIN,NAMING_MATCH",
                FixtureFingerprintNormalizer.preferExpectedNamespaceForm(
                        "FK_LIKE:public.invoices.order_id->public.orders.id:SQL_LOG_JOIN,NAMING_MATCH",
                        "public",
                        Set.of("FK_LIKE:invoices.order_id->public.orders.id:SQL_LOG_JOIN,NAMING_MATCH")));
    }

    @Test
    void doesNotChooseArbitrarilyBetweenAmbiguousGoldenForms() {
        assertEquals(
                "FK_LIKE:invoices.order_id->orders.id:SQL_LOG_JOIN",
                FixtureFingerprintNormalizer.preferExpectedNamespaceForm(
                        "FK_LIKE:public.invoices.order_id->public.orders.id:SQL_LOG_JOIN",
                        "public",
                        Set.of(
                                "FK_LIKE:invoices.order_id->public.orders.id:SQL_LOG_JOIN",
                                "FK_LIKE:public.invoices.order_id->orders.id:SQL_LOG_JOIN")));
    }

    @Test
    void doesNotRemoveNamespaceTextInsideAnIdentifierOrCatalogQualifiedEndpoint() {
        String actual = "FK_LIKE:public_archive.orders.customer_id->catalog.public.customers.id:SQL_LOG_JOIN";
        assertEquals(
                actual,
                FixtureFingerprintNormalizer.preferExpectedNamespaceForm(
                        actual,
                        "public",
                        Set.of("FK_LIKE:public_archive.orders.customer_id->catalog.customers.id:SQL_LOG_JOIN")));
    }

    @Test
    void cliComparisonPreservesExplicitGoldenFormBeforeNamespaceNormalization() {
        String explicit = "FK_LIKE:public.orders.customer_id->public.customers.id:SQL_LOG_JOIN";
        assertEquals(explicit, FixtureFingerprintNormalizer.preferExpectedNamespaceForm(
                explicit, "public", Set.of(explicit)));
    }
}
