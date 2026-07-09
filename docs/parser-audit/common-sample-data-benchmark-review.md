# Common sample-data benchmark 审计

本文记录 common token-event 在 sample-data CLI 统计中的来源、边界和能力判断。

## 当前统计

common 是正式 CLI parser category：配置 `database.type: common` 时由 `CommonDatabaseAdaptor` 接管，使用 common portable typed grammar 跑 file DDL、object files、plain SQL logs、naming evidence、lineage 和 derived path。它不是 MySQL/PostgreSQL/Oracle/SQL Server 的方言 fallback，也不连接 live catalog metadata。

自然 sample-data CLI 只读取：

```text
relation-detector/sample-data/common-natural
```

parser coverage 样例保留在：

```text
relation-detector/sample-data/common-parser-coverage
```

coverage 样例继续服务 correctness/golden，不混入自然 sample-data CLI 统计。

| Parser | Fixtures | SQL / DDL | Rel | Lin | Name | Diag | DerRel | DerLin | DerName |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| common-token-event-sample-data | 9 | 6 / 3 | 319 | 105 | 966 | 0 | 1089 | 13 | 718 |

## 为什么 common 不和 38 个方言 ERP 文件做数量对齐

common natural benchmark 的目标是 portable SQL subset，不是某个方言 ERP 样例库的完整迁移：

- 只放跨方言可自然表达的 `SELECT`、CTE、derived table、join/exists/in、标准 `INSERT SELECT`、基础 `UPDATE/DELETE`、基础 DDL。
- 不放 MySQL `ON DUPLICATE KEY UPDATE`、PostgreSQL `UPDATE FROM`、Oracle PL/SQL、SQL Server `MERGE` 等方言能力。
- 不混入 `*-for-golden.sql` 这类 parser coverage body；这些文件用于 correctness，而不是自然 sample-data 统计。
- common 的 `Name/DerName` 可以较高，因为 portable schema/DDL 与自然查询集中覆盖了稳定的 id/foreign-key-like 命名链；这不是方言覆盖量的直接排名。

## 能力判断

| 分类 | 当前结论 |
| --- | --- |
| `COMMON_PARSER_GAP` | 本轮未确认新的 common parser gap。 |
| `PORTABLE_SQL_ASSET_GAP` | common natural 是精选 portable benchmark，不承诺 38 个 ERP 文件规模。 |
| `EXPECTED_DIALECT_ONLY` | 方言专属 SQL 不进入 common grammar。 |
| `COMMON_FALSE_POSITIVE` | 本轮未确认 common false positive。 |
| `REVIEW_NEEDED` | 无。 |

## 后续口径

- common 作为正式 parser category 继续通过 CLI 可运行、可观测、可验收。
- common 不参与方言自动 fallback；MySQL/PostgreSQL/Oracle/SQL Server unsupported version 仍回到本方言 token-event fallback。
- common 是否需要补能力，只通过 portable natural benchmark、common correctness 或 semantic-equivalent benchmark 的明确缺失来判断，不用方言 ERP 文件数量简单倒推。
