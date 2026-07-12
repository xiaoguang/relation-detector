# ENUM 详细说明

本文档定义项目中需要稳定维护的枚举。实现人员应优先以本文档为准创建 Java enum、JSON 字段值和测试 fixture。

## 维护总原则

- JSON 输出中的 enum 值必须稳定，采用全大写下划线格式，例如 `FK_LIKE`。
- 已发布 enum 值不要改名；如果语义变化，应新增 enum 值并保留旧值兼容。
- enum 用于表达离散状态，不要把可变文本、文件名、SQL 片段放进 enum。
- 每个 enum 都应有单元测试覆盖 JSON 序列化和反序列化。
- 文档中标记为“预留”的值可以先不实现完整逻辑，但不要在 v1 输出中伪造结果。

## 1. DatabaseType

表示用户要扫描的数据库类型，来自 YAML 的 `database.type`，用于选择 adaptor。

建议 Java enum：

```java
public enum DatabaseType {
  COMMON,
  MYSQL,
  POSTGRESQL,
  SQLSERVER,
  ORACLE
}
```

| 值 | 含义 | v1 状态 | 示例配置 |
| --- | --- | --- | --- |
| `COMMON` | 跨方言 portable SQL parser category。只使用 common token-event typed grammar，不连接具体数据库 catalog。 | 正式 CLI category / portable sample-data 可运行 | `type: common` |
| `MYSQL` | MySQL 数据库。当前有 MySQL 5.7/8.0 versioned parser、sample-data golden 和 live collectors。 | 当前覆盖最广之一，仍有已审计 parser gap | `type: mysql` |
| `POSTGRESQL` | PostgreSQL 数据库。当前有 PostgreSQL 16/17/18 versioned parser、sample-data golden 和 live collectors。 | 当前覆盖最广之一，仍有已审计 parser/provenance gap | `type: postgresql` |
| `SQLSERVER` | Microsoft SQL Server。 | sample-data parser golden 已接入 | `type: sqlserver` |
| `ORACLE` | Oracle Database。 | 初始 adaptor / parser golden 已接入 | `type: oracle` |

维护说明：

- 配置中可以允许小写别名，例如 `mysql`，但内部统一转成 `MYSQL`。
- `COMMON` 是 portable parser category，不是某个方言的 fallback facade。CLI 配置 `database.type: common` 时由 core 的 `CommonDatabaseAdaptor` 接管，只跑 common token-event SQL/DDL parser、file DDL、object files 和 plain SQL logs；不做 live metadata、database object collection 或 data profiling。
- 当前 `MYSQL`、`POSTGRESQL` 的工程覆盖和测试资产最完整，但 correctness golden 只证明当前回归基线稳定，不等于官方语法全集或全部 relation/lineage 语义已经无缺口。`ORACLE` 已有初始 adaptor、Oracle token-event fallback 和 `INCOMPLETE_VERSIONED` versioned full-grammar；`SQLSERVER` 已有 adaptor、token-event fallback 和 SQL Server 2016/2017/2019/2022/2025 versioned full-grammar sample-data golden。
- 用户选择 `SQLSERVER` 时，应由 `adaptor-sqlserver` 接管；如果该模块未在 classpath 中，应返回“adaptor 未找到”错误，不应偷偷降级到其他数据库。用户选择 `ORACLE` 时，应由 `adaptor-oracle` 接管；如果该模块未在 classpath 中，同样应返回 adaptor 未找到。

## 2. OutputFormat

表示 CLI 输出格式，来自命令行 `--format` 或 YAML `output.format`。

```java
public enum OutputFormat {
  JSON,
  TABLE
}
```

| 值 | 含义 | 使用场景 |
| --- | --- | --- |
| `JSON` | 输出结构化 JSON。 | 机器消费、CI 集成、后续生成图。 |
| `TABLE` | 输出终端表格。 | 人工查看、调试、快速确认结果。 |

维护说明：

- `--format` 命令行参数优先级高于 YAML。
- 如果后续增加 `CSV`、`DOT`、`MERMAID`，应作为新增 enum 值，不改变 `JSON` 和 `TABLE` 行为。
- v1 不生成 Graphviz/Mermaid，避免输出范围扩散。

## 3. RelationType

表示最终关系的大类。它回答“这两张表之间是什么性质的关系”。

```java
public enum RelationType {
  FK_LIKE,
  CO_OCCURRENCE
}
```

| 值 | 含义 | 置信度特点 | 例子 |
| --- | --- | --- | --- |
| `FK_LIKE` | 类外键关系。一个表的列很可能引用另一个表的主键或唯一键。 | 可以从中到极高。显式 FK 最高，推断关系较低。 | `orders.user_id -> users.id` |
| `CO_OCCURRENCE` | 弱共现关系。可以是只有表共同出现，也可以是 SQL 明确给出列等值但无法判断 FK-like 方向。 | 通常较低；列级共现高于纯表级共现。 | `users -> audit_logs`；`inventory.product_id -> order_items.product_id` |

使用规则：

- 有明确列级引用证据时，优先输出 `FK_LIKE`。
- 只有表共同出现、没有明确列关系时，输出表级 `CO_OCCURRENCE`。
- 等值谓词两侧都是可解析物理列但方向不可靠时，输出列级 `CO_OCCURRENCE`。
- 方向不可靠时，不要强行输出列级 `FK_LIKE`。

反例：

- `orders.user_id` 命名像外键，但没有 JOIN、索引、唯一性、数据画像等辅助证据时，不应单独输出高置信 `FK_LIKE`。
- 一个复杂报表 SQL 同时读取 10 张表，不能把所有表对都当成强关系，只能按阈值生成低置信共现或跳过。

## 4. RelationSubType

表示关系的主要可信形态。它回答“这条关系主要是根据什么类型的证据成立的”。

