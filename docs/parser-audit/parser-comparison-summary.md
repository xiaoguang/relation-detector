# Parser Comparison Summary

This document separates three questions that used to be mixed together:

1. What can each parser currently recognize on full `sample-data` assets?
2. Can every parser produce the same result when the business semantics are intentionally equivalent?
3. Which remaining differences are parser capability gaps instead of SQL asset differences?

## 1. sample-data Capability Snapshot

`sample-data` is the broad ERP corpus. The SQL files are dialect-native, so this table is a capability snapshot, not a strict equality benchmark.

| Parser category | Fixtures | SQL / DDL | Relations | Lineage | NAMING_MATCH | Diagnostics |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| common token-event sample-data | 9 | 6 / 3 | 319 | 105 | 248 | 0 |
| MySQL token-event root sample-data | 38 | 32 / 6 | 347 | 242 | 238 | 0 |
| MySQL full-grammer v5_7 sample-data | 38 | 32 / 6 | 337 | 264 | 242 | 0 |
| MySQL full-grammer v8_0 sample-data | 38 | 32 / 6 | 366 | 253 | 244 | 0 |
| PostgreSQL token-event root sample-data | 38 | 32 / 6 | 352 | 205 | 241 | 0 |
| PostgreSQL full-grammer v16 sample-data | 38 | 32 / 6 | 352 | 206 | 241 | 0 |
| PostgreSQL full-grammer v17 sample-data | 38 | 32 / 6 | 352 | 206 | 241 | 0 |
| PostgreSQL full-grammer v18 sample-data | 38 | 32 / 6 | 352 | 205 | 241 | 0 |
| Oracle token-event root sample-data | 38 | 32 / 6 | 358 | 217 | 239 | 0 |
| Oracle full-grammer v12c sample-data | 38 | 32 / 6 | 358 | 217 | 239 | 0 |
| Oracle full-grammer v19c sample-data | 38 | 32 / 6 | 358 | 217 | 239 | 0 |
| Oracle full-grammer v21c sample-data | 38 | 32 / 6 | 358 | 217 | 239 | 0 |
| Oracle full-grammer v26ai sample-data | 38 | 32 / 6 | 358 | 217 | 239 | 0 |
| SQL Server token-event root sample-data | 38 | 32 / 6 | 343 | 266 | 251 | 0 |
| SQL Server full-grammer v2016 sample-data | 38 | 32 / 6 | 344 | 266 | 251 | 0 |
| SQL Server full-grammer v2017 sample-data | 38 | 32 / 6 | 344 | 266 | 251 | 0 |
| SQL Server full-grammer v2019 sample-data | 38 | 32 / 6 | 344 | 266 | 251 | 0 |
| SQL Server full-grammer v2022 sample-data | 38 | 32 / 6 | 344 | 266 | 251 | 0 |
| SQL Server full-grammer v2025 sample-data | 38 | 32 / 6 | 344 | 266 | 251 | 0 |

Interpretation:

- MySQL token-event and MySQL 5.7/8.0 full-grammer now cover the same sample-data surface. Their remaining differences come from natural 5.7/8.0 SQL rewrites, versioned DDL/routine coverage, and parser capability differences. The semantic-equivalent benchmark is the equality check; this table is a broad capability snapshot.
- PostgreSQL token-event and PostgreSQL full-grammer are close on sample-data lineage. v16/v17 have one extra non-trivial self-update lineage from the version-specific `pg16_generated_margin_demo.sales_amount = target.sales_amount * 1.05` SQL; that is an expected asset/version delta, not a parser false positive.
- Oracle token-event and Oracle full-grammer now match on the merged `Rel/Lin/Name` counts for natural sample-data. A source-set audit still shows Oracle token-event is narrower than full-grammer for nested scalar aggregate updates in `sp_update_supplier_metrics`; the count table alone should not be used as proof of source-set parity.
- SQL Server sample-data is now fully represented. SQL Server full-grammer has one remaining full-only weak relationship candidate from `accounting_periods.period_code = CONVERT(... sales_orders.order_date ...)`; because this is column-to-function(column), it is tracked as a predicate tightness item rather than accepted as direct column equality.
- `sample-data` counts should not be used as a semantic equality score because each dialect has native syntax and version-specific assets.

### Derived-enabled Snapshot

This table uses the same sample-data CLI inputs with `derivedPaths.enabled=true`. `DerRel` is derived relationship count, `DerLin` is derived value-lineage count, and `DerName` is top-level `TRANSITIVE_NAMING_PATH` count. Direct `Rel/Lin/Name` columns are repeated from the derived-enabled JSON output.

