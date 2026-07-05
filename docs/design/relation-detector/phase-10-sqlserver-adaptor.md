# Phase 10：SQL Server adaptor 详细设计

## 目标

SQL Server adaptor 按 MySQL / PostgreSQL / Oracle 的 parser bundle 模式接入 relation-detector：对外仍使用统一的 `parser.mode=auto|full-grammer|token-event`，内部提供 SQL Server root token-event fallback、versioned full-grammer module、sample-data 和 correctness golden。

本轮版本矩阵：

| Profile | SQL Server 版本 | 兼容级别 | package | correctness golden |
| --- | --- | ---: | --- | --- |
| `sqlserver/2016` | SQL Server 2016 | 130 | `sqlserver.fullgrammer.v2016` | `test-fixtures/correctness/sqlserver/v2016` |
| `sqlserver/2017` | SQL Server 2017 | 140 | `sqlserver.fullgrammer.v2017` | `test-fixtures/correctness/sqlserver/v2017` |
| `sqlserver/2019` | SQL Server 2019 | 150 | `sqlserver.fullgrammer.v2019` | `test-fixtures/correctness/sqlserver/v2019` |
| `sqlserver/2022` | SQL Server 2022 | 160 | `sqlserver.fullgrammer.v2022` | `test-fixtures/correctness/sqlserver/v2022` |
| `sqlserver/2025` | SQL Server 2025 | 170 | `sqlserver.fullgrammer.v2025` | `test-fixtures/correctness/sqlserver/v2025` |

官方参考入口：

- T-SQL Language Reference: `https://learn.microsoft.com/en-us/sql/t-sql/language-reference`
- ALTER DATABASE compatibility level: `https://learn.microsoft.com/en-us/sql/t-sql/statements/alter-database-transact-sql-compatibility-level`
- Community grammar base: `https://github.com/antlr/grammars-v4/tree/master/sql/tsql`

## 当前实现状态

SQL Server 当前已经完成 **ERP sample-data correctness 全量接入**：有独立 Maven 模块、DatabaseAdaptor、root token-event grammar、五个 versioned full-grammer profile、`sample-data/sqlserver/2016|2017|2019|2022|2025` 每版 38 个 SQL 文件，以及 root token-event + 五个 versioned full-grammer correctness golden。首批 Microsoft 文档可确认的 version-only 语法已经落到 `.g4` 差异和 correctness / architecture 测试中：2017 `STRING_AGG`、2022 `DATETRUNC` / `GENERATE_SERIES`、2025 `VECTOR(...)`。

需要分清两个边界：

- sample-data 业务语义覆盖已经从 smoke 扩展到与 MySQL 8.0 sample-data 文件布局对齐。
- Microsoft 官方 T-SQL reference 的逐版本严格裁剪已完成首批边界；当前 sample-data 仍使用 SQL Server 2016-compatible 保守 T-SQL 子集，因此 sample-data 输出保持跨版本一致，version-only 探针单独放在 correctness fixture。

已实现：

- Maven 模块：`adaptor-sqlserver`。
- `DatabaseAdaptor`：`com.relationdetector.sqlserver.SqlServerDatabaseAdaptor`，通过 Java SPI 注册。
- token-event SQL/DDL：`SqlServerTokenEventStructuredSqlParser` / `SqlServerTokenEventStructuredDdlParser`，使用 `adaptor-sqlserver` 自己的 `SqlServerRelationSqlLexer.g4` / `SqlServerRelationSqlParser.g4`。
- full-grammer module：`sqlserver/2016`、`sqlserver/2017`、`sqlserver/2019`、`sqlserver/2022`、`sqlserver/2025`，每个 profile 使用自己 package 下的 generated lexer/parser。
- sample-data：`sample-data/sqlserver/2016|2017|2019|2022|2025`，每版 38 个 SQL 文件。
- correctness golden：root token-event 覆盖 `sample-data/sqlserver/2025` 的 38 个文件；五个 versioned full-grammer profile 各覆盖对应版本目录的 38 个文件，另有 version-only fixture 锁定 2017/2022/2025 的首批边界。
- asset hygiene：SQL Server sample-data 与 correctness input 会拒绝 MySQL/PostgreSQL/Oracle 残留语法。
- endpoint schema fidelity：SQL Server parser 支持 `[schema].[table]` 和 `[table]` 两种输入形态。输入显式写 `[dbo].[employees]` 时，SQL/DDL relationship、DDL column inventory 和 top-level `namingEvidence` 都保留 `dbo.employees`；输入只写 `[employees]` 时输出 `employees`，不自动补 `dbo`。SQL Server sample-data 当前选择 `[dbo].[table]` 作为资产内 canonical 形式，这是资产一致性规则，不是所有数据库都必须 schema-qualified 的全局规则。