```java
public enum RelationSubType {
  DECLARED_FK,
  DDL_DECLARED_FK,
  INFERRED_JOIN_FK,
  SUBQUERY_INFERRED_FK,
  PROFILE_SUPPORTED_FK,
  NAMING_SUPPORTED_FK,
  COLUMN_CO_OCCURRENCE,
  TABLE_CO_OCCURRENCE
}
```

| 值 | 所属 RelationType | 含义 | 典型 evidence | 例子 |
| --- | --- | --- | --- | --- |
| `DECLARED_FK` | `FK_LIKE` | 数据库元数据中明确存在外键。 | `METADATA_FOREIGN_KEY` | MySQL `information_schema` 显示 `orders.user_id` 引用 `users.id`。 |
| `DDL_DECLARED_FK` | `FK_LIKE` | DDL 文件中明确声明外键。 | `DDL_FOREIGN_KEY` | `FOREIGN KEY (user_id) REFERENCES users(id)`。 |
| `INFERRED_JOIN_FK` | `FK_LIKE` | JOIN / comma join 谓词加上足够方向证据后得到的推断 FK-like。方向证据可以来自 DDL/metadata/data-profile、“SQL 谓词 + 一侧 unique、一侧 non-unique”，或 top-level `namingEvidence` 中被该关系引用的唯一 `NAMING_MATCH` 方向提示。 | `SQL_LOG_JOIN` + `TARGET_UNIQUE`、metadata/index facts、`NAMING_MATCH` evidenceRef | `JOIN users u ON o.user_id = u.id`；若 `users.id` unique，或命名证据唯一指向 `orders.user_id -> users.id`，则输出该方向。 |
| `SUBQUERY_INFERRED_FK` | `FK_LIKE` | `IN` / `EXISTS` 谓词加上足够方向证据后得到的推断 FK-like。谓词 evidence 保留具体语法来源；命名只能作为已有谓词上的方向提示，不能单独创建关系。 | `SQL_LOG_SUBQUERY_IN`、`SQL_LOG_EXISTS` + unique/profile facts 或 `NAMING_MATCH` evidenceRef | `o.user_id IN (SELECT id FROM users)`；若 `users.id` unique 或命名方向唯一，则可推导 `orders.user_id -> users.id`。 |
| `PROFILE_SUPPORTED_FK` | `FK_LIKE` | 数据画像强支持的推断关系。 | `VALUE_CONTAINMENT_HIGH`、`TARGET_UNIQUE` | 抽样发现 `orders.user_id` 99.5% 都存在于 `users.id`。 |
| `NAMING_SUPPORTED_FK` | `FK_LIKE` | 命名方向启发式支持的 FK-like；当前实现通常保留 `INFERRED_JOIN_FK` / `SUBQUERY_INFERRED_FK` subtype，并用 `NAMING_MATCH` evidenceRef 标明方向来源。 | 既有 SQL predicate evidence + top-level `namingEvidence` 引用 | `user_id` / `id` 这类名称必须先进入命名证据池，并且同端点已有 JOIN/EXISTS/IN 等 SQL 谓词候选，才能作为方向提示。 |
| `COLUMN_CO_OCCURRENCE` | `CO_OCCURRENCE` | SQL 给出明确列等值，但无法可靠判断 FK-like 方向。 | 当前生产 parser 通常保留具体 `SQL_LOG_JOIN` / `SQL_LOG_EXISTS` / `SQL_LOG_SUBQUERY_IN`；`SQL_LOG_COLUMN_CO_OCCURRENCE` 仅作兼容保留。 | `warehouse_inventory.product_id = order_items.product_id`。 |
| `TABLE_CO_OCCURRENCE` | `CO_OCCURRENCE` | 只能证明两个表共现，不能证明列级引用。当前生产 parser 默认不主动输出表级共现 evidence。 | `SQL_LOG_TABLE_CO_OCCURRENCE` 仅作兼容保留。 | 外部导入或显式 opt-in 审计场景发现 `FROM users, audit_logs` 且没有连接条件。 |

多证据时的主导优先级：

1. `DECLARED_FK`
2. `DDL_DECLARED_FK`
3. `PROFILE_SUPPORTED_FK`
4. `INFERRED_JOIN_FK`
5. `SUBQUERY_INFERRED_FK`
6. `NAMING_SUPPORTED_FK`
7. `COLUMN_CO_OCCURRENCE`
8. `TABLE_CO_OCCURRENCE`

维护说明：

- `RelationSubType` 不替代 evidence 列表。它只放“主导形态”。
- 一条关系可以有很多 evidence，但只能有一个 `relationSubType`。
- 显式数据库 FK 永远不应被数据画像或日志证据覆盖成其他 subtype。
- `PROFILE_SUPPORTED_FK` 是“画像强支持的推断关系”，不是“画像发现的新关系”。画像默认只对已有候选运行。

## 5. LineageFlowKind

表示字段血缘中的值流动性质。它只用于 `DataLineageCandidate`，不用于 `RelationshipCandidate`。

```java
public enum LineageFlowKind {
  VALUE,
  CONTROL
}
```

| 值 | 含义 | 例子 |
| --- | --- | --- |
| `VALUE` | 源字段的值参与目标字段写入。 | `SET users.total_spent = SUM(orders.pay_amount)` 输出 `orders.pay_amount -> users.total_spent`。 |
| `CONTROL` | 源字段不一定成为目标值，但控制目标值如何被选择或分类。 | `SET status = CASE WHEN risk.score > 80 THEN 'HOLD' ELSE 'OK' END` 输出 `risk.score -> status`，flow kind 为 `CONTROL`。 |

维护说明：

- `VALUE` 和 `CONTROL` 可以指向同一个 target，但表示不同语义。
- v1 不输出 parameter/literal/json path 来源，因此 `CONTROL` source 仍必须是物理 `table.column`。
- `LineageFlowKind` 不参与关系置信度计算。

## 6. LineageTransformType

表示字段血缘中的表达式变换形态。它只解释字段值如何流动，不代表表关系类型。

