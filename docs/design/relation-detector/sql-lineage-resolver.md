# SqlLineageResolver 与字段血缘详细设计

## 1. 当前定位

`SqlLineageResolver` 不是 SQL parser，也不是最终 Data Lineage 输出器。它是 SQL 语义层里的一个保守列来源解析 helper，用来回答：

```text
某个 CTE / derived table / projection alias 的输出列，能否安全回溯到物理表字段？
```

当前 SQL 解析入口已经分成两类事件来源：

- `token-event`：基于 ANTLR lexer/parser support 和 Java token-event builder 生成结构事件。
- `full-grammer`：按数据库大版本使用 vendored grammar 与 typed parse-tree visitor 生成结构事件。

两条链路最终都产出同一组 `StructuredSqlEvent`，再由 `TokenEventRelationExtractor` 和 `TokenEventDataLineageExtractor` 消费。`SqlLineageResolver` 只服务这些 extractor 中的列端点回溯，不负责解析完整 SQL 文本。

## 2. 为什么需要它

很多 SQL 关系不直接写在物理表别名之间，而是写在 CTE 或 derived table 列上：

```sql
WITH recent_orders AS (
  SELECT o.id AS order_id, o.customer_id
  FROM orders o
)
SELECT *
FROM recent_orders ro
JOIN customers c ON ro.customer_id = c.id;
```

如果直接使用 SQL 表面结构，只能看到：

```text
ro.customer_id = customers.id
```

`ro` 不是物理表，不能输出 `ro.customer_id -> customers.id`。`SqlLineageResolver` 会把投影列回溯成：

```text
recent_orders.customer_id -> orders.customer_id
ro.customer_id            -> orders.customer_id
```

于是 relationship extractor 可以生成真实物理端点：

```text
orders.customer_id -> customers.id
```

## 3. 输入和输出

输入来自结构事件，而不是重新扫描 raw SQL：

- `ROWSET_REFERENCE`
- `CTE_DECLARATION`
- `PROJECTION_ITEM`
- `IGNORED_ROWSET`
- derived alias / CTE alias 的 rowset attributes

输出是列来源映射：

```text
alias.column -> physical_table.physical_column
```

这个映射会被两个组件消费：

- `TokenEventRelationExtractor`：把 join / exists / in predicate 中的派生列端点还原成物理列端点。
- `TokenEventDataLineageExtractor`：把 UPDATE / INSERT / MERGE 写入表达式中的 projection alias 还原成物理字段来源。

`SqlLineageResolver` 本身不创建 `RelationshipCandidate`，不创建 `DataLineageCandidate`，也不计算 confidence。

## 4. 支持范围

### 4.1 CTE

```sql
WITH x AS (
  SELECT c.region_id
  FROM customers c
)
SELECT *
FROM x
JOIN regions r ON x.region_id = r.id;
```

映射：

```text
x.region_id -> customers.region_id
```

最终关系：

```text
customers.region_id -> regions.id
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
SELECT *
FROM regional_orders x
JOIN regions r ON x.region_id = r.id;
```

映射会逐层传播：

```text
recent_orders.region_id   -> customers.region_id
regional_orders.region_id -> customers.region_id
x.region_id               -> customers.region_id
```

### 4.3 Derived Table

```sql
SELECT *
FROM (
  SELECT o.id AS order_id, o.customer_id
  FROM orders o
) AS projected_orders
JOIN customers c ON projected_orders.customer_id = c.id;
```

映射：

```text
projected_orders.order_id    -> orders.id
projected_orders.customer_id -> orders.customer_id
```

### 4.4 显式输出列名

```sql
WITH x(order_id, buyer_id) AS (
  SELECT o.id, o.customer_id
  FROM orders o
)
SELECT *
FROM x
JOIN customers c ON x.buyer_id = c.id;
```

映射：

```text
x.order_id -> orders.id
x.buyer_id -> orders.customer_id
```

### 4.5 LATERAL / 相关 derived table

PostgreSQL `LATERAL` 或相关 derived table 可以引用左侧已出现 alias：

```sql
SELECT o.id, u.email
FROM orders o
JOIN LATERAL (
  SELECT o.user_id AS user_id
) x ON true
JOIN users u ON x.user_id = u.id;
```

映射：

```text
x.user_id -> orders.user_id
```

作用域规则是结构性的：derived table 只能看到它语法上允许看到的外层 alias，不会把后文 alias 泄漏进子查询。

## 5. 精确与保守边界

`SqlLineageResolver` 只把明确列投影视为精确来源：

```sql
SELECT o.id
SELECT o.id AS order_id
SELECT o.customer_id customer_id
```

这些可以生成：

```text
order_id    -> orders.id
customer_id -> orders.customer_id
```

表达式投影不会被它直接当成 FK-like 端点：

