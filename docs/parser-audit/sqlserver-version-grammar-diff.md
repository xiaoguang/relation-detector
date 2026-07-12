# SQL Server Version Grammar Difference Audit

## Summary

SQL Server full-grammar uses a pinned `antlr/grammars-v4/sql/tsql`
snapshot as its engineering base, then applies project-maintained version
cuts from Microsoft documentation. The token-event grammar remains one broad,
compact fallback grammar and is not used as a source of full-grammar version
truth.

The supported profiles map to SQL Server compatibility levels:

| Profile | SQL Server release | Compatibility level | Local package |
| --- | --- | ---: | --- |
| `sqlserver/2016` | SQL Server 2016 | 130 | `sqlserver.fullgrammar.v2016` |
| `sqlserver/2017` | SQL Server 2017 | 140 | `sqlserver.fullgrammar.v2017` |
| `sqlserver/2019` | SQL Server 2019 | 150 | `sqlserver.fullgrammar.v2019` |
| `sqlserver/2022` | SQL Server 2022 | 160 | `sqlserver.fullgrammar.v2022` |
| `sqlserver/2025` | SQL Server 2025 | 170 | `sqlserver.fullgrammar.v2025` |

## Official Sources

| Area | Source |
| --- | --- |
| T-SQL language reference | `https://learn.microsoft.com/en-us/sql/t-sql/language-reference` |
| SQL Server compatibility levels | `https://learn.microsoft.com/en-us/sql/t-sql/statements/alter-database-transact-sql-compatibility-level` |
| `STRING_AGG` | `https://learn.microsoft.com/en-us/sql/t-sql/functions/string-agg-transact-sql` |
| `DATETRUNC` | `https://learn.microsoft.com/en-us/sql/t-sql/functions/datetrunc-transact-sql` |
| `GENERATE_SERIES` | `https://learn.microsoft.com/en-us/sql/t-sql/functions/generate-series-transact-sql` |
| `VECTOR` data type | `https://learn.microsoft.com/en-us/sql/t-sql/data-types/vector-data-type` |

Microsoft documentation is the source of truth for version boundaries. The
vendored grammars-v4 T-SQL grammar is a fixed implementation base, not the
version authority.

## Implemented Version Boundaries

| Feature | First accepted profile | Lower-version behavior | Positive fixture/test |
| --- | --- | --- | --- |
| `STRING_AGG(...) WITHIN GROUP (...)` | `sqlserver/2017` | `sqlserver/2016` rejects through grammar syntax errors. | `sqlserver2017-version-string-agg-sql`; `SqlServerParserArchitectureTest`. |
| `DATETRUNC(...)` | `sqlserver/2022` | `sqlserver/2016`, `sqlserver/2017`, and `sqlserver/2019` reject through grammar syntax errors. | `sqlserver2022-version-datetrunc-generate-series-sql`; `SqlServerParserArchitectureTest`. |
| `GENERATE_SERIES(...)` rowset function | `sqlserver/2022` | `sqlserver/2016`, `sqlserver/2017`, and `sqlserver/2019` reject through grammar syntax errors. | `sqlserver2022-version-datetrunc-generate-series-sql`; `SqlServerParserArchitectureTest`. |
| `VECTOR(...)` column data type | `sqlserver/2025` | `sqlserver/2016`, `sqlserver/2017`, `sqlserver/2019`, and `sqlserver/2022` reject through grammar syntax errors. | `sqlserver2025-version-vector-ddl`; `SqlServerParserArchitectureTest`. |

The rejection is grammar-based. The low-version lexers may recognize future
tokens so they can fail deterministically, but their parser rules do not allow
those tokens in statement, rowset-function, scalar-function, or data-type
contexts. No Java profile blacklist is used to simulate the version boundary.

## Current Grammar Status

| Profile | Grammar status | Notes |
| --- | --- | --- |
| `sqlserver/2016` | `VERSION_SCOPED` projection | Baseline T-SQL grammar for sample-data, plus grammar-level rejection of implemented 2017/2022/2025-only probes. |
| `sqlserver/2017` | `VERSION_SCOPED` projection | Adds `STRING_AGG`; otherwise inherits 2016-compatible sample-data surface. |
| `sqlserver/2019` | `VERSION_SCOPED` projection | Currently inherits 2017 syntax for the implemented probe set; no separate 2019-only grammar feature has been source-backed in this repository yet. |
| `sqlserver/2022` | `VERSION_SCOPED` projection | Adds `DATETRUNC` and `GENERATE_SERIES`. |
| `sqlserver/2025` | `VERSION_SCOPED` projection | Adds `VECTOR(...)` data type; broader 2025 AI/regex syntax remains future work until each feature is source-backed and tested. |

Sample-data remains a natural ERP baseline written in a SQL Server
2016-compatible T-SQL subset. Version-specific probes live in version-only
correctness fixtures so the ERP baseline does not become an artificial syntax
stress test.

## Backlog

The following work remains open and must not be implied by the current
fixtures:

- Add source-backed 2019-only syntax if a grammar production is needed by
  correctness or semantic-equivalent benchmarks.
- Expand SQL Server 2025 support beyond `VECTOR(...)` only after confirming
  each feature from Microsoft documentation.
- Continue converting any newly added version-only syntax into `.g4` or typed
  context changes; do not implement version boundaries with Java string checks.

## Review Status

| Classification | Status | Notes |
| --- | --- | --- |
| `CONFIRMED_VERSION_BOUNDARY` | Closed for the four implemented probes | 2017 `STRING_AGG`, 2022 `DATETRUNC`, 2022 `GENERATE_SERIES`, 2025 `VECTOR`. |
| `OFFICIAL_GRAMMAR_BACKLOG` | Open | Full SQL Server official T-SQL conversion remains incremental. |
| `REVIEW_NEEDED` | None | The added fixtures have clear relation semantics and no lineage-producing writes. |