```java
public enum LineageTransformType {
  DIRECT,
  AGGREGATE,
  CUMULATIVE,
  COALESCE,
  CASE_WHEN,
  CONCAT_FORMAT,
  ARITHMETIC,
  FUNCTION_CALL,
  WINDOW_DERIVED,
  UNKNOWN_EXPRESSION
}
```

| 值 | 默认分 | 含义 | 例子 |
| --- | ---: | --- | --- |
| `DIRECT` | 0.90 | 直接字段赋值。 | `SET a.x = b.y`。 |
| `AGGREGATE` | 0.80 | 聚合结果写入目标。 | `SUM(o.pay_amount) -> users.total_spent`。 |
| `CUMULATIVE` | 0.80 | 运行累计、累计分布、running total 等累计聚合衍生值写入目标。 | `@running_sum := @running_sum + weight` 后写入 `cdf_end`。 |
| `COALESCE` | 0.75 | 多来源兜底选择。 | `COALESCE(sm.avg_cost, wi.default_unit_cost)`。 |
| `CASE_WHEN` | 0.65 / 0.55 | 条件选择；VALUE 为 0.65，CONTROL 为 0.55。 | `CASE WHEN risk_score > 80 THEN ...`。 |
| `CONCAT_FORMAT` | 0.70 | 字符串拼接或格式化。 | `FORMAT('Country: %s', u.country_code)`。 |
| `ARITHMETIC` | 0.75 | 数值运算。 | `stock_reserved + oi.quantity`。 |
| `FUNCTION_CALL` | 0.65 | 其它函数调用。 | `LOWER(email)`。 |
| `WINDOW_DERIVED` | 0.50 | 窗口函数派生。 | `DENSE_RANK() OVER (...)`。 |
| `UNKNOWN_EXPRESSION` | 0.35 | 能抽出字段来源，但表达式类型不确定。 | 方言函数或复杂表达式。 |

维护说明：

- `LineageTransformType` 的 confidence 不参与 `EvidenceType` 的关系分数。
- 新增 transform type 时，必须同步更新 JSON 输出测试、correctness fixture golden 和本 enum 文档。
- 如果表达式包含聚合函数，v1 优先以聚合函数参数作为 VALUE source，过滤条件字段不作为 VALUE source。

## 7. EvidenceType

表示单条证据的类型。它回答“这条证据从哪里来、说明了什么”。

```java
public enum EvidenceType {
  METADATA_FOREIGN_KEY,
  DDL_FOREIGN_KEY,
  VIEW_JOIN,
  PROCEDURE_JOIN,
  TRIGGER_REFERENCE,
  SQL_LOG_JOIN,
  SQL_LOG_SUBQUERY_IN,
  SQL_LOG_EXISTS,
  SQL_LOG_COLUMN_CO_OCCURRENCE,
  SQL_LOG_TABLE_CO_OCCURRENCE,
  NAMING_MATCH,
  SOURCE_INDEX,
  TARGET_UNIQUE,
  COLUMN_TYPE_COMPATIBLE,
  VALUE_CONTAINMENT_HIGH,
  VALUE_OVERLAP_HIGH,
  NEGATIVE_VALUE_MISMATCH,
  TRANSITIVE_PATH,
  REPEATED_OBSERVATION
}
```

### 5.1 结构类证据

| 值 | 含义 | 默认分 | 何时产生 |
| --- | --- | ---: | --- |
| `METADATA_FOREIGN_KEY` | 数据库元数据中的显式外键。 | 0.98 | JDBC 读取到 FK 约束。 |
| `DDL_FOREIGN_KEY` | DDL 文件中的外键。 | 0.90 | 解析 `CREATE TABLE` 或 `ALTER TABLE` 得到 FK。 |
| `SOURCE_INDEX` | source 列存在索引。 | 0.10 | 元数据或 DDL 中发现 source 列有普通索引。 |
| `TARGET_UNIQUE` | target 列唯一。 | 0.18 | target 列是 PK、unique constraint 或 unique index。 |
| `COLUMN_TYPE_COMPATIBLE` | 两列定义兼容。 | 0.08 | 类型、长度、精度等兼容。 |

例子：

```text
orders.user_id -> users.id
Evidence:
- TARGET_UNIQUE: users.id is primary key
- SOURCE_INDEX: orders.user_id has index idx_orders_user_id
- COLUMN_TYPE_COMPATIBLE: both columns are BIGINT
```

维护说明：

- `SOURCE_INDEX`、`TARGET_UNIQUE`、`COLUMN_TYPE_COMPATIBLE` 通常是辅助证据，不能单独证明关系。
- `METADATA_FOREIGN_KEY` 是最强证据，最低最终置信度不低于 0.95。

### 5.2 SQL/对象定义证据

| 值 | 含义 | 默认分 | 何时产生 |
| --- | --- | ---: | --- |
| `VIEW_JOIN` | 视图定义中的列级谓词证据。 | 0.72 | 证据比普通日志稳定，但方向仍由 DDL/metadata/profile/unique 等证据决定。 |
| `PROCEDURE_JOIN` | 存储过程中的列级谓词证据。 | 0.70 | 证据来自稳定业务逻辑，但不单独证明 FK-like 方向。 |
| `TRIGGER_REFERENCE` | 触发器中的物理表列引用/谓词证据。 | 0.65 | `NEW/OLD` 只作为 trigger pseudo rowset 过滤，不作为物理 endpoint。 |
| `SQL_LOG_JOIN` | SQL JOIN / comma join 等值谓词证据。 | 0.55 | 当前 typed SQL parser 生成；没有方向证据时输出 `CO_OCCURRENCE`。 |
| `SQL_LOG_SUBQUERY_IN` | `IN (SELECT ...)` / tuple IN 谓词证据。 | 0.58 | 当前 typed SQL parser 生成；方向由 unique/profile/DDL/metadata 等证据决定。 |
| `SQL_LOG_EXISTS` | correlated `EXISTS` / `NOT EXISTS` 谓词证据。 | 0.58 | 当前 typed SQL parser 生成；保留 EXISTS evidence，不泛化成普通 JOIN。 |
| `SQL_LOG_COLUMN_CO_OCCURRENCE` | 泛化列级弱共现。 | 0.40 | `RESERVED_COMPATIBILITY / NOT_PRODUCED`。用于历史/外部导入或无法保留具体谓词语法形态的兼容场景；当前生产 parser 不主动产出。 |
| `SQL_LOG_TABLE_CO_OCCURRENCE` | SQL 日志中的表共现。 | 0.25 | `RESERVED_COMPATIBILITY / NOT_PRODUCED`。用于历史/外部导入或显式 opt-in 审计场景；当前生产 parser 不主动产出。 |
| `REPEATED_OBSERVATION` | 同一关系在同一证据类型/来源下重复出现后的派生加分。 | 0.00-0.10 | `RelationshipMerger` 发现同组 evidence 的 `count > 1`。 |