当前有意保留的缺口：

- 五个 SQL Server full-grammer `.g4` 来自同一个 pinned `grammars-v4/sql/tsql` 快照，但已经按 Microsoft 官方文档做了首批逐版本裁剪。更广泛的官方 T-SQL family 覆盖仍是 backlog。
- 当前 sample-data 为跨版本业务等价 baseline，不混入高版本专属 T-SQL。后续版本专属语法应单独进入 version-only fixture。
- SQL Server metadata collector / object collector / profiler 当前是空实现，后续需要接 JDBC catalog。

详细迁移审计见 `docs/parser-audit/sqlserver-migration-review.md`；版本差异清单见 `docs/parser-audit/sqlserver-version-grammar-diff.md`。

## 包结构

```text
adaptor-sqlserver/src/main/java/com/relationdetector/sqlserver
  SqlServerDatabaseAdaptor

adaptor-sqlserver/src/main/java/com/relationdetector/sqlserver/tokenevent
  SqlServerTokenEventStructuredSqlParser
  SqlServerTokenEventStructuredDdlParser

adaptor-sqlserver/src/main/java/com/relationdetector/sqlserver/fullgrammer/common
  AbstractSqlServerFullGrammerDialectModule
  SqlServerFullGrammerStructuredSqlParser
  SqlServerFullGrammerStructuredDdlParser
  SqlServerParseTreeEventCollector
  SqlServerExpressionAnalyzer

adaptor-sqlserver/src/main/java/com/relationdetector/sqlserver/fullgrammer/v2016
adaptor-sqlserver/src/main/java/com/relationdetector/sqlserver/fullgrammer/v2017
adaptor-sqlserver/src/main/java/com/relationdetector/sqlserver/fullgrammer/v2019
adaptor-sqlserver/src/main/java/com/relationdetector/sqlserver/fullgrammer/v2022
adaptor-sqlserver/src/main/java/com/relationdetector/sqlserver/fullgrammer/v2025
  SqlServerFullGrammerBinding
  SqlServer20xxFullGrammerDialectModule

adaptor-sqlserver/src/main/java/com/relationdetector/sqlserver/routine
  SqlServerRoutineScopePolicy
```

ANTLR grammar：

```text
adaptor-sqlserver/src/main/antlr4/com/relationdetector/sqlserver/tokenevent
  SqlServerRelationSqlLexer.g4
  SqlServerRelationSqlParser.g4

adaptor-sqlserver/src/main/antlr4/com/relationdetector/sqlserver/fullgrammer/v2016
adaptor-sqlserver/src/main/antlr4/com/relationdetector/sqlserver/fullgrammer/v2017
adaptor-sqlserver/src/main/antlr4/com/relationdetector/sqlserver/fullgrammer/v2019
adaptor-sqlserver/src/main/antlr4/com/relationdetector/sqlserver/fullgrammer/v2022
adaptor-sqlserver/src/main/antlr4/com/relationdetector/sqlserver/fullgrammer/v2025
  SqlServerFullGrammerLexer.g4
  SqlServerFullGrammerParser.g4
```

token-event 与 full-grammer 不共享 generated parser class，也不互相 delegate。root token-event 只使用 `SqlServerRelationSql*`；versioned full-grammer 只使用各自 `SqlServerFullGrammer*`。

## Parser 选择

