# Data Lineage Pending Review

This file is maintained with Data Lineage v1. It lists field-flow candidates that are plausible, but not yet stable enough to become `expected-lineage.json` golden fingerprints.

v1 rule: only reviewed physical `table.column -> table.column` lineage enters golden. Parameters, JSON paths, literals, local variables, and dynamic SQL remain outside Data Lineage v1.

## Pending Candidates

### `mysql-business-*-procedure-*-sql`

Input group: `test-fixtures/correctness/mysql/mysql-business-*-procedure-*-sql/input.sql`

Pending examples:

- Parameter JSON / `JSON_TABLE` inputs into temporary rowsets.
- Multi-step routine-local rowset transformations.
- Cross-statement updates inside procedure body.

Reason: v1 deliberately excludes Parameter Binding and routine-local variable/temporary rowset lineage. These examples should be revisited after Parameter Binding or procedure-scope data-flow design.
