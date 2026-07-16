package com.relationdetector.core.identity;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.metadata.MetadataColumnFact;
import com.relationdetector.contracts.model.ColumnRef;
import com.relationdetector.contracts.model.Endpoint;
import com.relationdetector.contracts.model.TableId;
import com.relationdetector.contracts.spi.IdentifierRules;

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
    void preservesPostgresQuotedCaseInTheCanonicalTableKey() {
        CanonicalIdentifierResolver postgres = new CanonicalIdentifierResolver(postgresRules());

        assertNotEquals(
                postgres.tableKey(postgres.resolveQualified("\"Orders\"", NamespaceContext.empty()),
                        NamespaceContext.empty()),
                postgres.tableKey(postgres.resolveQualified("orders", NamespaceContext.empty()),
                        NamespaceContext.empty()));
    }

    @Test
    void preservesOracleQuotedCaseInTheCanonicalTableKey() {
        CanonicalIdentifierResolver oracle = new CanonicalIdentifierResolver(oracleRules());

        assertNotEquals(
                oracle.tableKey(oracle.resolveQualified("\"Orders\"", NamespaceContext.empty()),
                        NamespaceContext.empty()),
                oracle.tableKey(oracle.resolveQualified("ORDERS", NamespaceContext.empty()),
                        NamespaceContext.empty()));
    }

    @Test
    void retainsSqlServerBracketedDotsAsSingleQualifiedComponents() {
        CanonicalIdentifierResolver sqlServer = new CanonicalIdentifierResolver(sqlServerRules());

        TableId table = sqlServer.resolveQualified("[db.part].[sales.part].[orders.part]", NamespaceContext.empty());

        assertEquals("db.part", table.catalog());
        assertEquals("sales.part", table.schema());
        assertEquals("orders.part", table.tableName());
    }

    @Test
    void derivesCanonicalTableComponentFromStructureInsteadOfCallerNormalizedName() {
        TableId forged = new TableId("erp", "sales", "orders", "sales.invoices");

        assertEquals("erp|sales|orders", resolver.tableKey(forged, NamespaceContext.empty()));
    }

    @Test
    void separatesStructuralIdentityFromDialectCanonicalIdentity() {
        CanonicalIdentifierResolver oracle = new CanonicalIdentifierResolver(oracleRules());
        TableId lowerDisplay = new TableId(null, "sales", "orders", "SALES.ORDERS");
        TableId upperDisplay = new TableId(null, "SALES", "ORDERS", "SALES.ORDERS");

        assertAll(
                () -> assertFalse(lowerDisplay.sameIdentity(upperDisplay),
                        "TableId has no dialect rules and therefore enforces exact structural spelling"),
                () -> assertEquals(
                        oracle.tableKey(lowerDisplay, NamespaceContext.empty()),
                        oracle.tableKey(upperDisplay, NamespaceContext.empty()),
                        "dialect-canonical comparisons belong to CanonicalIdentifierResolver"));
    }

    @Test
    void keepsSqlSpellingInEndpointWhileCanonicalKeyUsesDialectRules() {
        CanonicalIdentifierResolver upperCaseResolver = new CanonicalIdentifierResolver(value ->
                value == null ? "" : value.toUpperCase(java.util.Locale.ROOT));
        TableId table = upperCaseResolver.resolveQualified("sales.orders", NamespaceContext.empty());

        assertEquals("sales.orders", table.displayName());
        assertEquals("SALES.ORDERS", table.normalizedName());
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

    private static IdentifierRules postgresRules() {
        return new IdentifierRules() {
            @Override
            public String normalize(String identifier) {
                if (identifier == null) return "";
                String value = identifier.strip();
                return value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")
                        ? value.substring(1, value.length() - 1)
                        : value.toLowerCase(java.util.Locale.ROOT);
            }
        };
    }

    private static IdentifierRules oracleRules() {
        return new IdentifierRules() {
            @Override
            public String normalize(String identifier) {
                if (identifier == null) return "";
                String value = identifier.strip();
                return value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")
                        ? value.substring(1, value.length() - 1)
                        : value.toUpperCase(java.util.Locale.ROOT);
            }
        };
    }

    private static IdentifierRules sqlServerRules() {
        return new IdentifierRules() {
            @Override
            public String normalize(String identifier) {
                if (identifier == null) return "";
                String value = identifier.strip();
                return value.length() >= 2 && value.startsWith("[") && value.endsWith("]")
                        ? value.substring(1, value.length() - 1).toLowerCase(java.util.Locale.ROOT)
                        : value.toLowerCase(java.util.Locale.ROOT);
            }
        };
    }
}