```mermaid
flowchart TD
  A["CLI/YAML database.type=sqlserver"] --> B["SqlServerDatabaseAdaptor"]
  B --> C["ParserBundleSelector"]
  C --> D{"parser.mode"}
  D -->|"token-event"| E["SQL Server token-event parser"]
  D -->|"auto/full-grammer + profile selected"| F["SQL Server versioned full-grammer module"]
  D -->|"unsupported version / no profile"| E
  F --> G["Structured events"]
  E --> G
  G --> H["core relationship / lineage / DDL semantic layer"]
```

运行语义：

- `parser.mode=token-event`：只调用 SQL Server token-event fallback。
- `parser.mode=auto`：有 `sqlserver/<version>` profile 时选择对应 full-grammer generated parser；选不中时 fallback token-event。
- `parser.mode=full-grammer`：优先 versioned full-grammer generated parser；profile 缺失或 hard failure 时 fallback token-event 并 warning。
- versioned correctness fixture 不允许 silent fallback；它必须按 manifest 指定 profile 运行。

## Correctness 范围

当前 SQL Server correctness 覆盖 root token-event 与五个 versioned full-grammer 的 ERP sample-data fixture。root token-event 覆盖 38 个 fixture；versioned full-grammer 覆盖 sample-data 以及 version/profile smoke 或边界 fixture。root token-event 和 versioned full-grammer lineage 已对齐；full-grammer 由于 typed DDL / predicate context 更完整，会产生更多 relationship 和命名 evidence：

| Golden 组 | Fixture | SQL / DDL | Relationship fingerprints | Lineage fingerprints | Diagnostics | Rel NAMING_MATCH | Top-level namingEvidence |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| SQL Server root token-event | 38 | 32 / 6 | 727 | 437 | 0 | 283 | 349 |
| SQL Server full-grammer v2016 | 39 | 33 / 6 | 1035 | 437 | 0 | 529 | 595 |
| SQL Server full-grammer v2017 | 40 | 34 / 6 | 1036 | 437 | 0 | 530 | 596 |
| SQL Server full-grammer v2019 | 39 | 33 / 6 | 1035 | 437 | 0 | 529 | 595 |
| SQL Server full-grammer v2022 | 40 | 34 / 6 | 1036 | 437 | 0 | 530 | 596 |
| SQL Server full-grammer v2025 | 40 | 33 / 7 | 1036 | 437 | 0 | 529 | 595 |

当前 fixture 语义：

- DDL：ERP 表、PK / FK / UNIQUE / index、视图和触发器承载表间结构 evidence。
- SQL / procedure / query：`JOIN`、`EXISTS`、`IN`、CTE、`INSERT SELECT`、`UPDATE ... FROM`、`MERGE`、聚合和表达式写入。
- 预期 relationship：DDL FK/index 关系，以及 SQL predicate join / subquery relation。
- 预期 lineage：明确字段写入、聚合写入、`UPDATE ... FROM` 与 `MERGE` 更新映射；参数、局部变量、临时表和动态 SQL 不作为物理 source。

SQL Server root token-event 与 full-grammer v2025 在 sample-data 上当前 lineage fingerprint 数一致；full-grammer 因 typed DDL / predicate context 更完整，多识别少量 relationship 与 top-level naming evidence。五个 versioned full-grammer 的 sample-data 输出一致；该一致性来自当前 sample-data 的跨版本保守 T-SQL 子集。版本差异由 version-only fixtures 和 `SqlServerParserArchitectureTest` 单独验证。

## 后续收口

1. 继续按 Microsoft Learn T-SQL 文档为 2016/2017/2019/2022/2025 扩展 source-backed 版本边界 `.g4` 差异和负向测试。
2. 保持 SQL Server sample-data 的自然业务 SQL；如需高密度关系探测，继续扩展 semantic-equivalent benchmark，而不是把探针模板放回 sample-data。
3. 补 SQL Server JDBC metadata / object / database DDL collector。
4. 扩展 semantic-equivalent benchmark，对比 MySQL 8.0、PostgreSQL、Oracle、SQL Server 在同一业务语义上的 relation / lineage 覆盖。