例子：

```sql
SELECT *
FROM orders o
WHERE o.user_id IN (SELECT u.id FROM users u);
```

当前 typed SQL parser 产生：

```text
EvidenceType: SQL_LOG_SUBQUERY_IN
RelationSubType: SUBQUERY_INFERRED_FK 或 COLUMN_CO_OCCURRENCE
attributes.joinKind: IN_SUBQUERY
```

维护说明：

- 同样是 JOIN，来自 view 的 evidence 通常比来自日志的 evidence 更强，因为 view 更稳定。
- 当前 typed SQL parser 优先保留具体谓词形态：JOIN / comma join 为 `SQL_LOG_JOIN`，correlated `EXISTS` 为 `SQL_LOG_EXISTS`，`IN (SELECT ...)` / tuple IN 为 `SQL_LOG_SUBQUERY_IN`。这三类是 `SQL_LOG_COLUMN_CO_OCCURRENCE` 在生产解析路径上的实际替代：它们同样证明列级关联，但比泛化 co-occurrence 多保留了 SQL 语法来源。
- `SQL_LOG_COLUMN_CO_OCCURRENCE` 是 `RESERVED_COMPATIBILITY / NOT_PRODUCED`，只为历史结果、外部 adaptor 导入、或无法保留具体谓词形态的兼容输入保留；生产 parser / extractor 不主动降级生成它。
- `SQL_LOG_TABLE_CO_OCCURRENCE` 也是 `RESERVED_COMPATIBILITY / NOT_PRODUCED`，但它没有等价的现役替代 evidence。无列级谓词的多表同现当前不生成正式 relationship；如果存在明确谓词，则由 `SQL_LOG_JOIN` / `SQL_LOG_EXISTS` / `SQL_LOG_SUBQUERY_IN` 代表。
- 解析失败不要制造 evidence，应记录 `PARSE_WARNING`。
- `REPEATED_OBSERVATION` 不由 SQL parser、DDL parser 或 adaptor 直接产生，只能由 core 归并阶段产生。它必须带 `attributes.count`、`attributes.maxScore`、`attributes.formula`、`attributes.baseEvidenceType`，用于解释递减增益和绝对上限。

### 5.3 命名和数据画像证据

| 值 | 含义 | 默认分 | 何时产生 |
| --- | --- | ---: | --- |
| `NAMING_MATCH` | 命名方向规则匹配。 | 0.20 | 完整证据保存在 top-level `namingEvidence`；relationship 只能引用它。attributes 包含 `evidenceRef`、`namingRule`、`suggestedSourceEndpoint`、`suggestedTargetEndpoint`、`matchedColumn`、`matchedTable`、`directionHint=true`。 |
| `VALUE_CONTAINMENT_HIGH` | source 值域高度包含于 target。 | 0.30 | 抽样包含率高于阈值。 |
| `VALUE_OVERLAP_HIGH` | source 与 target 值重合率较高。 | 0.20 | 抽样重合率高于阈值。 |
| `NEGATIVE_VALUE_MISMATCH` | 数据画像显示明显不匹配。 | -0.30 | 大量 source 值不在 target 中，或类型/基数明显不合理。 |
| `TRANSITIVE_PATH` | 已确认有向证据图上的传递路径推导。 | 路径置信度 | `derivedPaths.enabled=true` 时由 core 推导产生；不表示直接物理 FK 或直接字段写入。 |

维护说明：

- `NAMING_MATCH` 不能单独生成关系；它先作为 top-level naming evidence 进入证据池，relationship 只能通过 `evidenceRef` 引用它，并在方向唯一时参与 FK-like 方向推导。
- `VALUE_CONTAINMENT_HIGH` 是强辅助证据；当前由 MySQL/PostgreSQL/Oracle/SQL Server live profiler 在 `dataProfile.enabled=true`、候选受限、权限允许且 containment gate 满足时产出。
- `VALUE_OVERLAP_HIGH` 当前也由四个 live profiler 产出；它表示值域重合较高，但不如 `VALUE_CONTAINMENT_HIGH` 强。
- `NEGATIVE_VALUE_MISMATCH` 是负向证据，会降低最终分数，而不是删除关系；当前只在 live sample 非 partial、样本规模和 missing ratio gate 满足时产出。
- 离线 seed `INSERT` 画像仍等待 typed literal `INSERT ... VALUES` sample event；生产代码不得用 regex/token span 扫描 SQL 伪造该 evidence。
- `TRANSITIVE_PATH` 只出现在 `derivedRelationships` / `derivedDataLineages`，或作为 derived `namingEvidence` 的推导说明；默认关闭，不参与直接 relationship / lineage 的 correctness golden。
- EvidenceType 的默认分值、定分理由、合并公式和完整 SQL 算例，以 [Phase 2：核心模型和评分详细设计](phase-02-core-model-scoring.md) 的“置信度计算”章节为准。维护枚举时必须同步检查该章节，避免 enum 文档和评分模型出现两套解释。

