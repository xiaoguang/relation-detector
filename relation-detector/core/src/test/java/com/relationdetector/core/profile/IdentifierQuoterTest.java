package com.relationdetector.core.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.model.ColumnRef;
import com.relationdetector.contracts.model.TableId;

class IdentifierQuoterTest {
    @Test
    void rendersCatalogSchemaTableAndColumnFromTypedComponents() {
        TableId table = new TableId("sales-db", "order schema", "order", "order schema.order");
        ColumnRef column = ColumnRef.of(table, "customer id");

        IdentifierQuoter quoter = IdentifierQuoter.sqlServer();

        assertEquals("[sales-db].[order schema].[order]", quoter.table(table));
        assertEquals("[customer id]", quoter.column(column));
    }

    @Test
    void doesNotInterpretDotsInsideOneTypedIdentifierComponent() {
        TableId table = new TableId(null, "tenant.one", "orders", "tenant.one.orders");

        assertEquals("\"tenant.one\".\"orders\"", IdentifierQuoter.doubleQuote().table(table));
    }

    @Test
    void quotesEveryTypedIdentifierComponentEvenWhenLexicallySafe() {
        TableId table = new TableId("erp", "sales", "orders", "sales.orders");
        ColumnRef column = ColumnRef.of(table, "customer_id");

        assertEquals("`erp`.`orders`", IdentifierQuoter.mysql().table(table),
                "MySQL database identity occupies the catalog axis; schema is not executable qualification");
        assertEquals("`customer_id`", IdentifierQuoter.mysql().column(column));
    }
}
