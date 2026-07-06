# ProjectionTraceResolver 与字段血缘详细设计

## 1. 当前定位

本文保留历史文件名，避免已有 Markdown 链接失效；当前代码中的实现类已经不是 `SqlLineageResolver`，而是：

```text
core.lineage.ProjectionTraceResolver
```

`ProjectionTraceResolver` 不是 SQL parser，也不是最终 Data Lineage 输出器。它是字段血缘链路里的结构化投影回溯 helper，用来回答：

```text
某个 CTE / derived table / projection alias 的输出列，能否安全回溯到物理表字段？
```

当前 `token-event` 和 `full-grammer` 都先产生同一组 `StructuredSqlEvent`。`StructuredDataLineageExtractor` 再从这些事件构建 `ProjectionTrace`、`ExpressionSourceSet`、`AssignmentMapping`，最后生成 `DataLineageCandidate`。这个链路不再保留 SQL 文本 regex helper、token span fallback 或旧 `SqlLineageResolver`。

## 2. 为什么需要它

很多字段血缘不直接写在物理表别名之间，而是经过 CTE、derived table 或 projection alias：

```sql
WITH recent_orders AS (
  SELECT o.id AS order_id, o.customer_id
  FROM orders o
)
INSERT INTO order_audit(order_id, buyer_id)
SELECT ro.order_id, ro.customer_id
FROM recent_orders ro;
```

如果只看写入表面结构，只能看到：

```text
ro.order_id    -> order_audit.order_id
ro.customer_id -> order_audit.buyer_id
```

`ProjectionTraceResolver` 会把 `ro` 的输出列回溯为物理来源：

```text
ro.order_id    -> orders.id
ro.customer_id -> orders.customer_id
```

最终正式字段血缘是：

```text
orders.id          -> order_audit.order_id
orders.customer_id -> order_audit.buyer_id
```

## 3. 输入和输出

输入来自结构事件，而不是重新扫描 raw SQL：

- `ROWSET_REFERENCE`
- `CTE_DECLARATION`
- `PROJECTION_ITEM`
- `IGNORED_ROWSET`
- `LOCAL_TEMP_TABLE_DECLARATION`
- `WRITE_TARGET`
- `UPDATE_ASSIGNMENT`
- `INSERT_SELECT_MAPPING`
- `MERGE_WRITE_MAPPING`

输出是内部 `ProjectionTrace` / `ExpressionSourceSet`：

```text
alias.column -> physical_table.physical_column
```

这些内部模型只服务 `StructuredDataLineageExtractor`。正式输出仍由：

```text
StructuredDataLineageExtractor
  -> DataLineageMerger
  -> ScanResult.dataLineages
  -> JsonResultWriter.dataLineages
```

产生。

## 4. 支持范围

### 4.1 CTE

```sql
WITH x AS (
  SELECT c.region_id
  FROM customers c
)
INSERT INTO customer_region_snapshot(region_id)
SELECT x.region_id
FROM x;
```

映射：

```text
x.region_id -> customers.region_id
```

最终血缘：

```text
customers.region_id -> customer_region_snapshot.region_id
```

### 4.2 多层 CTE

```sql
WITH recent_orders AS (
  SELECT o.id AS order_id, c.region_id
  FROM orders o
  JOIN customers c ON o.customer_id = c.id
),
regional_orders AS (
  SELECT ro.order_id, ro.region_id
  FROM recent_orders ro
)
INSERT INTO regional_order_snapshot(order_id, region_id)
SELECT x.order_id, x.region_id
FROM regional_orders x;
```

映射会逐层传播：

```text
regional_orders.order_id -> orders.id
regional_orders.region_id -> customers.region_id
x.order_id -> orders.id
x.region_id -> customers.region_id
```

### 4.3 Derived Table

```sql
INSERT INTO projected_orders(order_id, customer_id)
SELECT p.order_id, p.customer_id
FROM (
  SELECT o.id AS order_id, o.customer_id
  FROM orders o
) p;
```

映射：

```text
p.order_id    -> orders.id
p.customer_id -> orders.customer_id
```

### 4.4 显式输出列名

```sql
WITH x(order_id, buyer_id) AS (
  SELECT o.id, o.customer_id
  FROM orders o
)
INSERT INTO order_buyers(order_id, buyer_id)
SELECT x.order_id, x.buyer_id
FROM x;
```

映射：

```text
x.order_id -> orders.id
x.buyer_id -> orders.customer_id
```

## 5. 精确与保守边界

`ProjectionTraceResolver` 只把结构事件中明确的列投影回溯为精确来源：

```sql
SELECT o.id
SELECT o.id AS order_id
SELECT o.customer_id customer_id
```

表达式投影不会被当作单一物理字段端点：

```sql
SELECT SUM(pay_amount) AS actual_total
SELECT COALESCE(a.user_id, b.user_id) AS user_id
SELECT row_number() OVER (...) AS rn
SELECT a.user_id + 1 AS user_id
```

这些表达式的字段来源由 `ExpressionSourceSet` 保留，并由 `StructuredDataLineageExtractor` 根据写入上下文输出 `AGGREGATE`、`COALESCE`、`ARITHMETIC`、`WINDOW_DERIVED` 等 transform。

## 6. 与 Relationship 的边界

当前正式字段血缘和 relationship 是两条输出模型：

- `ProjectionTraceResolver` 服务 Data Lineage 写入映射。
- `TokenEventRelationExtractor` 可以消费同一批结构事件中的 rowset、predicate、projection 信息来还原关系端点，但 relationship 的 FK-like 方向、confidence、`NAMING_MATCH` 都在 relationship 语义层处理。

字段来源只证明“这个 projection column 来自哪个物理列”，不证明该列一定是外键。

## 7. 负向边界

以下内容不进入物理字段血缘：

- 参数、bind variable、routine 参数。
- 局部变量。
- 字面量。
- JSON path。
- dynamic SQL 拼接结果。
- 显式临时表 scope。
- trigger `OLD` / `NEW` pseudo rowset。

过滤依据必须来自 parse-tree context、结构事件、scope 或 endpoint 类型，不能通过表名/列名特殊规则猜测。

## 8. 当前测试入口

相关测试入口以当前代码为准：

```text
relation-detector/core/src/test/java/com/relationdetector/core/lineage/ProjectionTraceResolverTest.java
relation-detector/core/src/test/java/com/relationdetector/core/lineage/VisitorThreadSafetyTest.java
relation-detector/cli/src/test/java/com/relationdetector/cli/CorrectnessFixtureRunnerTest.java
relation-detector/cli/src/test/java/com/relationdetector/cli/DataLineageAuditGeneratorTest.java
```

correctness golden 中的 `expected-lineage.json` 是正式验收来源；如果新增 projection trace 能力导致输出变化，需要先按 SQL 上下文审计，再决定是修 parser 还是更新 golden。