| Parser category | Fixtures | SQL / DDL | Rel | Lin | Name | Diag | DerRel | DerLin | DerName |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| common token-event sample-data | 9 | 6 / 3 | 319 | 105 | 966 | 0 | 1089 | 13 | 718 |
| MySQL token-event root sample-data | 38 | 32 / 6 | 347 | 242 | 941 | 0 | 1004 | 56 | 703 |
| MySQL full-grammer v5_7 sample-data | 38 | 32 / 6 | 337 | 264 | 937 | 0 | 1043 | 60 | 695 |
| MySQL full-grammer v8_0 sample-data | 38 | 32 / 6 | 366 | 253 | 990 | 0 | 1077 | 59 | 746 |
| PostgreSQL token-event root sample-data | 38 | 32 / 6 | 352 | 205 | 970 | 0 | 1061 | 37 | 729 |
| PostgreSQL full-grammer v16 sample-data | 38 | 32 / 6 | 352 | 206 | 970 | 0 | 1061 | 37 | 729 |
| PostgreSQL full-grammer v17 sample-data | 38 | 32 / 6 | 352 | 206 | 970 | 0 | 1061 | 37 | 729 |
| PostgreSQL full-grammer v18 sample-data | 38 | 32 / 6 | 352 | 205 | 970 | 0 | 1061 | 37 | 729 |
| Oracle token-event root sample-data | 38 | 32 / 6 | 358 | 217 | 941 | 0 | 1004 | 45 | 702 |
| Oracle full-grammer v12c sample-data | 38 | 32 / 6 | 358 | 217 | 950 | 0 | 1009 | 45 | 711 |
| Oracle full-grammer v19c sample-data | 38 | 32 / 6 | 358 | 217 | 950 | 0 | 1009 | 45 | 711 |
| Oracle full-grammer v21c sample-data | 38 | 32 / 6 | 358 | 217 | 950 | 0 | 1009 | 45 | 711 |
| Oracle full-grammer v26ai sample-data | 38 | 32 / 6 | 358 | 217 | 950 | 0 | 1009 | 45 | 711 |
| SQL Server token-event root sample-data | 38 | 32 / 6 | 343 | 266 | 950 | 0 | 1025 | 105 | 699 |
| SQL Server full-grammer v2016 sample-data | 38 | 32 / 6 | 344 | 266 | 951 | 0 | 1025 | 105 | 700 |
| SQL Server full-grammer v2017 sample-data | 38 | 32 / 6 | 344 | 266 | 951 | 0 | 1025 | 105 | 700 |
| SQL Server full-grammer v2019 sample-data | 38 | 32 / 6 | 344 | 266 | 951 | 0 | 1025 | 105 | 700 |
| SQL Server full-grammer v2022 sample-data | 38 | 32 / 6 | 344 | 266 | 951 | 0 | 1025 | 105 | 700 |
| SQL Server full-grammer v2025 sample-data | 38 | 32 / 6 | 344 | 266 | 951 | 0 | 1025 | 105 | 700 |

Derived relationship now uses reverse referenced-by traversal internally and still emits FK-like forward output. The large SQL Server derived relationship inflation caused by earlier forward FK + identity bridge traversal is removed; derived naming evidence is now visible as `TRANSITIVE_NAMING_PATH`.

## 2. semantic-equivalent Benchmark

`test-fixtures/semantic-equivalent` is the equality benchmark. Each scenario has dialect/version fixtures for common, MySQL, PostgreSQL, and Oracle. The SQL text may differ, but the intended business semantics and final physical fingerprints must match.

| Scenario | Relations | Lineage | Fixture coverage | Status |
| --- | ---: | ---: | ---: | --- |
| `sales-fact-rebuild` | 2 | 7 | 12 parser categories | `MATCHED` |
| `batch-expiry-analysis` | 2 | 0 | 12 parser categories | `MATCHED` |
| `ddl-fk-index` | 1 | 0 | 12 parser categories | `MATCHED` |
| `inventory-posting` | 2 | 6 | 12 parser categories | `MATCHED` |
| `mrp-run` | 2 | 5 | 12 parser categories | `MATCHED` |
| `picking-task` | 3 | 6 | 12 parser categories | `MATCHED` |
| `return-refund` | 2 | 6 | 12 parser categories | `MATCHED` |
| `relation-probe` | 9 | 0 | SQL Server full-grammer v2016/v2017/v2019/v2022/v2025 | `MATCHED` |

The 12 shared ERP parser categories are:

- common token-event
- MySQL token-event root
- MySQL full-grammer v8_0
- PostgreSQL token-event root
- PostgreSQL full-grammer v16 / v17 / v18
- Oracle token-event root
- Oracle full-grammer v12c / v19c / v21c / v26ai

SQL Server currently has an additional `relation-probe` semantic-equivalent scenario for high-density T-SQL relationship detection across `v2016` through `v2025`. Current conclusion: no confirmed parser gap is exposed by the semantic-equivalent scenarios. If a future scenario has matching business semantics but different fingerprints, that is a parser gap candidate unless the SQL translation itself is not semantically equivalent.

