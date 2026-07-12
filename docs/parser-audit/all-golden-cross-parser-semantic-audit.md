# All Golden Cross-Parser Semantic Audit

This generated audit compares mirrored correctness golden files across parser categories. It is a review aid, not a replacement for SQL semantic judgment. Differences are classified so implementation work can focus on typed visitor gaps rather than restoring scanner-era guessing.

## Summary

| Classification | Groups | Full/Right extra relations | Root/Left extra relations | Full/Right extra lineage | Root/Left extra lineage |
| --- | ---: | ---: | ---: | ---: | ---: |
| `TYPED_VISITOR_GAP` | 154 | 1150 | 106 | 98 | 6 |
| `TYPED_VISITOR_GAP_ROUTINE_OR_GENERATOR` | 55 | 606 | 0 | 192 | 7 |
| `VERSION_DELTA` | 1 | 0 | 0 | 9 | 0 |
| `VERSION_ONLY_SYNTAX` | 2 | 2 | 0 | 22 | 0 |

## High-Value Typed Visitor Gaps

| Fixture | Pair | Missing relation | Missing lineage | Classification | Notes |
| --- | --- | ---: | ---: | --- | --- |
| `sample-data-full-01-schema-01-tables-ddl` | PostgreSQL token-event root -> PostgreSQL full-grammar v18 | 87 | 0 | `TYPED_VISITOR_GAP` | root token-event has less typed structural coverage than full-grammar |
| `sample-data-full-01-schema-01-tables-ddl` | PostgreSQL token-event root -> PostgreSQL full-grammar v17 | 87 | 0 | `TYPED_VISITOR_GAP` | root token-event has less typed structural coverage than full-grammar |
| `sample-data-full-01-schema-01-tables-ddl` | PostgreSQL token-event root -> PostgreSQL full-grammar v16 | 87 | 0 | `TYPED_VISITOR_GAP` | root token-event has less typed structural coverage than full-grammar |
| `basic-correctness-case-01-ddl` | PostgreSQL token-event root -> PostgreSQL full-grammar v18 | 56 | 0 | `TYPED_VISITOR_GAP` | root token-event has less typed structural coverage than full-grammar |
| `basic-correctness-case-01-ddl` | PostgreSQL token-event root -> PostgreSQL full-grammar v17 | 56 | 0 | `TYPED_VISITOR_GAP` | root token-event has less typed structural coverage than full-grammar |
| `basic-correctness-case-01-ddl` | PostgreSQL token-event root -> PostgreSQL full-grammar v16 | 56 | 0 | `TYPED_VISITOR_GAP` | root token-event has less typed structural coverage than full-grammar |
| `sample-data-real-world-scenarios-sql` | PostgreSQL token-event root -> PostgreSQL full-grammar v18 | 53 | 0 | `TYPED_VISITOR_GAP` | root token-event has less typed structural coverage than full-grammar |
| `sample-data-real-world-scenarios-sql` | PostgreSQL token-event root -> PostgreSQL full-grammar v17 | 53 | 0 | `TYPED_VISITOR_GAP` | root token-event has less typed structural coverage than full-grammar |
| `sample-data-real-world-scenarios-sql` | PostgreSQL token-event root -> PostgreSQL full-grammar v16 | 53 | 0 | `TYPED_VISITOR_GAP` | root token-event has less typed structural coverage than full-grammar |
| `sample-data-real-world-scenarios-sql` | MySQL token-event root -> MySQL full-grammar v8_0 | 53 | 0 | `TYPED_VISITOR_GAP` | root token-event has less typed structural coverage than full-grammar |
| `sample-data-full-03-data-02-supplementary-data-sql` | MySQL token-event root -> MySQL full-grammar v8_0 | 4 | 49 | `TYPED_VISITOR_GAP_ROUTINE_OR_GENERATOR` | routine/data-generator/trigger body or complex typed structural coverage |
| `sample-data-full-01-schema-01-tables-ddl` | MySQL token-event root -> MySQL full-grammar v8_0 | 47 | 0 | `TYPED_VISITOR_GAP` | root token-event has less typed structural coverage than full-grammar |
| `sample-data-full-03-data-03-third-batch-data-sql` | MySQL token-event root -> MySQL full-grammar v8_0 | 2 | 33 | `TYPED_VISITOR_GAP_ROUTINE_OR_GENERATOR` | routine/data-generator/trigger body or complex typed structural coverage |
| `basic-correctness-case-01-procedure-sp-fill-biz-bill-item-fact-new-with-progress-sql` | MySQL token-event root -> MySQL full-grammar v8_0 | 7 | 22 | `TYPED_VISITOR_GAP_ROUTINE_OR_GENERATOR` | routine/data-generator/trigger body or complex typed structural coverage |
| `sample-data-full-04-queries-01-complex-queries-sql` | PostgreSQL token-event root -> PostgreSQL full-grammar v18 | 28 | 0 | `TYPED_VISITOR_GAP_ROUTINE_OR_GENERATOR` | routine/data-generator/trigger body or complex typed structural coverage |
| `sample-data-full-04-queries-01-complex-queries-sql` | PostgreSQL token-event root -> PostgreSQL full-grammar v17 | 28 | 0 | `TYPED_VISITOR_GAP_ROUTINE_OR_GENERATOR` | routine/data-generator/trigger body or complex typed structural coverage |
| `sample-data-full-04-queries-01-complex-queries-sql` | PostgreSQL token-event root -> PostgreSQL full-grammar v16 | 28 | 0 | `TYPED_VISITOR_GAP_ROUTINE_OR_GENERATOR` | routine/data-generator/trigger body or complex typed structural coverage |
| `sample-data-full-04-queries-01-complex-queries-sql` | MySQL token-event root -> MySQL full-grammar v8_0 | 28 | 0 | `TYPED_VISITOR_GAP_ROUTINE_OR_GENERATOR` | routine/data-generator/trigger body or complex typed structural coverage |
| `sample-data-full-03-data-05-massive-data-generator-sql` | MySQL token-event root -> MySQL full-grammar v8_0 | 1 | 24 | `TYPED_VISITOR_GAP_ROUTINE_OR_GENERATOR` | routine/data-generator/trigger body or complex typed structural coverage |
| `sample-data-full-04-queries-08-common-system-queries-sql` | PostgreSQL token-event root -> PostgreSQL full-grammar v18 | 24 | 0 | `TYPED_VISITOR_GAP_ROUTINE_OR_GENERATOR` | routine/data-generator/trigger body or complex typed structural coverage |
| `sample-data-full-04-queries-08-common-system-queries-sql` | PostgreSQL token-event root -> PostgreSQL full-grammar v17 | 24 | 0 | `TYPED_VISITOR_GAP_ROUTINE_OR_GENERATOR` | routine/data-generator/trigger body or complex typed structural coverage |
| `sample-data-full-04-queries-08-common-system-queries-sql` | PostgreSQL token-event root -> PostgreSQL full-grammar v16 | 24 | 0 | `TYPED_VISITOR_GAP_ROUTINE_OR_GENERATOR` | routine/data-generator/trigger body or complex typed structural coverage |
| `sample-data-full-04-queries-08-common-system-queries-sql` | MySQL token-event root -> MySQL full-grammar v8_0 | 24 | 0 | `TYPED_VISITOR_GAP_ROUTINE_OR_GENERATOR` | routine/data-generator/trigger body or complex typed structural coverage |
| `sample-data-full-03-data-04-return-damage-data-sql` | MySQL token-event root -> MySQL full-grammar v8_0 | 2 | 20 | `TYPED_VISITOR_GAP_ROUTINE_OR_GENERATOR` | routine/data-generator/trigger body or complex typed structural coverage |
| `sample-data-full-04-queries-02-complex-queries-batch2-sql` | MySQL token-event root -> MySQL full-grammar v8_0 | 20 | 0 | `TYPED_VISITOR_GAP_ROUTINE_OR_GENERATOR` | routine/data-generator/trigger body or complex typed structural coverage |
| `sample-data-full-04-queries-03-complex-queries-batch3-sql` | PostgreSQL token-event root -> PostgreSQL full-grammar v18 | 19 | 0 | `TYPED_VISITOR_GAP_ROUTINE_OR_GENERATOR` | routine/data-generator/trigger body or complex typed structural coverage |
| `sample-data-full-04-queries-03-complex-queries-batch3-sql` | PostgreSQL token-event root -> PostgreSQL full-grammar v17 | 19 | 0 | `TYPED_VISITOR_GAP_ROUTINE_OR_GENERATOR` | routine/data-generator/trigger body or complex typed structural coverage |
| `sample-data-full-04-queries-03-complex-queries-batch3-sql` | PostgreSQL token-event root -> PostgreSQL full-grammar v16 | 19 | 0 | `TYPED_VISITOR_GAP_ROUTINE_OR_GENERATOR` | routine/data-generator/trigger body or complex typed structural coverage |
| `sample-data-full-04-queries-03-complex-queries-batch3-sql` | MySQL token-event root -> MySQL full-grammar v8_0 | 19 | 0 | `TYPED_VISITOR_GAP_ROUTINE_OR_GENERATOR` | routine/data-generator/trigger body or complex typed structural coverage |
| `sample-data-full-04-queries-02-complex-queries-batch2-sql` | PostgreSQL token-event root -> PostgreSQL full-grammar v18 | 18 | 0 | `TYPED_VISITOR_GAP_ROUTINE_OR_GENERATOR` | routine/data-generator/trigger body or complex typed structural coverage |
| `sample-data-full-04-queries-02-complex-queries-batch2-sql` | PostgreSQL token-event root -> PostgreSQL full-grammar v17 | 18 | 0 | `TYPED_VISITOR_GAP_ROUTINE_OR_GENERATOR` | routine/data-generator/trigger body or complex typed structural coverage |
| `sample-data-full-04-queries-02-complex-queries-batch2-sql` | PostgreSQL token-event root -> PostgreSQL full-grammar v16 | 18 | 0 | `TYPED_VISITOR_GAP_ROUTINE_OR_GENERATOR` | routine/data-generator/trigger body or complex typed structural coverage |
| `pg15-sql` | PostgreSQL token-event root -> PostgreSQL full-grammar v18 | 2 | 16 | `TYPED_VISITOR_GAP` | root token-event has less typed structural coverage than full-grammar |
| `pg15-sql` | PostgreSQL token-event root -> PostgreSQL full-grammar v17 | 2 | 16 | `TYPED_VISITOR_GAP` | root token-event has less typed structural coverage than full-grammar |
| `sample-data-full-02-procedures-09-return-refund-procedures-sql` | MySQL token-event root -> MySQL full-grammar v8_0 | 15 | 0 | `TYPED_VISITOR_GAP_ROUTINE_OR_GENERATOR` | routine/data-generator/trigger body or complex typed structural coverage |
| `sample-data-full-04-queries-06-return-damage-queries-sql` | PostgreSQL token-event root -> PostgreSQL full-grammar v18 | 14 | 0 | `TYPED_VISITOR_GAP_ROUTINE_OR_GENERATOR` | routine/data-generator/trigger body or complex typed structural coverage |
| `sample-data-full-04-queries-06-return-damage-queries-sql` | PostgreSQL token-event root -> PostgreSQL full-grammar v17 | 14 | 0 | `TYPED_VISITOR_GAP_ROUTINE_OR_GENERATOR` | routine/data-generator/trigger body or complex typed structural coverage |
| `sample-data-full-04-queries-06-return-damage-queries-sql` | PostgreSQL token-event root -> PostgreSQL full-grammar v16 | 14 | 0 | `TYPED_VISITOR_GAP_ROUTINE_OR_GENERATOR` | routine/data-generator/trigger body or complex typed structural coverage |
| `sample-data-full-04-queries-06-return-damage-queries-sql` | MySQL token-event root -> MySQL full-grammar v8_0 | 14 | 0 | `TYPED_VISITOR_GAP_ROUTINE_OR_GENERATOR` | routine/data-generator/trigger body or complex typed structural coverage |
| `sample-data-full-02-procedures-11-common-system-procedures-sql` | MySQL token-event root -> MySQL full-grammar v8_0 | 14 | 0 | `TYPED_VISITOR_GAP_ROUTINE_OR_GENERATOR` | routine/data-generator/trigger body or complex typed structural coverage |
| `generated-comprehensive-query-sql` | PostgreSQL token-event root -> PostgreSQL full-grammar v18 | 14 | 0 | `TYPED_VISITOR_GAP` | root token-event has less typed structural coverage than full-grammar |
| `generated-comprehensive-query-sql` | PostgreSQL token-event root -> PostgreSQL full-grammar v17 | 14 | 0 | `TYPED_VISITOR_GAP` | root token-event has less typed structural coverage than full-grammar |
| `generated-comprehensive-query-sql` | PostgreSQL token-event root -> PostgreSQL full-grammar v16 | 14 | 0 | `TYPED_VISITOR_GAP` | root token-event has less typed structural coverage than full-grammar |
| `basic-correctness-case-01-procedure-proc-generate-purchase-inbound-from-order-sql` | MySQL token-event root -> MySQL full-grammar v8_0 | 3 | 10 | `TYPED_VISITOR_GAP_ROUTINE_OR_GENERATOR` | routine/data-generator/trigger body or complex typed structural coverage |
| `sample-data-full-02-procedures-10-supplier-geo-procedures-sql` | MySQL token-event root -> MySQL full-grammar v8_0 | 7 | 5 | `TYPED_VISITOR_GAP_ROUTINE_OR_GENERATOR` | routine/data-generator/trigger body or complex typed structural coverage |
| `sample-data-full-01-schema-03-triggers-sql` | MySQL token-event root -> MySQL full-grammar v8_0 | 6 | 6 | `TYPED_VISITOR_GAP_ROUTINE_OR_GENERATOR` | routine/data-generator/trigger body or complex typed structural coverage |
| `pg17-sql` | PostgreSQL token-event root -> PostgreSQL full-grammar v18 | 1 | 11 | `VERSION_ONLY_SYNTAX` | expected major-version syntax/capability difference |
| `pg17-sql` | PostgreSQL token-event root -> PostgreSQL full-grammar v17 | 1 | 11 | `VERSION_ONLY_SYNTAX` | expected major-version syntax/capability difference |
| `sample-data-full-02-procedures-02-procedures-supplement-sql` | MySQL token-event root -> MySQL full-grammar v8_0 | 11 | 0 | `TYPED_VISITOR_GAP_ROUTINE_OR_GENERATOR` | routine/data-generator/trigger body or complex typed structural coverage |
| `sample-data-full-04-queries-05-batch-expiry-queries-sql` | PostgreSQL token-event root -> PostgreSQL full-grammar v18 | 10 | 0 | `TYPED_VISITOR_GAP_ROUTINE_OR_GENERATOR` | routine/data-generator/trigger body or complex typed structural coverage |
| `sample-data-full-04-queries-05-batch-expiry-queries-sql` | PostgreSQL token-event root -> PostgreSQL full-grammar v17 | 10 | 0 | `TYPED_VISITOR_GAP_ROUTINE_OR_GENERATOR` | routine/data-generator/trigger body or complex typed structural coverage |
| `sample-data-full-04-queries-05-batch-expiry-queries-sql` | PostgreSQL token-event root -> PostgreSQL full-grammar v16 | 10 | 0 | `TYPED_VISITOR_GAP_ROUTINE_OR_GENERATOR` | routine/data-generator/trigger body or complex typed structural coverage |
| `sample-data-full-04-queries-05-batch-expiry-queries-sql` | MySQL token-event root -> MySQL full-grammar v8_0 | 10 | 0 | `TYPED_VISITOR_GAP_ROUTINE_OR_GENERATOR` | routine/data-generator/trigger body or complex typed structural coverage |
| `sample-data-full-02-procedures-08-batch-expiry-procedures-sql` | MySQL token-event root -> MySQL full-grammar v8_0 | 10 | 0 | `TYPED_VISITOR_GAP_ROUTINE_OR_GENERATOR` | routine/data-generator/trigger body or complex typed structural coverage |
| `sample-data-full-02-procedures-07-store-customer-procedures-sql` | MySQL token-event root -> MySQL full-grammar v8_0 | 10 | 0 | `TYPED_VISITOR_GAP_ROUTINE_OR_GENERATOR` | routine/data-generator/trigger body or complex typed structural coverage |
| `sample-data-full-02-procedures-05-third-batch-procedures-sql` | MySQL token-event root -> MySQL full-grammar v8_0 | 5 | 5 | `TYPED_VISITOR_GAP_ROUTINE_OR_GENERATOR` | routine/data-generator/trigger body or complex typed structural coverage |
| `supply-chain-update-explicit-join-sql` | MySQL token-event root -> MySQL full-grammar v8_0 | 6 | 3 | `TYPED_VISITOR_GAP` | root token-event has less typed structural coverage than full-grammar |
| `supply-chain-update-comma-and-subquery-sql` | MySQL token-event root -> MySQL full-grammar v8_0 | 6 | 3 | `TYPED_VISITOR_GAP` | root token-event has less typed structural coverage than full-grammar |
| `pg15-sql` | PostgreSQL token-event root -> PostgreSQL full-grammar v16 | 2 | 7 | `TYPED_VISITOR_GAP` | root token-event has less typed structural coverage than full-grammar |
| `pg15-sql` | PostgreSQL full-grammar v16 -> PostgreSQL full-grammar v17 | 0 | 9 | `VERSION_DELTA` | expected major-version syntax/capability difference |
| `sample-data-full-04-queries-04-store-customer-queries-sql` | PostgreSQL token-event root -> PostgreSQL full-grammar v18 | 8 | 0 | `TYPED_VISITOR_GAP_ROUTINE_OR_GENERATOR` | routine/data-generator/trigger body or complex typed structural coverage |
| `sample-data-full-04-queries-04-store-customer-queries-sql` | PostgreSQL token-event root -> PostgreSQL full-grammar v17 | 8 | 0 | `TYPED_VISITOR_GAP_ROUTINE_OR_GENERATOR` | routine/data-generator/trigger body or complex typed structural coverage |
| `sample-data-full-04-queries-04-store-customer-queries-sql` | PostgreSQL token-event root -> PostgreSQL full-grammar v16 | 8 | 0 | `TYPED_VISITOR_GAP_ROUTINE_OR_GENERATOR` | routine/data-generator/trigger body or complex typed structural coverage |
| `sample-data-full-04-queries-04-store-customer-queries-sql` | MySQL token-event root -> MySQL full-grammar v8_0 | 8 | 0 | `TYPED_VISITOR_GAP_ROUTINE_OR_GENERATOR` | routine/data-generator/trigger body or complex typed structural coverage |
| `sample-data-full-01-schema-02-indexes-and-views-views-sql` | PostgreSQL token-event root -> PostgreSQL full-grammar v18 | 8 | 0 | `TYPED_VISITOR_GAP` | root token-event has less typed structural coverage than full-grammar |
| `sample-data-full-01-schema-02-indexes-and-views-views-sql` | PostgreSQL token-event root -> PostgreSQL full-grammar v17 | 8 | 0 | `TYPED_VISITOR_GAP` | root token-event has less typed structural coverage than full-grammar |
| `sample-data-full-01-schema-02-indexes-and-views-views-sql` | PostgreSQL token-event root -> PostgreSQL full-grammar v16 | 8 | 0 | `TYPED_VISITOR_GAP` | root token-event has less typed structural coverage than full-grammar |
| `business-update-warehouse-comma-subquery-sql` | PostgreSQL token-event root -> PostgreSQL full-grammar v18 | 7 | 1 | `TYPED_VISITOR_GAP` | root token-event has less typed structural coverage than full-grammar |
| `business-update-warehouse-comma-subquery-sql` | PostgreSQL token-event root -> PostgreSQL full-grammar v17 | 7 | 1 | `TYPED_VISITOR_GAP` | root token-event has less typed structural coverage than full-grammar |
| `business-update-warehouse-comma-subquery-sql` | PostgreSQL token-event root -> PostgreSQL full-grammar v16 | 7 | 1 | `TYPED_VISITOR_GAP` | root token-event has less typed structural coverage than full-grammar |
| `business-update-warehouse-complex-sql` | PostgreSQL token-event root -> PostgreSQL full-grammar v18 | 6 | 1 | `TYPED_VISITOR_GAP` | root token-event has less typed structural coverage than full-grammar |
| `business-update-warehouse-complex-sql` | PostgreSQL token-event root -> PostgreSQL full-grammar v17 | 6 | 1 | `TYPED_VISITOR_GAP` | root token-event has less typed structural coverage than full-grammar |
| `business-update-warehouse-complex-sql` | PostgreSQL token-event root -> PostgreSQL full-grammar v16 | 6 | 1 | `TYPED_VISITOR_GAP` | root token-event has less typed structural coverage than full-grammar |
| `sample-data-full-01-schema-05-third-batch-tables-ddl` | PostgreSQL token-event root -> PostgreSQL full-grammar v18 | 6 | 0 | `TYPED_VISITOR_GAP` | root token-event has less typed structural coverage than full-grammar |
| `sample-data-full-01-schema-05-third-batch-tables-ddl` | PostgreSQL token-event root -> PostgreSQL full-grammar v17 | 6 | 0 | `TYPED_VISITOR_GAP` | root token-event has less typed structural coverage than full-grammar |
| `sample-data-full-01-schema-05-third-batch-tables-ddl` | PostgreSQL token-event root -> PostgreSQL full-grammar v16 | 6 | 0 | `TYPED_VISITOR_GAP` | root token-event has less typed structural coverage than full-grammar |
| `sample-data-full-01-schema-05-third-batch-tables-ddl` | MySQL token-event root -> MySQL full-grammar v8_0 | 6 | 0 | `TYPED_VISITOR_GAP` | root token-event has less typed structural coverage than full-grammar |
| `sample-data-full-01-schema-04-supplementary-tables-ddl` | MySQL token-event root -> MySQL full-grammar v8_0 | 6 | 0 | `TYPED_VISITOR_GAP` | root token-event has less typed structural coverage than full-grammar |
| `sample-data-full-01-schema-02-indexes-and-views-views-sql` | MySQL token-event root -> MySQL full-grammar v8_0 | 6 | 0 | `TYPED_VISITOR_GAP` | root token-event has less typed structural coverage than full-grammar |
| `official-subquery-deep-sql` | PostgreSQL token-event root -> PostgreSQL full-grammar v18 | 6 | 0 | `TYPED_VISITOR_GAP` | root token-event has less typed structural coverage than full-grammar |
| `official-subquery-deep-sql` | PostgreSQL token-event root -> PostgreSQL full-grammar v17 | 6 | 0 | `TYPED_VISITOR_GAP` | root token-event has less typed structural coverage than full-grammar |
| `official-subquery-deep-sql` | PostgreSQL token-event root -> PostgreSQL full-grammar v16 | 6 | 0 | `TYPED_VISITOR_GAP` | root token-event has less typed structural coverage than full-grammar |
| `official-cte-dml-sql` | PostgreSQL token-event root -> PostgreSQL full-grammar v18 | 5 | 1 | `TYPED_VISITOR_GAP` | root token-event has less typed structural coverage than full-grammar |
| `official-cte-dml-sql` | PostgreSQL token-event root -> PostgreSQL full-grammar v17 | 5 | 1 | `TYPED_VISITOR_GAP` | root token-event has less typed structural coverage than full-grammar |
| `official-cte-dml-sql` | PostgreSQL token-event root -> PostgreSQL full-grammar v16 | 5 | 1 | `TYPED_VISITOR_GAP` | root token-event has less typed structural coverage than full-grammar |
| `business-financial-asset-wash-procedure-sql` | MySQL token-event root -> MySQL full-grammar v8_0 | 3 | 3 | `TYPED_VISITOR_GAP_ROUTINE_OR_GENERATOR` | routine/data-generator/trigger body or complex typed structural coverage |
| `business-financial-asset-wash-procedure-comma-sql` | MySQL token-event root -> MySQL full-grammar v8_0 | 3 | 3 | `TYPED_VISITOR_GAP_ROUTINE_OR_GENERATOR` | routine/data-generator/trigger body or complex typed structural coverage |
| `business-account-balances-financial-explicit-join-sql` | PostgreSQL token-event root -> PostgreSQL full-grammar v18 | 3 | 3 | `TYPED_VISITOR_GAP` | root token-event has less typed structural coverage than full-grammar |
| `business-account-balances-financial-explicit-join-sql` | PostgreSQL token-event root -> PostgreSQL full-grammar v17 | 3 | 3 | `TYPED_VISITOR_GAP` | root token-event has less typed structural coverage than full-grammar |
| `business-account-balances-financial-explicit-join-sql` | PostgreSQL token-event root -> PostgreSQL full-grammar v16 | 3 | 3 | `TYPED_VISITOR_GAP` | root token-event has less typed structural coverage than full-grammar |
| `business-account-balances-financial-cte-sql` | PostgreSQL token-event root -> PostgreSQL full-grammar v18 | 3 | 3 | `TYPED_VISITOR_GAP` | root token-event has less typed structural coverage than full-grammar |
| `business-account-balances-financial-cte-sql` | PostgreSQL token-event root -> PostgreSQL full-grammar v17 | 3 | 3 | `TYPED_VISITOR_GAP` | root token-event has less typed structural coverage than full-grammar |
| `business-account-balances-financial-cte-sql` | PostgreSQL token-event root -> PostgreSQL full-grammar v16 | 3 | 3 | `TYPED_VISITOR_GAP` | root token-event has less typed structural coverage than full-grammar |
| `official-cte-dml-sql` | MySQL token-event root -> MySQL full-grammar v8_0 | 4 | 1 | `TYPED_VISITOR_GAP` | root token-event has less typed structural coverage than full-grammar |
| `generated-provided-complex-sql` | PostgreSQL token-event root -> PostgreSQL full-grammar v18 | 5 | 0 | `TYPED_VISITOR_GAP` | root token-event has less typed structural coverage than full-grammar |
| `generated-provided-complex-sql` | PostgreSQL token-event root -> PostgreSQL full-grammar v17 | 5 | 0 | `TYPED_VISITOR_GAP` | root token-event has less typed structural coverage than full-grammar |
| `generated-provided-complex-sql` | PostgreSQL token-event root -> PostgreSQL full-grammar v16 | 5 | 0 | `TYPED_VISITOR_GAP` | root token-event has less typed structural coverage than full-grammar |
| `basic-correctness-case-01-procedure-proc-refresh-org-pdf-sql` | MySQL token-event root -> MySQL full-grammar v8_0 | 0 | 5 | `TYPED_VISITOR_GAP_ROUTINE_OR_GENERATOR` | routine/data-generator/trigger body or complex typed structural coverage |
| `sample-data-full-02-procedures-04-procedures-supplement-sql` | MySQL token-event root -> MySQL full-grammar v8_0 | 1 | 3 | `TYPED_VISITOR_GAP_ROUTINE_OR_GENERATOR` | routine/data-generator/trigger body or complex typed structural coverage |
| `sample-data-full-01-schema-04-supplementary-tables-ddl` | PostgreSQL token-event root -> PostgreSQL full-grammar v18 | 4 | 0 | `TYPED_VISITOR_GAP` | root token-event has less typed structural coverage than full-grammar |
| `sample-data-full-01-schema-04-supplementary-tables-ddl` | PostgreSQL token-event root -> PostgreSQL full-grammar v17 | 4 | 0 | `TYPED_VISITOR_GAP` | root token-event has less typed structural coverage than full-grammar |
| `sample-data-full-01-schema-04-supplementary-tables-ddl` | PostgreSQL token-event root -> PostgreSQL full-grammar v16 | 4 | 0 | `TYPED_VISITOR_GAP` | root token-event has less typed structural coverage than full-grammar |
| `sample-data-enterprise-extension-queries-sql` | PostgreSQL token-event root -> PostgreSQL full-grammar v18 | 4 | 0 | `TYPED_VISITOR_GAP` | root token-event has less typed structural coverage than full-grammar |
| `sample-data-enterprise-extension-queries-sql` | PostgreSQL token-event root -> PostgreSQL full-grammar v17 | 4 | 0 | `TYPED_VISITOR_GAP` | root token-event has less typed structural coverage than full-grammar |
| `sample-data-enterprise-extension-queries-sql` | PostgreSQL token-event root -> PostgreSQL full-grammar v16 | 4 | 0 | `TYPED_VISITOR_GAP` | root token-event has less typed structural coverage than full-grammar |
| `sample-data-enterprise-extension-queries-sql` | MySQL token-event root -> MySQL full-grammar v8_0 | 4 | 0 | `TYPED_VISITOR_GAP` | root token-event has less typed structural coverage than full-grammar |
| `official-subquery-edge-sql` | PostgreSQL token-event root -> PostgreSQL full-grammar v18 | 4 | 0 | `TYPED_VISITOR_GAP` | root token-event has less typed structural coverage than full-grammar |
| `official-subquery-edge-sql` | PostgreSQL token-event root -> PostgreSQL full-grammar v17 | 4 | 0 | `TYPED_VISITOR_GAP` | root token-event has less typed structural coverage than full-grammar |
| `official-subquery-edge-sql` | PostgreSQL token-event root -> PostgreSQL full-grammar v16 | 4 | 0 | `TYPED_VISITOR_GAP` | root token-event has less typed structural coverage than full-grammar |
| `generated-industrial-complex-sql` | PostgreSQL token-event root -> PostgreSQL full-grammar v18 | 4 | 0 | `TYPED_VISITOR_GAP` | root token-event has less typed structural coverage than full-grammar |
| `generated-industrial-complex-sql` | PostgreSQL token-event root -> PostgreSQL full-grammar v17 | 4 | 0 | `TYPED_VISITOR_GAP` | root token-event has less typed structural coverage than full-grammar |
| `generated-industrial-complex-sql` | PostgreSQL token-event root -> PostgreSQL full-grammar v16 | 4 | 0 | `TYPED_VISITOR_GAP` | root token-event has less typed structural coverage than full-grammar |
| `extreme-nesting-withrelation-withlineage-sql` | PostgreSQL token-event root -> PostgreSQL full-grammar v18 | 4 | 0 | `TYPED_VISITOR_GAP` | root token-event has less typed structural coverage than full-grammar |
| `extreme-nesting-withrelation-withlineage-sql` | PostgreSQL token-event root -> PostgreSQL full-grammar v17 | 4 | 0 | `TYPED_VISITOR_GAP` | root token-event has less typed structural coverage than full-grammar |
| `extreme-nesting-withrelation-withlineage-sql` | PostgreSQL token-event root -> PostgreSQL full-grammar v16 | 4 | 0 | `TYPED_VISITOR_GAP` | root token-event has less typed structural coverage than full-grammar |
| `extreme-nesting-withrelation-sql` | PostgreSQL token-event root -> PostgreSQL full-grammar v18 | 4 | 0 | `TYPED_VISITOR_GAP` | root token-event has less typed structural coverage than full-grammar |
| `extreme-nesting-withrelation-sql` | PostgreSQL token-event root -> PostgreSQL full-grammar v17 | 4 | 0 | `TYPED_VISITOR_GAP` | root token-event has less typed structural coverage than full-grammar |
| `extreme-nesting-withrelation-sql` | PostgreSQL token-event root -> PostgreSQL full-grammar v16 | 4 | 0 | `TYPED_VISITOR_GAP` | root token-event has less typed structural coverage than full-grammar |
| `edge-cases-sql` | PostgreSQL token-event root -> PostgreSQL full-grammar v18 | 4 | 0 | `TYPED_VISITOR_GAP` | root token-event has less typed structural coverage than full-grammar |
| `edge-cases-sql` | PostgreSQL token-event root -> PostgreSQL full-grammar v17 | 4 | 0 | `TYPED_VISITOR_GAP` | root token-event has less typed structural coverage than full-grammar |

## Review Status

No item is marked `REVIEW_NEEDED` by this generated comparison alone. A diff becomes `REVIEW_NEEDED` only after inspecting the SQL context and failing to classify it as a typed visitor gap, version-only syntax, expected filtered scope, or generated sample expression boundary.