## 8. EvidenceSourceType

表示 evidence 的来源类别。它和 `EvidenceType` 不同：`EvidenceType` 说明“证据是什么”，`EvidenceSourceType` 说明“证据从哪里来”。

建议 Java enum：

```java
public enum EvidenceSourceType {
  METADATA,
  DDL_FILE,
  DATABASE_DDL,
  DATABASE_OBJECT,
  NATIVE_LOG,
  PLAIN_SQL,
  DATA_PROFILE,
  NAMING_HEURISTIC,
  INFERENCE
}
```

| 值 | 含义 | 示例 |
| --- | --- | --- |
| `METADATA` | JDBC 从系统表或 catalog 读取到的信息。 | `information_schema.KEY_COLUMN_USAGE` |
| `DDL_FILE` | 本地 DDL 文件。 | `schema.sql` |
| `DATABASE_DDL` | 从数据库内读取到的真实 DDL 定义。 | MySQL `SHOW CREATE TABLE` |
| `DATABASE_OBJECT` | 过程、函数、视图、触发器定义。 | `view user_orders` |
| `NATIVE_LOG` | 数据库原生日志。 | MySQL slow log、PostgreSQL statement log |
| `PLAIN_SQL` | 清洗后的纯 SQL 文本文件。 | `app-sql.sql` |
| `DATA_PROFILE` | 数据画像查询结果。 | `99.5% sampled values matched` |
| `NAMING_HEURISTIC` | 命名规则推断。 | `user_id` -> `users.id` |
| `INFERENCE` | core 在已有证据图上做的传递推导。 | `a.r -> b.s -> c.t` |

维护说明：

- JSON 中 evidence 的 `source` 可以包含更具体的字符串，例如 `mysql-slow-log`；但内部推荐同时保留 `EvidenceSourceType`，便于统计和过滤。
- `DATABASE_DDL` 与 `DDL_FILE` 都可能产生 `DDL_FOREIGN_KEY`、`SOURCE_INDEX`、`TARGET_UNIQUE`；区别只在来源：前者来自 live catalog 反查出的表定义，后者来自用户提供的文件。
- 不要把文件路径作为 enum 值。

## 9. StatementSourceType

表示一条 SQL 语句来自哪里，用于选择 evidence 类型和解析策略。

```java
public enum StatementSourceType {
  DDL_FILE,
  PROCEDURE,
  FUNCTION,
  VIEW,
  MATERIALIZED_VIEW,
  TRIGGER,
  EVENT,
  RULE,
  PACKAGE,
  PACKAGE_BODY,
  MIGRATION,
  NATIVE_LOG,
  PLAIN_SQL
}
```

| 值 | 含义 | 解析后的常见 evidence |
| --- | --- | --- |
| `DDL_FILE` | DDL 文件中的语句。 | `DDL_FOREIGN_KEY`、`SOURCE_INDEX`、`TARGET_UNIQUE` |
| `PROCEDURE` | 存储过程 body。 | `SQL_LOG_JOIN`、`SQL_LOG_SUBQUERY_IN`、`SQL_LOG_EXISTS` |
| `FUNCTION` | 函数 body。 | `SQL_LOG_JOIN`、`SQL_LOG_SUBQUERY_IN`、`SQL_LOG_EXISTS` |
| `VIEW` | 视图定义 SQL。 | `VIEW_JOIN` |
| `MATERIALIZED_VIEW` | 物化视图定义 SQL。 | `VIEW_JOIN` |
| `TRIGGER` | 触发器 body。 | `TRIGGER_REFERENCE` |
| `EVENT` | MySQL scheduler event body。 | `SQL_LOG_JOIN`、`SQL_LOG_SUBQUERY_IN`、`SQL_LOG_EXISTS` |
| `RULE` | PostgreSQL rewrite rule definition。 | `SQL_LOG_JOIN`、`SQL_LOG_SUBQUERY_IN`、`SQL_LOG_EXISTS` |
| `PACKAGE` | Oracle package specification，后续 adaptor 预留。 | `SQL_LOG_JOIN`、`SQL_LOG_SUBQUERY_IN`、`SQL_LOG_EXISTS` |
| `PACKAGE_BODY` | Oracle package body，后续 adaptor 预留。 | `SQL_LOG_JOIN`、`SQL_LOG_SUBQUERY_IN`、`SQL_LOG_EXISTS` |
| `MIGRATION` | migration 脚本中的 SQL。 | `SQL_LOG_JOIN`、`SQL_LOG_SUBQUERY_IN`、`SQL_LOG_EXISTS` |
| `NATIVE_LOG` | 数据库原生日志提取出的 SQL。 | `SQL_LOG_JOIN`、`SQL_LOG_SUBQUERY_IN`、`SQL_LOG_EXISTS` |
| `PLAIN_SQL` | 清洗后的纯 SQL 文本。 | `SQL_LOG_JOIN`、`SQL_LOG_SUBQUERY_IN`、`SQL_LOG_EXISTS` |

维护说明：

- `FUNCTION` 和 `PROCEDURE` 未来可共用 `PROCEDURE_JOIN` evidence，因为二者都属于持久化数据库逻辑；当前生产 parser 仍保留具体 SQL predicate evidence。
- `VIEW`、`MATERIALIZED_VIEW` 的物理列谓词已经提升为 `VIEW_JOIN`；`RULE` 当前仍保留具体 SQL predicate evidence。
- `EVENT`、`PACKAGE`、`PACKAGE_BODY` 属于持久化数据库逻辑，后续可按 procedure/function 专属 evidence 处理。
- `MIGRATION` 不是数据库持久对象，证据来源按 `PLAIN_SQL` 处理。
- `TRIGGER` 及带 `routineReturnsTrigger=true` 的 PostgreSQL trigger function 使用 `TRIGGER_REFERENCE`；解析失败必须记录 warning，不能静默丢弃。

## 10. DatabaseObjectType