## 3. Parser Gap List

| Area | Current evidence | Classification | Next action |
| --- | --- | --- | --- |
| MySQL token-event vs MySQL full-grammer on broad sample-data | root `347 / 242`; v8_0 `366 / 253` | `TOKEN_EVENT_TYPED_VISITOR_COVERAGE` | Two remaining confirmed relation/name misses are scalar-subquery/routine join contexts: `purchase_receipt_items.order_item_id -> purchase_order_items.id` and `serial_numbers.return_id -> sales_returns.id`. Continue adding typed token-event support only from concrete SQL evidence. |
| PostgreSQL token-event vs PostgreSQL full-grammer on broad sample-data | root `352 / 205`; v18 `352 / 205`; v16/v17 `352 / 206` | `EXPECTED_VERSION_DELTA` | v16/v17 carry one additional non-trivial self-update lineage in their version-specific SQL. v18 and root token-event are aligned on merged direct counts. |
| Oracle full-grammer vs Oracle token-event on broad sample-data | root `358 / 217`; full-grammer `358 / 217` | `SOURCE_SET_DELTA` | Merged counts align, but token-event remains narrower for supplier metric scalar aggregate source sets. Continue auditing source columns, not only final lineage count. Oracle full-grammer remains versioned but official full syntax coverage is still backlog. |
| SQL Server full-grammer vs token-event on broad sample-data | root `343 / 266`; full-grammer `344 / 266` | `SQLSERVER_PREDICATE_TIGHTNESS` | The only full-only relation currently observed is `accounting_periods.period_code -> sales_orders.order_date` from `period_code = CONVERT(... order_date ...)`; this should be tightened if CO evidence is limited to direct column comparisons. |
| Cross-dialect semantic-equivalent scenarios | all scenarios are `MATCHED` | `NO_CONFIRMED_GAP` | Use this benchmark as the primary proof that equivalent SQL can converge across parser categories. |
| Dynamic SQL, parameters, local variables, temporary tables, pseudo rowsets | excluded by design in lineage audit | `EXPECTED_FILTERED_SCOPE` | Do not add these to physical relation / lineage golden unless a future design changes the semantic boundary. |

## 4. Output Audit Notes

The latest sample-data output audit checked every generated JSON in `relation-detector/target/sample-data-parser-cli/results`:

- Summary counts match the corresponding output arrays.
- `warning-codes.tsv` is clean: every parser reports `NONE 0`.
- `rawEvidence.source` no longer contains the local absolute workspace path.

Remaining implementation mismatches that are not solved by documentation:

| Area | Concrete evidence | Status |
| --- | --- | --- |
| SQL Server full predicate relation | `dbo.accounting_periods.period_code -> dbo.sales_orders.order_date` from `period_code = CONVERT(NVARCHAR(7), order_date, 120)` | Likely false positive if `CO_OCCURRENCE` should mean direct column comparison. |
| Oracle token-event scalar aggregate source set | `sp_update_supplier_metrics` lacks several `purchase_returns` / `purchase_return_items` / `inspection_reports` source columns that Oracle full-grammer reports | Parser/source-set gap; merged lineage count alone hides it. |
| MySQL token-event scalar subquery relation coverage | Missing `purchase_receipt_items.order_item_id -> purchase_order_items.id` and `serial_numbers.return_id -> sales_returns.id` compared with MySQL 8.0 full-grammer | Parser gap in routine / select-list scalar subquery predicate extraction. |
| Lineage evidence typing | `dataLineages[].evidence[]` and `rawEvidence[]` use `transformType/sourceType/score/source/detail/attributes` and do not expose relationship-style `type` | Output provenance typing gap; facts and evidence refs are present, but lineage evidence typing is not yet unified with relationship/naming evidence. |
| Oracle routine provenance id | `sourceStatementId` / `sourceBlockId` may contain a trailing `)` for `sp_update_supplier_metrics)` | Provenance canonicalization bug; fact counts are unaffected. |

## Validation

Use these commands to validate the split:

```bash
mvn -pl relation-detector/cli -am \
  -Dtest=SemanticEquivalentCorrectnessTest \
  -Dsurefire.failIfNoSpecifiedTests=false test

mvn -pl relation-detector/cli -am \
  -Dtest=CorrectnessFixtureRunnerTest \
  -DcorrectnessFixtureFilter=semantic-equivalent \
  -Dsurefire.failIfNoSpecifiedTests=false test

mvn -pl relation-detector/cli -am \
  -Dtest=CorrectnessFixtureRunnerTest \
  -DcorrectnessFixtureProfile=full \
  -Dsurefire.failIfNoSpecifiedTests=false test
```
