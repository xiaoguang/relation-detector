package com.relationdetector.semantic.evidence;

import com.relationdetector.semantic.ingest.ScanResultContractException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.relationdetector.semantic.event.SemanticEventCandidate;
import com.relationdetector.semantic.extraction.shard.SemanticShardingException;

final class SemanticEventContributionStoreTest {
    @TempDir
    java.nio.file.Path tempDir;

    @Test
    void externallyMergesMembersAndWeightedConfidenceBeforeBoundedMaterialization() {
        try (SemanticEventContributionStore store =
                     new SemanticEventContributionStore(tempDir.resolve("contributions"))) {
            SemanticEventCandidate first = event(
                    List.of("INSERT"),
                    List.of("shop.orders.customer_id"),
                    List.of("shop.audit_log.id"),
                    List.of("lineage:1"),
                    "0.8000",
                    1);
            store.append(first);
            store.append(first);
            store.append(event(
                    List.of("UPDATE", "INSERT"),
                    List.of("shop.customers.id"),
                    List.of("shop.audit_log.id"),
                    List.of("lineage:2", "lineage:1"),
                    "1.0000",
                    3));
            store.finish();

            SemanticEventCandidate merged =
                    store.materializeWithinBudget("event:write-audit", 0, 20_000);

            assertEquals(List.of("INSERT", "UPDATE"), merged.operationKinds());
            assertEquals(
                    List.of("shop.customers.id", "shop.orders.customer_id"),
                    merged.inputEndpoints());
            assertEquals(List.of("shop.audit_log.id"), merged.outputEndpoints());
            assertEquals(List.of("lineage:1", "lineage:2"), merged.lineageRefs());
            assertEquals(List.of("lineage:1", "lineage:2"), merged.evidenceRefs());
            assertEquals(new BigDecimal("0.9500"), merged.confidence());
            assertEquals(4, merged.attributes().get("directLineageCount"));
        }
    }

    @Test
    void rejectsHighFanoutBeforeMemberListsCanBeMaterialized() {
        try (SemanticEventContributionStore store =
                     new SemanticEventContributionStore(tempDir.resolve("high-fanout"))) {
            for (int index = 0; index < 5_000; index++) {
                store.append(event(
                        List.of("INSERT"),
                        List.of("shop.source_%05d.id".formatted(index)),
                        List.of("shop.audit_log.id"),
                        List.of("lineage:%05d".formatted(index)),
                        "0.9000",
                        1));
            }
            store.finish();

            assertThrows(
                    SemanticShardingException.class,
                    () -> store.materializeWithinBudget("event:write-audit", 0, 2_000));
        }
    }

    @Test
    void includesAssociationBytesInTheSameMaterializationGate() {
        try (SemanticEventContributionStore store =
                     new SemanticEventContributionStore(tempDir.resolve("association-budget"))) {
            store.append(event(
                    List.of("INSERT"),
                    List.of("shop.orders.customer_id"),
                    List.of("shop.audit_log.id"),
                    List.of("lineage:1"),
                    "0.9000",
                    1));
            store.finish();

            assertThrows(
                    SemanticShardingException.class,
                    () -> store.materializeWithinBudget(
                            "event:write-audit",
                            SemanticEventInputBudget.maximumSerializedBytes(2_000),
                            2_000));
        }
    }

    @Test
    void acceptsTheExactCombinedBudgetAndRejectsOneAdditionalByte() {
        int maxInputTokens = 2_000;
        long maximumBytes =
                SemanticEventInputBudget.maximumSerializedBytes(maxInputTokens);

        assertDoesNotThrow(() -> SemanticEventInputBudget.requireWithin(
                maximumBytes - 1,
                1,
                maxInputTokens));
        assertThrows(
                SemanticShardingException.class,
                () -> SemanticEventInputBudget.requireWithin(
                        maximumBytes - 1,
                        2,
                        maxInputTokens));
    }

    @Test
    void rejectsContributionsThatDisagreeOnTypedIdentity() {
        try (SemanticEventContributionStore store =
                     new SemanticEventContributionStore(tempDir.resolve("identity-conflict"))) {
            store.append(event(
                    List.of("INSERT"),
                    List.of("shop.orders.customer_id"),
                    List.of("shop.audit_log.id"),
                    List.of("lineage:1"),
                    "0.9000",
                    1));
            SemanticEventCandidate conflicting = new SemanticEventCandidate(
                    "event:write-audit",
                    "SQL_WRITE_OPERATION",
                    "ROUTINE",
                    "shop.write_other_audit",
                    "PROCEDURE",
                    "write_other_audit",
                    "procedures/write_other_audit.sql",
                    "statement:2",
                    "",
                    "",
                    "",
                    List.of("INSERT"),
                    List.of("shop.orders.customer_id"),
                    List.of("shop.audit_log.id"),
                    List.of("lineage:2"),
                    List.of(),
                    List.of(),
                    List.of("lineage:2"),
                    new BigDecimal("0.9000"),
                    Map.of("directLineageCount", 1));
            store.append(conflicting);

            assertThrows(ScanResultContractException.class, store::finish);
        }
    }

    private SemanticEventCandidate event(
            List<String> operations,
            List<String> inputs,
            List<String> outputs,
            List<String> lineageRefs,
            String confidence,
            int count
    ) {
        return new SemanticEventCandidate(
                "event:write-audit",
                "SQL_WRITE_OPERATION",
                "ROUTINE",
                "shop.write_audit",
                "PROCEDURE",
                "write_audit",
                "procedures/write_audit.sql",
                "statement:1",
                "",
                "",
                "",
                operations,
                inputs,
                outputs,
                lineageRefs,
                List.of(),
                List.of(),
                lineageRefs,
                new BigDecimal(confidence),
                Map.of("directLineageCount", count));
    }
}
