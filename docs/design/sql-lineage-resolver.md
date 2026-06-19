# SqlLineageResolver 详细设计

## 1. 背景和目标

早期 SQL 关系抽取只能直接解析真实表别名上的列关系：

```sql
SELECT *
FROM orders o
JOIN users u ON o.user_id = u.id;
```

这种 SQL 可以直接得到：

```text
orders.user_id -> users.id
```

但在 CTE、派生表、多层嵌套查询中，外层 JOIN 常常引用的是临时行集别名：

```sql
WITH x AS (
  SELECT c.region_id
  FROM customers c
)
SELECT *
FROM x
JOIN regions r ON x.region_id = r.id;
```

如果不做 lineage，系统只能看到 `x.region_id = r.id`。`x` 不是物理表，直接输出 `x.region_id -> regions.id` 是错误的；完全跳过又会漏掉真实关系：

```text
customers.region_id -> regions.id
```

`SqlLineageResolver` 的目标是：在不引入完整 SQL AST parser 的前提下，安全地解析“简单投影列”的来源，把派生行集列回溯到真实表列。

## 2. 能处理 CTE 以外的情况吗？

可以。当前设计不仅处理 CTE，也处理 FROM/JOIN 中的派生表和多层嵌套查询。

### 2.1 CTE

```sql
WITH recent_orders AS (
  SELECT o.id AS order_id, o.customer_id
  FROM orders o
)
SELECT *
FROM recent_orders ro
JOIN customers c ON ro.customer_id = c.id;
```

lineage 映射：

```text
recent_orders.order_id    -> orders.id
recent_orders.customer_id -> orders.customer_id
ro.order_id               -> orders.id
ro.customer_id            -> orders.customer_id
```

最终关系：

```text
orders.customer_id -> customers.id
```

### 2.2 多层 CTE

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

lineage 会逐层传播：

```text
recent_orders.region_id  -> customers.region_id
regional_orders.region_id -> customers.region_id
x.region_id              -> customers.region_id
```

最终关系：

```text
customers.region_id -> regions.id
```

### 2.3 派生表

MySQL 和 PostgreSQL 都支持 FROM 子句中的 derived table，例如：

```sql
SELECT *
FROM (
  SELECT o.id AS order_id, o.customer_id
  FROM orders o
) AS projected_orders
JOIN customers c ON projected_orders.customer_id = c.id;
```

lineage 映射：

```text
projected_orders.order_id    -> orders.id
projected_orders.customer_id -> orders.customer_id
```

最终关系：

```text
orders.customer_id -> customers.id
```

### 2.4 多层嵌套派生表

```sql
SELECT *
FROM (
  SELECT inner_orders.order_id, inner_orders.customer_id
  FROM (
    SELECT o.id AS order_id, o.customer_id
    FROM orders o
  ) AS inner_orders
) AS projected_orders
JOIN customers c ON projected_orders.customer_id = c.id;
```

lineage 会从内向外传播：

```text
inner_orders.customer_id     -> orders.customer_id
projected_orders.customer_id -> orders.customer_id
```

最终关系：

```text
orders.customer_id -> customers.id
```

### 2.5 LATERAL / correlated 派生表

PostgreSQL `LATERAL` 子查询，以及一部分相关派生表，可以引用它左侧已经出现的表别名：

```sql
SELECT o.id, u.email
FROM orders o
JOIN LATERAL (
  SELECT o.user_id AS user_id
) x ON true
JOIN users u ON x.user_id = u.id;
```

`x` 不是物理表，不能输出：

```text
x.user_id -> users.id
```

但 `x.user_id` 是外层 `orders o` 的简单列投影，因此 lineage 可以安全记录：

```text
x.user_id -> orders.user_id
```

最终关系：

```text
orders.user_id -> users.id
```

实现上，`SqlLineageResolver` 在分析 derived table 时，只把该 derived table 之前已经出现的物理 alias 作为外层只读上下文；不会把 derived table 之后的 alias 泄漏进子查询。这样可以处理 `JOIN LATERAL (...) x`，同时避免错误使用后文表名。

