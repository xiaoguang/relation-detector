# Common sample-data benchmark 审计

本文记录 common token-event 在 sample-data 统计中的来源、边界和是否需要补能力的判断。

## 当前统计

common 不是数据库方言 adaptor，不能通过 CLI 的 `databaseType=mysql/postgres/oracle/sqlserver` 扫描自然方言目录。当前统计来自 correctness 中的 portable benchmark fixture：

| Parser | Fixtures | SQL / DDL | Relations | Lineage | NAMING_MATCH | Diagnostics | 来源 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| common-token-event sample-data | 15 | 11 / 4 | 729 | 292 | 397 | 0 | `test-fixtures/correctness/common` |

## 为什么 common 数量看起来高

common 行不是 38 个自然业务 SQL 文件的直接扫描结果，而是高密度 portable benchmark：

- SQL 被压缩成跨方言 portable subset，用来集中覆盖 join、CTE、derived table、DML lineage、DDL column inventory。
- 同一个 fixture 往往承载多个业务语义点，因此单位 fixture 的 relationship / lineage / namingEvidence 密度更高。
- 它的目标是验证 common portable grammar 的结构能力，不是替代 MySQL/PostgreSQL/Oracle/SQL Server 的自然方言 sample-data。

## 能力判断

| 分类 | 当前结论 |
| --- | --- |
| `COMMON_PARSER_GAP` | 本轮未确认新的 common parser gap |
| `PORTABLE_SQL_ASSET_GAP` | 当前 portable benchmark 仍是高密度精选资产，不等于完整 ERP 自然样例库 |
| `EXPECTED_DIALECT_ONLY` | MySQL `ON DUPLICATE KEY UPDATE`、PostgreSQL `UPDATE FROM`、Oracle PL/SQL、SQL Server `MERGE` 等不进入 common grammar |
| `COMMON_FALSE_POSITIVE` | 本轮未确认 common false positive |
| `REVIEW_NEEDED` | 无 |

## 后续口径

- common 只补 portable SQL subset：`SELECT`、CTE、derived table、join/exists/in、标准 `INSERT SELECT`、基础 `UPDATE/DELETE`、基础 DDL。
- 任何方言专属能力都应留在对应 adaptor token-event 或 full-grammer，不为追数量塞进 common。
- common 是否需要补能力，应通过 `semantic-equivalent benchmark` 或 portable SQL fixture 的实际缺失判断，而不是和 38 个自然方言 sample-data 文件做简单数量对齐。