```sql
SELECT SUM(pay_amount) AS actual_total
SELECT COALESCE(a.user_id, b.user_id) AS user_id
SELECT row_number() OVER (...) AS rn
SELECT a.user_id + 1 AS user_id
```

原因是这些输出列可能来自多个输入、聚合、窗口或计算结果，不一定仍代表可用于关系推断的引用列。表达式作为字段值来源时，由 `TokenEventDataLineageExtractor` 根据 `EXPRESSION_SOURCE`、`PROJECTION_ITEM` 等事件输出正式 Data Lineage。

## 6. Relationship 与 Data Lineage 的分工

### 6.1 Relationship

当 join predicate 是：

```sql
x.region_id = r.id
```

且 resolver 能还原：

```text
x.region_id -> customers.region_id
r.id        -> regions.id
```

relationship extractor 按普通 equality 处理：

```text
customers.region_id -> regions.id
evidence: SQL_LOG_JOIN / VIEW_JOIN / PROCEDURE_JOIN / TRIGGER_REFERENCE
attributes.lineageResolved: true
```

置信度不会因为 `lineageResolved=true` 自动升高。lineage 只证明列来源，不证明该列一定是外键；方向与强弱仍由 FK-like 方向规则、唯一性、命名、metadata、profile evidence 等语义层判断。

### 6.2 Data Lineage

字段写入血缘是独立模型，不混入 relationship：

```sql
UPDATE users u
LEFT JOIN (
  SELECT user_id, SUM(pay_amount) AS actual_total
  FROM orders
  GROUP BY user_id
) o_summary ON u.id = o_summary.user_id
SET u.total_spent = COALESCE(o_summary.actual_total, 0.00);
```

relationship:

```text
orders.user_id -> users.id
```

data lineage:

```text
VALUE:AGGREGATE:orders.pay_amount->users.total_spent
```

`SqlLineageResolver` 可以帮助把 `o_summary.actual_total` 回溯到 projection 来源，但正式 `DataLineageCandidate` 由 `TokenEventDataLineageExtractor` 创建。

## 7. 与 self-join 的关系

self-join 的列级弱共现不是靠列名判断，而是靠结构：

```sql
SELECT *
FROM hr_employees e
JOIN hr_employees m ON e.manager_id = m.emp_id;
```

如果 FK-like 方向无法可靠判断，`TokenEventRelationExtractor` 会在 ambiguous equality 分支检查：

- 两侧解析到同一物理表；
- SQL alias 不同；
- 物理列不同；
- predicate 是明确等值关系。

满足时输出列级弱共现：

```text
CO_OCCURRENCE:hr_employees.manager_id->hr_employees.emp_id:SQL_LOG_COLUMN_CO_OCCURRENCE
```

同一 alias 的行内比较不会输出关系：

```sql
WHERE a.left_col = a.right_col
```

## 8. 当前边界

已支持：

- CTE 输出列回溯。
- 多层 CTE 输出列传播。
- FROM/JOIN derived table 输出列回溯。
- 多层 nested derived table 输出列传播。
- CTE/derived alias 传播。
- 显式输出列名列表。
- LATERAL / correlated derived table 中已允许作用域的简单列投影。
- Data-modifying CTE、INSERT SELECT、MERGE 等被已审核 fixture 覆盖的写入场景。

仍保持保守：

- `SELECT *` / `alias.*` 不做完整列展开，除非事件中已有可用列清单。
- 参数、literal、JSON path、局部变量不作为 v1 Data Lineage source。
- 过程内显式临时表不作为跨语句物理字段血缘 source/target。
- 动态 SQL 不做静态还原，只记录 warning。
- 复杂表达式不会被 resolver 当作 relationship 端点；应进入 Data Lineage transform。

这些边界不是按特殊表名或列名过滤，而是由语法事件、作用域和 endpoint 类型决定。

## 9. 测试策略

主要测试入口：

```text
core/src/test/java/com/relationdetector/core/lineage/SqlLineageResolverTest.java
core/src/test/java/com/relationdetector/core/lineage/TokenEventDataLineageExtractorTest.java
core/src/test/java/com/relationdetector/core/tokenevent/TokenEventRelationEventsTest.java
cli/src/test/java/com/relationdetector/cli/CorrectnessFixtureRunnerTest.java
cli/src/test/java/com/relationdetector/cli/FullGrammerSqlBehaviorTest.java
```

测试关注点：

- CTE / derived alias 能还原到物理表列。
- CTE 名、derived alias、function rowset alias 不输出为物理表。
- Relationship 与 Data Lineage 各自输出，不互相污染。
- self-join 不靠特殊名字匹配。
- full-grammer 与 token-event 经过同一语义 extractor 后不低于当前 correctness golden。