## 3. 安全解析原则

`SqlLineageResolver` 只把“纯列投影”视为精确列来源。

### 3.1 精确来源

这些写法可以安全回溯：

```sql
SELECT o.id
SELECT o.id AS order_id
SELECT o.customer_id customer_id
```

输出列来源清晰：

```text
order_id    -> orders.id
customer_id -> orders.customer_id
```

### 3.2 显式列名覆盖

CTE 或派生表可以显式指定输出列名：

```sql
WITH x(order_id, buyer_id) AS (
  SELECT o.id, o.customer_id
  FROM orders o
)
SELECT *
FROM x
JOIN customers c ON x.buyer_id = c.id;
```

lineage：

```text
x.order_id -> orders.id
x.buyer_id -> orders.customer_id
```

最终关系：

```text
orders.customer_id -> customers.id
```

### 3.3 MySQL UPDATE 派生表中的单表裸列投影

MySQL 常见的汇总回写语句会把主表 JOIN 到一个单表聚合派生表。例如：

```sql
UPDATE users u
LEFT JOIN (
  SELECT user_id, SUM(pay_amount) AS actual_total
  FROM orders
  WHERE order_status = 'PAID'
  GROUP BY user_id
) o_summary ON u.id = o_summary.user_id
SET
  u.total_spent = COALESCE(o_summary.actual_total, 0.00)
WHERE u.is_active = 1;
```

这里 `o_summary.user_id` 来自派生表中的裸列投影 `SELECT user_id FROM orders`。因为该派生表 body 只有一个物理来源表 `orders`，系统可以安全记录：

```text
o_summary.user_id -> orders.user_id
```

于是外层条件 `u.id = o_summary.user_id` 可以输出：

```text
orders.user_id -> users.id
```

注意这里的方向不是 SQL 等号左右顺序，而是系统的 FK-like 归一方向：引用列/外键样列指向被引用键列。SQL 写作 `u.id = o_summary.user_id`，但输出仍是 `orders.user_id -> users.id`。

这个规则是受控开启的：默认公共 lineage 仍不把裸列投影当成精确来源；当前只由 MySQL ANTLR relation visitor 在 `UPDATE` 场景中启用。这样既覆盖 MySQL 业务中常见的 derived aggregate 回写，又避免全局改变 PostgreSQL 或 legacy fixture 的保守基线。

同一派生表中的聚合列仍不会生成精确 lineage：

```sql
SELECT SUM(pay_amount) AS actual_total FROM orders
```

`actual_total` 是聚合结果，不会被推断成 `orders.pay_amount` 的外键关系来源。

从写入血缘角度，`SUM(pay_amount)` 到 `users.total_spent` 是合理的业务 `Data_Lineage`：

```text
orders.pay_amount -> users.total_spent, transform=SUM
```

但当前 `SqlLineageResolver` 只服务关系抽取，不输出正式 Data Lineage。这个业务血缘会作为后续独立设计边界记录，而不是混入 FK-like 关系或现有 JSON relationship 输出。

### 3.4 不安全来源

这些写法不会生成精确列级 lineage：

```sql
SELECT COALESCE(a.user_id, b.user_id) AS user_id
SELECT SUM(p.amount) AS total_amount
SELECT lower(u.email) AS normalized_email
SELECT row_number() OVER (...) AS rn
SELECT a.user_id + 1 AS user_id
```

原因：

- 一个输出列可能来自多个输入列。
- 函数、聚合、窗口函数会改变语义。
- 表达式结果不一定还能代表外键值。

此时 parser 仍可解析子查询内部显式 JOIN，但不会把表达式输出列继续推成列级 `FK_LIKE`。

### 3.4 LATERAL 的安全边界

这些 LATERAL/correlated 形态可以安全回溯：

```sql
SELECT o.user_id AS user_id
SELECT o.id order_id
SELECT o.customer_id
```

这些形态不生成精确 lineage：

```sql
SELECT COALESCE(o.user_id, fallback.user_id) AS user_id
SELECT max(o.user_id) AS user_id
SELECT row_number() OVER (ORDER BY o.id) AS rn
SELECT *
```