表示从数据库或文件读取到的对象定义类型。

```java
public enum DatabaseObjectType {
  PROCEDURE,
  FUNCTION,
  VIEW,
  MATERIALIZED_VIEW,
  TRIGGER,
  EVENT,
  RULE,
  PACKAGE,
  PACKAGE_BODY
}
```

| 值 | MySQL 对应对象 | PostgreSQL 对应对象 | 说明 |
| --- | --- | --- | --- |
| `PROCEDURE` | procedure | procedure | 持久化过程逻辑。 |
| `FUNCTION` | function | function | PostgreSQL 中很多 trigger 逻辑也在 function 中。 |
| `VIEW` | view | view | 视图 SQL 通常是稳定 JOIN 证据。 |
| `MATERIALIZED_VIEW` | 不适用 | materialized view | PostgreSQL `pg_matviews` 定义。 |
| `TRIGGER` | trigger | trigger + trigger function | 触发器中的引用可表达写入关系。 |
| `EVENT` | scheduler event | 不适用 | MySQL event body 可包含关系 SQL。 |
| `RULE` | 不适用 | rewrite rule | PostgreSQL rule 可包含查询重写 SQL。 |
| `PACKAGE` | 不适用 | 不适用 | Oracle package spec 预留。 |
| `PACKAGE_BODY` | 不适用 | 不适用 | Oracle package body 预留。 |

维护说明：

- PostgreSQL trigger 需要关联 `TRIGGER` 与对应 `FUNCTION`，不要只看 trigger 元数据。
- 不要复用 `VIEW` 表达 materialized view；物化语义不同，虽然当前 JOIN evidence 同样映射为 `VIEW_JOIN`。

## 10.1 StructuredParseEventType

表示 token-event / full-grammar 结构化解析阶段产生的中间事件，不等价于最终 relationship 或 Data Lineage。

```java
public enum StructuredParseEventType {
  TABLE_REFERENCE,
  COLUMN_EQUALITY,
  ROWSET_REFERENCE,
  PREDICATE_EQUALITY,
  JOIN_USING_COLUMNS,
  EXISTS_PREDICATE,
  IN_SUBQUERY_PREDICATE,
  TUPLE_IN_SUBQUERY_PREDICATE,
  CTE_DECLARATION,
  IGNORED_ROWSET,
  LOCAL_TEMP_TABLE_DECLARATION,
  TRIGGER_TARGET_TABLE,
  TRIGGER_PSEUDO_ROWSET,
  WRITE_TARGET,
  UPDATE_ASSIGNMENT,
  INSERT_SELECT_MAPPING,
  MERGE_WRITE_MAPPING,
  PROJECTION_ITEM,
  EXPRESSION_SOURCE,
  DDL_FOREIGN_KEY,
  DDL_INDEX,
  DYNAMIC_SQL
}
```

| 值 | 含义 |
| --- | --- |
| `TABLE_REFERENCE` | legacy/bootstrap 表引用事件。当前 semantic extractor 的主输入是归一后的 `ROWSET_REFERENCE`。 |
| `COLUMN_EQUALITY` | legacy/bootstrap 列等值事件。当前 builder 会归一为 `PREDICATE_EQUALITY`。 |
| `ROWSET_REFERENCE` | 物理 rowset 或写目标 rowset 引用，包括 table、alias、joinKind、source span 等 attributes。 |
| `PREDICATE_EQUALITY` | 明确 `alias.column = alias.column` 谓词，供 `StructuredRelationshipExtractor` 判断 FK-like 或列级弱共现。 |
| `JOIN_USING_COLUMNS` | `JOIN ... USING (...)` 的列列表事件，不把列名当作 rowset。 |
| `EXISTS_PREDICATE` | correlated `EXISTS` 中可解析的相关等值谓词。 |
| `IN_SUBQUERY_PREDICATE` | scalar `IN (SELECT ...)` 谓词；只有外层是裸列、子查询 SELECT list 是裸列、并带 `verifiedColumnSubquery=true` 时才可被 relationship extractor 消费。 |
| `TUPLE_IN_SUBQUERY_PREDICATE` | tuple `IN` 谓词，例如 `(a,b) IN (SELECT x,y ...)`；左右 tuple 数量必须一致且每一项都是裸列。 |
| `CTE_DECLARATION` | CTE 名称和输出列声明，用于 ignored rowset 和 projection lineage。 |
| `IGNORED_ROWSET` | CTE、derived table、function rowset 等不应作为物理表输出的 rowset。 |
| `LOCAL_TEMP_TABLE_DECLARATION` | SQL 语法明确创建的本地临时表；不能按名字猜测临时表。 |
| `TRIGGER_TARGET_TABLE` | trigger 所属目标表。 |
| `TRIGGER_PSEUDO_ROWSET` | trigger 中 `NEW` / `OLD` pseudo rowset 到目标表的映射。 |
| `WRITE_TARGET` | DML 写目标表/alias。 |
| `UPDATE_ASSIGNMENT` | `UPDATE/MERGE UPDATE SET` 的目标列与表达式来源事件。 |
| `INSERT_SELECT_MAPPING` | `INSERT INTO (...) SELECT ...` 或等价 insert mapping。 |
| `MERGE_WRITE_MAPPING` | `MERGE` update/insert 写入映射。 |
| `PROJECTION_ITEM` | CTE/derived/select item 的输出列、来源列和 transform 信息。 |
| `EXPRESSION_SOURCE` | 表达式内来源列和 transform 分析事件。 |
| `DDL_FOREIGN_KEY` | DDL event visitor 识别出的外键关系事件，包括 table-level FK、inline `REFERENCES`、`ALTER TABLE ADD CONSTRAINT`。 |
| `DDL_INDEX` | DDL event visitor 识别出的索引/唯一性事件，例如 source index、primary key、unique constraint、unique index。 |
| `DYNAMIC_SQL` | 为可静态还原的动态 SQL 事件预留。当前不可还原时输出 warning。 |

