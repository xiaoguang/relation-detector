package com.relationdetector.core.lineage;

import com.relationdetector.core.parser.*;
import com.relationdetector.core.relation.*;

import com.relationdetector.core.tokenevent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

class SqlLineageResolverTest {
    @Test
    void resolvesAliasedColumnsThroughMultipleCteLayers() {
        String sql = """
                WITH recent_orders AS (
                  SELECT o.id, o.customer_id, o.region_id
                  FROM orders AS o
                ),
                regional_orders AS (
                  SELECT ro.id, ro.customer_id, r.id AS region_id
                  FROM recent_orders AS ro
                  JOIN regions AS r ON ro.region_id = r.id
                ),
                customer_orders AS (
                  SELECT regional_orders.id, c.account_id
                  FROM regional_orders
                  JOIN customers AS c ON regional_orders.customer_id = c.id
                )
                SELECT co.id, a.id
                FROM customer_orders AS co
                JOIN accounts AS a ON co.account_id = a.id
                """;

        SqlLineageResolver resolver = SqlLineageResolver.analyze(sql,
                Set.of("recent_orders", "regional_orders", "customer_orders"));

        var accountId = resolver.resolve("co", "account_id");
        assertTrue(accountId.isPresent());
        assertEquals("customers.account_id", accountId.get().displayName());
    }

    @Test
    void cteNameWinsOverInnerPhysicalAliasWithSameName() {
        String sql = """
                WITH "a" AS (
                  SELECT o.id AS order_id, o.customer_id
                  FROM "public"."orders" o
                  JOIN "public"."customers" c ON o.customer_id = c.id
                ),
                b AS (
                  SELECT a.order_id, c.region_id
                  FROM "a" a
                  JOIN "public"."customers" c ON a.customer_id = c.id
                ),
                c AS (
                  SELECT b.order_id, b.region_id
                  FROM b
                )
                SELECT *
                FROM c
                JOIN invoices i ON i.order_id = c.order_id
                """;

        SqlLineageResolver resolver = SqlLineageResolver.analyze(sql, Set.of("a", "b", "c"));

        var orderId = resolver.resolve("c", "order_id");
        assertTrue(orderId.isPresent(), () -> "known rowsets=" + knownRowsets(resolver));
        assertEquals("public.orders.id", orderId.get().displayName());
    }

    @Test
    void resolvesCteAliasIntroducedByCommaRowset() {
        String sql = """
                WITH user_financial_snapshot AS (
                  SELECT t.user_id
                  FROM transaction_ledgers t
                ),
                dormant_risk_scores AS (
                  SELECT u.id AS user_id
                  FROM users u, user_financial_snapshot snap
                  WHERE u.id = snap.user_id
                )
                SELECT drs.user_id
                FROM dormant_risk_scores drs
                """;

        SqlLineageResolver resolver = SqlLineageResolver.analyze(sql,
                Set.of("user_financial_snapshot", "dormant_risk_scores"));

        var snapUserId = resolver.resolve("snap", "user_id");
        assertTrue(snapUserId.isPresent(), () -> "known rowsets=" + knownRowsets(resolver));
        assertEquals("transaction_ledgers.user_id", snapUserId.get().displayName());
    }

    private static Object knownRowsets(SqlLineageResolver resolver) {
        try {
            var field = SqlLineageResolver.class.getDeclaredField("rowsets");
            field.setAccessible(true);
            return field.get(resolver);
        } catch (ReflectiveOperationException e) {
            return e.getClass().getSimpleName();
        }
    }
}