原因与普通派生表一致：输出列不是单一确定源列，或者需要完整列展开/表达式语义分析。

## 4. 与关系类型和置信度的关系

`SqlLineageResolver` 本身不直接计算 confidence，也不直接创建 `RelationshipCandidate`。它只提供列来源映射：

```text
alias.column -> real_table.real_column
```

`RelationExtractionVisitor` 在解析 ANTLR 结构化等值事件和 raw equality 兜底时消费这个映射。

如果等值条件来自真实表列：

```sql
x.region_id = r.id
```

并且 lineage 可还原：

```text
x.region_id -> customers.region_id
r.id        -> regions.id
```

则关系按普通 equality JOIN 处理：

```text
customers.region_id -> regions.id
relationType: FK_LIKE
relationSubType: INFERRED_JOIN_FK
evidence: SQL_LOG_JOIN / VIEW_JOIN / PROCEDURE_JOIN / TRIGGER_REFERENCE
attributes.lineageResolved: true
```

置信度不因为 lineage 本身自动升高。原因是 lineage 只证明“列来自哪里”，不证明“这列一定是外键”。置信度提升仍应来自更强证据：

- 显式 FK。
- 唯一索引或 PK。
- 命名匹配。
- 数据画像中的包含率、重合率、空值率、唯一性。
- 多来源重复出现。

## 5. 当前实现边界

当前实现仍是 regex/scanner 混合的轻量方案，不是完整 SQL parser。

已支持：

- CTE 输出列回溯。
- 多层 CTE 输出列传播。
- FROM/JOIN 派生表输出列回溯。
- 多层嵌套派生表输出列传播。
- CTE/派生表引用别名传播。
- 简单 `alias.column`、`alias.column AS output`、`alias.column output`。
- 显式输出列名列表，例如 `WITH x(a, b) AS (...)` 和 derived table column list。
- 表达式投影跳过。

仍不保证：

- `SELECT *` 或 `alias.*` 的完整列展开，因为缺少表结构列清单。
- `UNION` / `INTERSECT` / `EXCEPT` 分支间的列来源合并。
- 递归 CTE 中递归分支的稳定 lineage。
- `LATERAL` / `CROSS APPLY` / `OUTER APPLY` 的跨作用域引用。
- 数据修改 CTE 的 `RETURNING` 列 lineage。
- JSON_TABLE、unnest、set-returning function 等函数型行集的列来源。
- 表达式、聚合、窗口函数、类型转换后的强列级推断。
- 动态 SQL。

这些场景后续应考虑继续增强数据库专用 ANTLR parser/visitor，并把当前 resolver 保持为保守 lineage helper。

## 6. 测试策略

当前测试文件：

```text
relation-core/src/test/java/com/relationdetector/core/SqlLineageResolverTest.java
relation-core/src/test/java/com/relationdetector/core/DialectSqlRelationParserComplexMatrixTest.java
relation-cli/src/test/java/com/relationdetector/cli/CorrectnessFixtureRunnerTest.java
```

新增重点测试：

- 多层 CTE 中 `regional_orders.region_id` 回溯到 `customers.region_id`。
- 多层派生表中 `projected_orders.customer_id` 回溯到 `orders.customer_id`。
- `COALESCE(a.user_id, b.user_id) AS user_id` 不被误判为精确列来源。

这些测试既验证正向识别，也验证“不乱猜”的负向边界。

## 7. 参考语法来源

设计和测试场景参考了官方文档中的语法能力：

- PostgreSQL `WITH` / recursive CTE / data-modifying CTE: https://www.postgresql.org/docs/current/queries-with.html
- PostgreSQL table expressions and `LATERAL`: https://www.postgresql.org/docs/current/queries-table-expressions.html
- MySQL `WITH` / recursive CTE: https://dev.mysql.com/doc/refman/8.0/en/with.html
- MySQL derived tables: https://dev.mysql.com/doc/refman/8.0/en/derived-tables.html
