package com.relationdetector.core.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.metadata.MetadataColumnFact;
import com.relationdetector.contracts.model.ColumnRef;
import com.relationdetector.contracts.model.Endpoint;
import com.relationdetector.contracts.model.TableId;

class CanonicalIdentifierResolverTest {
    private final CanonicalIdentifierResolver resolver =
            new CanonicalIdentifierResolver(value -> value == null ? "" : value.toLowerCase());

    @Test
    void appliesExplicitCurrentSchemaWithoutOverwritingExplicitSchema() {
        NamespaceContext namespace = new NamespaceContext("erp", "shop", List.of());

        assertEquals("erp|shop|orders", resolver.tableKey(TableId.of(null, "orders"), namespace));
        assertEquals("erp|archive|orders", resolver.tableKey(TableId.of("archive", "orders"), namespace));
    }

    @Test
    void leavesUnqualifiedTableUnqualifiedWhenNamespaceIsAmbiguous() {
        NamespaceContext namespace = new NamespaceContext("erp", "", List.of("shop", "archive"));

        assertEquals("erp||orders", resolver.tableKey(TableId.of(null, "orders"), namespace));
    }

    @Test
    void preservesExplicitCatalogSchemaAndQuotedDots() {
        TableId qualified = resolver.resolveQualified("erp.archive.orders", NamespaceContext.empty());
        assertEquals("erp", qualified.catalog());
        assertEquals("archive", qualified.schema());
        assertEquals("orders", qualified.tableName());

        CanonicalIdentifierResolver quotedResolver = new CanonicalIdentifierResolver(value -> {
            if (value == null) return "";
            return value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")
                    ? value.substring(1, value.length() - 1)
                    : value.toLowerCase();
        });
        TableId quoted = quotedResolver.resolveQualified("\"erp.data\".\"sales.ops\".\"Order.Items\"",
                NamespaceContext.empty());
        assertEquals("erp.data", quoted.catalog());
        assertEquals("sales.ops", quoted.schema());
        assertEquals("Order.Items", quoted.tableName());
    }

    @Test
    void keepsSqlSpellingInEndpointWhileCanonicalKeyUsesDialectRules() {
        CanonicalIdentifierResolver upperCaseResolver = new CanonicalIdentifierResolver(value ->
                value == null ? "" : value.toUpperCase(java.util.Locale.ROOT));
        TableId table = upperCaseResolver.resolveQualified("sales.orders", NamespaceContext.empty());

        assertEquals("sales.orders", table.displayName());
        assertEquals("sales.orders", table.normalizedName());
        assertEquals(
                new CanonicalEndpointKey("", "SALES", "ORDERS", "CUSTOMER_ID"),
                CanonicalEndpointKey.from(
                        Endpoint.column(ColumnRef.of(table, "customer_id")),
                        upperCaseResolver,
                        NamespaceContext.empty()));
    }

    @Test
    void metadataCatalogParticipatesInCanonicalEndpointIdentity() {
        MetadataColumnFact column = new MetadataColumnFact(
                "erp", "sales", "orders", "customer_id",
                "bigint", "bigint", false, null, "", null, 1);

        assertEquals(
                new CanonicalEndpointKey("erp", "sales", "orders", "customer_id"),
                CanonicalEndpointKey.from(column, resolver, NamespaceContext.empty()));
    }

    @Test
    void aliasSymbolTableResolvesExactAliasOnlyWithinVisibleScope() {
        AliasSymbolTable aliases = new AliasSymbolTable(resolver, NamespaceContext.empty());
        aliases.bind("o", TableId.of("shop", "orders"));
        aliases.pushScope();
        aliases.bind("o", TableId.of("archive", "orders"));

        assertEquals("archive.orders", aliases.resolve("o").orElseThrow().displayName());
        aliases.popScope();
        assertEquals("shop.orders", aliases.resolve("o").orElseThrow().displayName());
        assertTrue(aliases.resolve("orders").isEmpty());
    }
}