维护说明：

- SQL relationship extractor 当前消费 `ROWSET_REFERENCE`、`PREDICATE_EQUALITY`、`JOIN_USING_COLUMNS`、`EXISTS_PREDICATE`、`IN_SUBQUERY_PREDICATE`、`TUPLE_IN_SUBQUERY_PREDICATE`、`PROJECTION_ITEM` 和 scope events。
- literal filter、literal `IN`、`LIKE`、表达式 tuple、aggregate/HAVING/filter 字段不能通过这些 event 伪造成关系；这类过滤由结构属性和 endpoint 类型决定，不按特殊表名/列名判断。
- Data Lineage extractor 当前消费写入映射、projection 和 local temp scope events。
- DDL relationship extractor 只消费 `DDL_FOREIGN_KEY` 和 `DDL_INDEX`。

## 11. LogFormatHint

表示日志文件格式提示。用于 `SqlLogExtractor` 判断如何从文件中抽取 SQL。

```java
public enum LogFormatHint {
  AUTO,
  PLAIN_SQL,
  MYSQL_GENERAL_LOG,
  MYSQL_SLOW_LOG,
  POSTGRES_STATEMENT_LOG
}
```

| 值 | 含义 | 使用场景 |
| --- | --- | --- |
| `AUTO` | 自动识别日志格式。 | 用户未显式指定格式。 |
| `PLAIN_SQL` | 清洗后的纯 SQL 文件。 | 每条 SQL 用分号或空行分隔。 |
| `MYSQL_GENERAL_LOG` | MySQL general log。 | 从 `Query` 行抽取 SQL。 |
| `MYSQL_SLOW_LOG` | MySQL slow log。 | 跳过 `# Time` 等元信息后抽取 SQL。 |
| `POSTGRES_STATEMENT_LOG` | PostgreSQL statement log。 | 从 `statement:` 或 `execute ...:` 后抽取 SQL。 |

维护说明：

- `AUTO` 识别失败时不应猜测，应给出 warning 或要求用户指定。
- 原生日志解析只负责抽 SQL，不负责生成关系；关系解析交给 SQL parser。
- 后续新增云厂商日志格式时，新增 enum 值，例如 `ALIYUN_RDS_MYSQL_AUDIT_LOG`。

## 12. DirectionConfidence

表示解析器对关系方向的把握程度。它不直接输出给最终用户，但会影响是否生成列级 `FK_LIKE`。

```java
public enum DirectionConfidence {
  CERTAIN,
  HIGH,
  MEDIUM,
  LOW,
  AMBIGUOUS
}
```

| 值 | 含义 | Core 处理 |
| --- | --- | --- |
| `CERTAIN` | 方向确定。通常来自显式 FK。 | 可以输出列级 `FK_LIKE`。 |
| `HIGH` | 方向高度可信。通常来自 target unique + source 命名/索引。 | 可以输出列级 `FK_LIKE`。 |
| `MEDIUM` | 方向有一定依据，但不充分。 | 可结合其他 evidence；不足时降级。 |
| `LOW` | 方向依据很弱。 | 通常不生成列级 `FK_LIKE`。 |
| `AMBIGUOUS` | 方向不明确或多义。 | 退化为表级 `CO_OCCURRENCE` 并记录 warning。 |

例子：

- `orders.user_id = users.id`，`users.id` 是 PK：`HIGH`。
- 显式 FK `orders.user_id -> users.id`：`CERTAIN`。
- `a.code = b.code`，两边都非 unique：`AMBIGUOUS`。

维护说明：

- 不要把 `LOW` 的方向关系直接输出为强 `FK_LIKE`。
- `DirectionConfidence` 是解析中间状态，不等于最终 confidence 分数。

## 13. WarningType

表示扫描过程中出现的非致命问题类型。

```java
public enum WarningType {
  CONFIG_WARNING,
  PERMISSION_WARNING,
  PARSE_WARNING,
  PROFILE_WARNING,
  AMBIGUOUS_RELATION_WARNING,
  ADAPTOR_CAPABILITY_WARNING
}
```

| 值 | 含义 | 例子 | 是否中断 |
| --- | --- | --- | --- |
| `CONFIG_WARNING` | 配置可用但存在可疑项。 | include/exclude 同时命中同一表。 | 否 |
| `PERMISSION_WARNING` | 权限不足导致某些来源不可读。 | 不能读取 MySQL routine definition。 | 否 |
| `PARSE_WARNING` | 单条 SQL/DDL 解析失败。 | parser 不支持某段 PL/pgSQL。 | 否 |
| `PROFILE_WARNING` | 数据画像部分失败或跳过。 | 某候选画像查询超时。 | 否 |
| `AMBIGUOUS_RELATION_WARNING` | 关系方向或列映射不明确。 | `a.x = b.y` 两侧都非 unique。 | 否 |
| `ADAPTOR_CAPABILITY_WARNING` | adaptor 不支持用户开启的某项能力。 | adaptor 不支持 native log。 | 视配置而定 |

维护说明：

- warning 是“扫描还能继续”的问题。
- 不可恢复错误应使用错误码退出，不要只给 warning。
- 解析/提取失败应把原始失败 SQL/DDL 放入 `WarningMessage.attributes.rawStatement`；异常类型放入 `exceptionClass`，来源类型放入 `statementSourceType`。
- parser 正常返回空关系不自动等价为 `PARSE_WARNING`，因为很多 SQL 不包含可用关系证据。
- warning detail 不应包含 password 或采样到的真实业务值。

## 14. WarningSeverity

表示 warning 的严重程度。

```java
public enum WarningSeverity {
  INFO,
  WARN,
  ERROR
}
```

| 值 | 含义 | 例子 |
| --- | --- | --- |
| `INFO` | 诊断信息，不影响结果可信度。 | data profile disabled by config。 |
| `WARN` | 有能力缺失或局部失败，结果可能不完整。 | 某个 SQL 文件部分语句解析失败。 |
| `ERROR` | 单个来源或子任务失败，但整体扫描仍可继续。 | 读取对象定义失败，但元数据扫描成功。 |

