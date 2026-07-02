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

SQL Server 当前处于 **initial smoke adaptor** 阶段。它已经有独立 Maven 模块、DatabaseAdaptor、root token-event grammar、五个 versioned full-grammer profile、SQL Server sample-data smoke slice 和 correctness golden，但还不是完整的 MySQL 8.0 correctness 语义迁移，也还没有完成 Microsoft 官方 T-SQL reference 的逐版本严格裁剪。

已实现：

- Maven 模块：`adaptor-sqlserver`。
- `DatabaseAdaptor`：`com.relationdetector.sqlserver.SqlServerDatabaseAdaptor`，通过 Java SPI 注册。
- token-event SQL/DDL：`SqlServerTokenEventStructuredSqlParser` / `SqlServerTokenEventStructuredDdlParser`，使用 `adaptor-sqlserver` 自己的 `SqlServerRelationSqlLexer.g4` / `SqlServerRelationSqlParser.g4`。
- full-grammer module：`sqlserver/2016`、`sqlserver/2017`、`sqlserver/2019`、`sqlserver/2022`、`sqlserver/2025`，每个 profile 使用自己 package 下的 generated lexer/parser。
- sample-data smoke slice：`sample-data/sqlserver/2016|2017|2019|2022|2025`。
- correctness golden：root token-event 与五个 versioned full-grammer profile 各覆盖 SQL Server DDL + DML smoke fixture。

当前有意保留的缺口：

- 五个 SQL Server full-grammer `.g4` 目前来自同一个 pinned `grammars-v4/sql/tsql` 快照，尚未按 Microsoft 官方文档完成逐版本裁剪；因此版本差异 profile 已接线，但严格版本边界仍在 backlog。
- MySQL 8.0 correctness 迁移当前只落地一组 ERP 销售事实表 smoke slice：`customers`、`orders`、`payments`、`sales_fact`、`INSERT SELECT`、`UPDATE FROM`、`MERGE` 和 DDL FK/index。
- `sample-data/sqlserver/*/02-procedures` 已提供 T-SQL procedure 资产，但 correctness 当前优先使用等价 DML batch fixture，后续需要补 `OBJECT_BLOCKS` procedure fixture 和 routine body golden。
- SQL Server metadata collector / object collector / profiler 当前是空实现，后续需要接 JDBC catalog。

详细迁移审计见 `docs/parser-audit/sqlserver-migration-review.md`。

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

当前 SQL Server correctness 是 smoke slice，不是完整迁移。

| Golden 组 | Fixture | SQL / DDL | 说明 |
| --- | ---: | --- | --- |
| SQL Server root token-event | 2 | 1 / 1 | root baseline，使用 2025 smoke SQL 作为宽松 fallback 输入 |
| SQL Server full-grammer v2016 | 2 | 1 / 1 | SQL Server 2016 profile smoke |
| SQL Server full-grammer v2017 | 2 | 1 / 1 | SQL Server 2017 profile smoke |
| SQL Server full-grammer v2019 | 2 | 1 / 1 | SQL Server 2019 profile smoke |
| SQL Server full-grammer v2022 | 2 | 1 / 1 | SQL Server 2022 profile smoke |
| SQL Server full-grammer v2025 | 2 | 1 / 1 | SQL Server 2025 profile smoke |

当前 smoke fixture 语义：

- DDL：`customers`、`orders`、`payments`、`sales_fact` 的 PK / FK / index。
- DML：`INSERT INTO ... SELECT` 重建 `sales_fact`，`UPDATE ... FROM` 汇总已支付金额，`MERGE` 同步支付聚合。
- 预期 relationship：DDL FK/index 关系，SQL predicate join / subquery relation。
- 预期 lineage：`orders.customer_id` / `orders.order_id` / `payments.amount` / `payments.paid_at` 写入 `sales_fact` 对应字段。

## 后续收口

1. 继续把 MySQL 8.0 correctness 按 `DIRECT_COMPATIBLE`、`SEMANTIC_REWRITE`、`VERSION_BOUNDARY_NEGATIVE` 分类迁移到 SQL Server。
2. 按 Microsoft Learn T-SQL 文档为 2016/2017/2019/2022/2025 增加真实版本边界 `.g4` 差异和负向 fixture。
3. 把 `sample-data/sqlserver/*/02-procedures` 接入 `OBJECT_BLOCKS` correctness，并补 T-SQL procedure body relation / lineage。
4. 补 SQL Server JDBC metadata / object / database DDL collector。
5. 扩展 semantic-equivalent benchmark，对比 MySQL 8.0、PostgreSQL、Oracle、SQL Server 在同一业务语义上的 relation / lineage 覆盖。

