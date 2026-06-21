# full-grammer Expression Transform Compatibility Audit

This file records expression-transform differences that were exposed by the
full-grammer shadow parser and resolved by manual review.

Production token-event output, full-grammer shadow output, and correctness
golden now agree on the reviewed labels. `FullGrammerCorrectnessShadowTest`
does not keep a transform compatibility allowlist; new transform drift must fail
the shadow parity test and be reviewed explicitly.

## Resolved Items

| Fixture | Previous gold fingerprint | Reviewed fingerprint | Decision |
| --- | --- | --- | --- |
| `mysql-business-financial-asset-wash-procedure-sql` | `VALUE:AGGREGATE:account_balances.max_credit_limit->account_balances.adjusted_limit` | `VALUE:ARITHMETIC:account_balances.max_credit_limit->account_balances.adjusted_limit` | `max_credit_limit` is a column name, not an aggregate function. The write is an arithmetic limit adjustment. |
| `mysql-business-financial-asset-wash-procedure-comma-sql` | `VALUE:AGGREGATE:account_balances.max_credit_limit->account_balances.adjusted_limit` | `VALUE:ARITHMETIC:account_balances.max_credit_limit->account_balances.adjusted_limit` | Same business case as the explicit JOIN procedure. |
| `mysql-business-financial-asset-wash-procedure-sql` | `VALUE:AGGREGATE:dormant_risk_scores.country_code,dormant_risk_scores.days_since_last_active,dormant_risk_scores.wealth_tile,user_financial_snapshot.primary_categories->account_balances.compliance_notes` | `VALUE:CONCAT_FORMAT:dormant_risk_scores.country_code,dormant_risk_scores.days_since_last_active,dormant_risk_scores.wealth_tile,user_financial_snapshot.primary_categories->account_balances.compliance_notes` | The final write builds formatted compliance notes; aggregate provenance of one source does not override the final transform. |
| `mysql-business-financial-asset-wash-procedure-comma-sql` | `VALUE:AGGREGATE:dormant_risk_scores.country_code,dormant_risk_scores.days_since_last_active,dormant_risk_scores.wealth_tile,user_financial_snapshot.primary_categories->account_balances.compliance_notes` | `VALUE:CONCAT_FORMAT:dormant_risk_scores.country_code,dormant_risk_scores.days_since_last_active,dormant_risk_scores.wealth_tile,user_financial_snapshot.primary_categories->account_balances.compliance_notes` | Same business case as the explicit JOIN procedure. |
| `basic-correctness-case-01-procedure-proc-refresh-org-pdf-sql` | `VALUE:AGGREGATE:jsh_temp_org_pdf.running_sum,jsh_temp_org_pdf.weight->jsh_temp_org_pdf.cdf_end` | `VALUE:CUMULATIVE:jsh_temp_org_pdf.running_sum,jsh_temp_org_pdf.weight->jsh_temp_org_pdf.cdf_end` | The expression is a running cumulative distribution update. `CUMULATIVE` was added as a public transform type with the same default confidence as `AGGREGATE`. |
| `basic-correctness-case-01-procedure-proc-refresh-org-pdf-sql` | `VALUE:CASE_WHEN:jsh_organization.org_no->jsh_temp_org_pdf.weight` | `CONTROL:CASE_WHEN:jsh_organization.org_no->jsh_temp_org_pdf.weight` | `org_no` controls the selected `CASE` branch; it is not directly written as the target value. |
| `basic-correctness-case-01-procedure-sp-fill-biz-bill-item-fact-new-with-progress-sql` | `VALUE:AGGREGATE:jsh_depot_item.tenant_id->biz_sync_progress.tenantId` | removed | `biz_sync_progress.tenantId` is written from `p_tenant_id` / local variables in `INSERT ... VALUES`; Data Lineage v1 does not model parameter binding. The old candidate came from crossing statement boundaries inside a procedure block. |

## Current Rule

- `AGGREGATE` is reserved for real aggregate functions such as `SUM`, `AVG`,
  `COUNT`, `MIN`, and `MAX`.
- `CUMULATIVE` is used for running sum, running total, cumulative distribution,
  and similar cumulative aggregate-derived writes.
- `CONCAT_FORMAT` describes final string-building writes even when one source was
  produced by an aggregate projection upstream.
- `CASE_WHEN` uses `CONTROL` when the source column appears in the condition that
  chooses a target value.
- Parameter, literal, JSON path, and local variable writes are still outside Data
  Lineage v1 and must not be replaced by unrelated physical table columns.