维护说明：

- `ERROR` severity 不等于进程退出码非 0。
- 只有配置无法读取、数据库无法连接、输出无法写入等不可恢复错误才应让 CLI 返回非零。

## 15. ErrorCode

CLI 退出码建议用 enum 或常量集中管理。虽然 Java 进程退出码是 int，但代码中应避免散落魔法数字。

```java
public enum ErrorCode {
  OK(0),
  CONFIG_FILE_ERROR(1),
  CONFIG_FORMAT_ERROR(2),
  ARGUMENT_ERROR(3),
  ADAPTOR_ERROR(4),
  INPUT_FILE_ERROR(5),
  DATABASE_CONNECTION_ERROR(10),
  SCAN_RUNTIME_ERROR(11),
  OUTPUT_WRITE_ERROR(12)
}
```

| 值 | 退出码 | 含义 |
| --- | ---: | --- |
| `OK` | 0 | 成功。 |
| `CONFIG_FILE_ERROR` | 1 | 配置文件不存在、不可读。 |
| `CONFIG_FORMAT_ERROR` | 2 | YAML 格式错误或必填字段缺失。 |
| `ARGUMENT_ERROR` | 3 | 命令行参数错误。 |
| `ADAPTOR_ERROR` | 4 | adaptor 未找到、冲突或加载失败。 |
| `INPUT_FILE_ERROR` | 5 | DDL、日志、对象定义文件不存在或不可读。 |
| `DATABASE_CONNECTION_ERROR` | 10 | JDBC 连接失败。 |
| `SCAN_RUNTIME_ERROR` | 11 | 扫描运行中发生不可恢复错误。 |
| `OUTPUT_WRITE_ERROR` | 12 | 输出文件写入失败。 |

维护说明：

- 已使用的退出码不要复用给新含义。
- warning 不应直接改变退出码，除非配置要求“warning as error”。

## 16. AdaptorCapability

表示 adaptor 支持的能力。当前设计中也可以用 boolean record，但为了第三方 adaptor 扩展和配置校验，建议实现为 enum set。

```java
public enum AdaptorCapability {
  METADATA,
  DDL_PARSING,
  DATABASE_OBJECTS,
  NATIVE_LOGS,
  DATA_PROFILING,
  EVIDENCE_WEIGHT_ADJUSTMENT
}
```

| 值 | 含义 |
| --- | --- |
| `METADATA` | 支持 JDBC 元数据采集。 |
| `DDL_PARSING` | 支持该数据库方言的 DDL 解析或预处理。 |
| `DATABASE_OBJECTS` | 支持读取过程、函数、视图、触发器定义。 |
| `NATIVE_LOGS` | 支持该数据库原生日志格式提取 SQL。 |
| `DATA_PROFILING` | 支持对候选关系做数据画像。 |
| `EVIDENCE_WEIGHT_ADJUSTMENT` | 支持数据库特定 evidence 权重修正。 |

维护说明：

- capabilities 用于提前校验用户配置。
- 用户开启 adaptor 不支持的能力时，应给明确错误或 warning。
- 不要通过 capabilities 表示版本号；版本兼容应由 adaptor 自己判断。

## 17. ScanSourceKind

表示用户配置中启用的数据来源类型。

```java
public enum ScanSourceKind {
  METADATA,
  DDL,
  OBJECTS,
  LOGS,
  DATA_PROFILE
}
```

| 值 | 对应配置 | 含义 |
| --- | --- | --- |
| `METADATA` | `sources.metadata` | JDBC 元数据采集。 |
| `DDL` | `sources.ddl` | 本地 DDL 文件。 |
| `OBJECTS` | `sources.objects` | 过程、函数、视图、触发器定义。 |
| `LOGS` | `sources.logs` | 原生日志或纯 SQL 文本。 |
| `DATA_PROFILE` | `sources.dataProfile` | 可选数据画像。 |

维护说明：

- `DATA_PROFILE` 依赖已有候选关系，不能作为第一批独立扫描来源。
- 至少应启用 `METADATA`、`DDL`、`OBJECTS`、`LOGS` 中的一种；只开启 `DATA_PROFILE` 没有意义，应配置失败。

## 18. 枚举之间的关系

一次典型关系输出：

```json
{
  "source": { "table": "orders", "column": "user_id" },
  "target": { "table": "users", "column": "id" },
  "relationType": "FK_LIKE",
  "relationSubType": "PROFILE_SUPPORTED_FK",
  "confidence": 0.91,
  "evidence": [
    {
      "type": "SQL_LOG_JOIN",
      "sourceType": "NATIVE_LOG",
      "score": 0.55
    },
    {
      "type": "VALUE_CONTAINMENT_HIGH",
      "sourceType": "DATA_PROFILE",
      "score": 0.30
    }
  ]
}
```

解释：

- `RelationType = FK_LIKE`：最终关系像外键。
- `RelationSubType = PROFILE_SUPPORTED_FK`：主导可信形态是数据画像强支持。
- `EvidenceType = SQL_LOG_JOIN`：一条证据来自日志 JOIN。
- `EvidenceType = VALUE_CONTAINMENT_HIGH`：另一条证据来自数据画像。
- `EvidenceSourceType` 表示证据来源类别，不替代 evidence type。

## 19. 实现检查清单

- 每个 enum 都有 JSON 序列化/反序列化测试。
- 配置中的小写值能映射到内部大写 enum。
- 未知 enum 值给出明确错误，不静默忽略。
- 输出 JSON 使用稳定 enum 字符串。
- `RelationSubType` 根据主导证据优先级计算，不由最后一条 evidence 覆盖。
- 负向 evidence 不应变成 `RelationSubType`。
- warning severity 不直接决定进程退出码。
- 预留数据库类型没有 adaptor 时必须明确报错。
