# SimpleSqlRelationParser 复杂 SQL 测试报告

## 1. 测试来源

本轮测试参考了官方 MySQL 和 PostgreSQL 文档中的对象语法形态，再构造项目内业务化长 SQL fixture。没有直接拷贝大段官方示例，测试 SQL 由项目自造，目的是覆盖真实语法结构：

- MySQL `CREATE PROCEDURE` / `CREATE FUNCTION`
- MySQL `CREATE TRIGGER`
- MySQL `CREATE VIEW`
- PostgreSQL `CREATE FUNCTION`
- PostgreSQL `CREATE PROCEDURE`
- PostgreSQL `CREATE TRIGGER`
- PostgreSQL `CREATE VIEW`
- PostgreSQL `WITH` / recursive CTE / data-modifying CTE 语法形态
- PostgreSQL `LATERAL` table expression 语法形态
- MySQL `WITH` / recursive CTE 语法形态
- MySQL derived table 语法形态
- MySQL JOIN clause 中的逗号 table reference、`[[AS] alias]`、`ON`、`USING`
- PostgreSQL table expressions 中的逗号 `FROM` 列表、qualified joins、table alias
- MySQL 反引号 identifier
- PostgreSQL 双引号 delimited identifier

参考文档：

- https://dev.mysql.com/doc/refman/8.0/en/create-procedure.html
- https://dev.mysql.com/doc/refman/8.0/en/create-trigger.html
- https://dev.mysql.com/doc/refman/8.0/en/create-view.html
- https://dev.mysql.com/doc/refman/8.0/en/with.html
- https://dev.mysql.com/doc/refman/8.0/en/derived-tables.html
- https://dev.mysql.com/doc/refman/8.0/en/join.html
- https://dev.mysql.com/doc/refman/8.0/en/identifiers.html
- https://www.postgresql.org/docs/current/sql-createfunction.html
- https://www.postgresql.org/docs/current/sql-createprocedure.html
- https://www.postgresql.org/docs/current/sql-createtrigger.html
- https://www.postgresql.org/docs/current/sql-createview.html
- https://www.postgresql.org/docs/current/plpgsql-trigger.html
- https://www.postgresql.org/docs/current/queries-with.html
- https://www.postgresql.org/docs/current/queries-table-expressions.html
- https://www.postgresql.org/docs/current/sql-syntax-lexical.html

## 2. 新增测试文件

测试文件：

```text
relation-core/src/test/java/com/relationdetector/core/SimpleSqlRelationParserComplexSqlTest.java
```

测试类型：

- 白盒单元测试。
- 直接调用 `SimpleSqlRelationParser`。
- 使用长 SQL 字符串模拟过程、触发器、视图、函数对象体。
- 使用短 SQL 矩阵覆盖 JOIN 关键字、逗号 JOIN、别名和引用标识符组合。

## 3. 覆盖场景

### 3.1 MySQL 存储过程

覆盖：

- `CREATE PROCEDURE ... BEGIN ... END`
- `INSERT INTO ... SELECT`
- 多个 `JOIN ... ON`
- 嵌套 `IN (SELECT ...)`
- 嵌套 `EXISTS (SELECT ...)`

期望识别：

- `orders.user_id -> users.id`
- `payments.order_id -> orders.id`
- `shipments.order_id -> orders.id`
- `shipments.carrier_id -> carriers.id`
- `customers.account_id -> accounts.id`
- `orders.customer_id -> customers.id`
- `invoices.order_id -> orders.id`

### 3.2 MySQL 触发器

覆盖：

- `CREATE TRIGGER ... AFTER INSERT ON ... FOR EACH ROW`
- trigger body 内部 `INSERT INTO ... SELECT`
- trigger body 内部多表 JOIN
- `NEW.id` 触发器变量与普通表别名混合出现

期望识别：

- `orders.user_id -> users.id`
- `orders.employee_id -> employees.id`
- `employees.department_id -> departments.id`

证据类型：

- `TRIGGER_REFERENCE`

### 3.3 PostgreSQL 视图

覆盖：

- `CREATE VIEW schema.view AS SELECT`
- schema-qualified 表名，例如 `public.orders`
- 多个 JOIN
- `LEFT JOIN`
- 嵌套 `EXISTS`

期望识别：

- `orders.user_id -> users.id`
- `orders.customer_id -> customers.id`
- `customers.account_id -> accounts.id`
- `payments.order_id -> orders.id`
- `shipments.carrier_id -> carriers.id`
- `shipments.order_id -> orders.id`

证据类型：

- `VIEW_JOIN`

### 3.4 PostgreSQL 函数

覆盖：

- `CREATE FUNCTION ... LANGUAGE plpgsql`
- `DECLARE ... BEGIN ... END`
- `SELECT ... INTO`
- 多个 JOIN
- 嵌套 `IN (SELECT ...)`

期望识别：

- `payments.order_id -> orders.id`
- `orders.user_id -> users.id`
- `orders.customer_id -> customers.id`
- `accounts.account_group_id -> account_groups.id`
- `customers.account_id -> accounts.id`

证据类型：

- JOIN 使用 `PROCEDURE_JOIN`
- IN 子查询使用 `SQL_LOG_SUBQUERY_IN`

说明：当前 enum 名称中 `SQL_LOG_SUBQUERY_IN` 仍沿用历史命名，但 source type 会保留为 `DATABASE_OBJECT`。后续可以考虑新增更中性的 `SUBQUERY_IN` evidence type。

### 3.5 逗号分隔表共现

覆盖：

- `FROM users u, audit_logs l, security_events se`
- 没有明确列级连接条件

期望识别：

- `users -> audit_logs`
- `users -> security_events`
- `audit_logs -> security_events`

关系类型：

- `CO_OCCURRENCE`

### 3.6 左连接、右连接、全连接

覆盖：

- `LEFT JOIN`
- `RIGHT OUTER JOIN`
- `FULL OUTER JOIN`
- `JOIN ... ON a.x = b.y`

期望识别：

- 外连接中的显式等值条件仍然输出列级 `FK_LIKE`。
- evidence attributes 中记录 `joinKind`，例如 `LEFT_JOIN`、`RIGHT_JOIN`、`FULL_JOIN`。

置信度策略：

- 当前不因为 left/right/full 改变基础分。
- 原因：外连接表达“保留哪一侧行”的查询语义，不直接削弱 `a.x = b.y` 这个列关联证据。
- 可选性、空值比例和基数差异应交给后续 data profile 或 metadata evidence 修正。

### 3.7 `JOIN ... USING`

覆盖：

- `JOIN order_tags ot USING (order_id)`
- `LEFT JOIN b USING (tenant_id, account_id)`

期望识别：

- 输出表级 `CO_OCCURRENCE`。
- evidence attributes 中记录 `usingColumns`。

置信度策略：

- 不直接生成列级 `FK_LIKE`。
- 原因：`USING(order_id)` 只说明两表都有同名列并在查询中相等，不说明谁引用谁，也不说明目标列唯一。

### 3.8 `NATURAL JOIN`

覆盖：

- `NATURAL JOIN`
- `NATURAL LEFT JOIN`

期望识别：

- 输出表级 `CO_OCCURRENCE`。
- evidence attributes 中记录 `naturalJoin=true`。

置信度策略：

- 不直接生成列级 `FK_LIKE`。
- 原因：`NATURAL JOIN` 隐式比较所有同名列，如果没有元数据和列来源分析，直接推断列级关系误报风险高。

### 3.9 CTE 和多层 CTE

覆盖：

- `WITH recent_orders AS (...)`
- 多个 CTE 级联。
- CTE body 内部的普通 JOIN。
- 外层查询引用 CTE 名称。

期望识别：

- 能识别 CTE body 内部真实物理表之间的 JOIN，例如 `orders.user_id -> users.id`。
- 不输出指向 CTE 伪表的关系，例如不输出 `payments.order_id -> recent_orders.id`。

当前策略：

- regex parser 会识别 CTE 名称，并跳过这些名称作为物理表 alias。
- `SqlLineageResolver` 会对简单投影列做 lineage 回溯。
- 如果 CTE 输出列来自 `alias.column`、`alias.column AS output` 或显式 CTE 输出列名列表，则可以继续解析外层关系。
- 如果 CTE 输出列来自表达式、聚合、窗口函数或 `SELECT *`，不生成精确列级 lineage。

置信度策略：

- CTE body 内部普通 JOIN 按来源类型正常评分。
- 外层 CTE alias 如果能回溯到真实表列，则按普通 equality JOIN 输出，并在 evidence attributes 中记录 `lineageResolved=true`。
- lineage 本身不提高分数；它只把列来源还原给 JOIN evidence 使用。

### 3.10 CTE 输出列 lineage

覆盖：

- CTE 输出列 `o.id AS order_id`。
- CTE 输出列 `c.region_id`。
- 外层查询通过 CTE alias 继续 JOIN。

示例形态：

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
JOIN regions r ON x.region_id = r.id
JOIN invoices i ON i.order_id = x.order_id;
```

期望识别：

- `orders.customer_id -> customers.id`
- `customers.region_id -> regions.id`
- `invoices.order_id -> orders.id`

并且不输出：

- `regional_orders.region_id -> regions.id`
- `recent_orders.order_id -> invoices.order_id`

### 3.11 派生表和多层嵌套查询 lineage

覆盖：

- FROM 子句中的 derived table。
- derived table 内部继续包含 derived table。
- 外层 JOIN 引用 derived table 输出列。

示例形态：

```sql
SELECT *
FROM (
  SELECT inner_orders.order_id, inner_orders.customer_id
  FROM (
    SELECT o.id AS order_id, o.customer_id
    FROM orders o
    JOIN users u ON o.user_id = u.id
  ) AS inner_orders
) AS projected_orders
JOIN customers c ON projected_orders.customer_id = c.id
JOIN invoices i ON i.order_id = projected_orders.order_id;
```

期望识别：

- `orders.user_id -> users.id`
- `orders.customer_id -> customers.id`
- `invoices.order_id -> orders.id`

### 3.12 表达式投影不做精确 FK-like 推断

覆盖：

- `COALESCE(a.user_id, b.user_id) AS user_id`
- 外层 `nuk.user_id = users.id`

期望：

- 不输出 `account_events.user_id -> users.id`。
- 不输出 `backup_account_events.user_id -> users.id`。
- 仍识别内部明确关系 `backup_account_events.account_event_id -> account_events.id`。

原因：

- 表达式输出不是单一源列。
- 强行把表达式输出列推回某个输入列会制造高风险误报。

### 3.13 JOIN 写法矩阵

测试文件：

```text
relation-core/src/test/java/com/relationdetector/core/SimpleSqlRelationParserJoinSyntaxMatrixTest.java
```

覆盖：

- 显式 `JOIN ... ON`。
- 逗号分隔 `FROM a, b, c WHERE ...`。
- 所有表都不带别名。
- 一边带别名、一边不带别名。
- MySQL 反引号标识符，例如 `` `orders`.`user_id` ``。
- PostgreSQL 双引号标识符，例如 `"public"."orders"`。
- 引用标识符和非引用标识符混用。

新增测试发现的问题：

- 无别名显式 JOIN 中，原 `FROM_OR_JOIN` regex 会把 `JOIN` 或 `ON` 误捕获成表别名，导致后续真实表没有进入 alias map。
- `"schema"."table"` 这类 quoted qualified identifier 原清理逻辑会得到 `public"."orders`，导致表名不规范。

修复：

- alias 捕获增加关键字保护，不再把 `ON`、`JOIN`、`USING` 等 SQL 关键字当成别名消费。
- qualified identifier 拆分改为按 quote 边界识别 `.`，支持：

```text
public.orders      -> schema=public, table=orders
"public"."orders" -> schema=public, table=orders
`shop`.`orders`   -> schema=shop, table=orders
```

### 3.14 DDL 写法矩阵

测试文件：

```text
relation-core/src/test/java/com/relationdetector/core/SimpleDdlParserMatrixTest.java
```

覆盖：

- inline references：`customer_id BIGINT REFERENCES customers(id)`。
- table-level FK：`FOREIGN KEY (...) REFERENCES ... (...)`。
- `ALTER TABLE ... ADD CONSTRAINT ... FOREIGN KEY`。
- composite FK：`FOREIGN KEY (tenant_id, customer_id) REFERENCES customers(tenant_id, id)`。
- quoted schema-qualified identifier：`"sales"."orders"`。
- target `PRIMARY KEY` / full unique index 作为 `TARGET_UNIQUE` 辅助 evidence。
- source 普通全列 index 作为 `SOURCE_INDEX` 辅助 evidence。
- PostgreSQL partial unique index 不作为全局 `TARGET_UNIQUE`。
- expression/functional index 不作为普通列唯一 evidence。
- MySQL prefix index 不作为完整 source column index evidence。

新增测试发现的问题：

- 原 `SimpleDdlParser` 不识别 inline `REFERENCES`。
- 原 `SimpleDdlParser` 不识别 `ALTER TABLE ... ADD FOREIGN KEY`。
- 原复合 FK 只取第一列，漏掉后续列对。
- 原 quoted schema-qualified identifier 会被清理成异常表名，例如 `sales"."orders`。
- 原 parser 没有把 PK/unique/source index 作为 FK candidate 的辅助 evidence。

修复：

- DDL parser 改为两阶段：先收集 FK candidate、PK/unique/source index，再给已有 FK candidate 附加辅助 evidence。
- 复合 FK 按列顺序生成多条候选，并记录 `compositePosition` / `compositeSize`。
- quoted qualified identifier 按 quote 边界拆分。
- partial/expression/functional/prefix index 保守跳过，不作为全局唯一性或完整列索引证据。

### 3.15 复杂 comma join 与输入过滤 predicate

覆盖：

- 多表传统逗号 JOIN：

```sql
SELECT o.id, u.id, c.id
FROM orders o,
     users u,
     customers c,
     selected_user_ids sui,
     selected_status_codes ssc
WHERE o.user_id = u.id
  AND o.created_user_id = u.id
  AND o.updated_user_id = u.id
  AND o.customer_id = c.id
  AND sui.user_id = u.id
  AND ssc.status_code = o.status_code
  AND o.deleted_at IS NULL
  AND c.tenant_id = p_tenant_id;
```

期望识别业务关系：

- `orders.user_id -> users.id`
- `orders.created_user_id -> users.id`
- `orders.updated_user_id -> users.id`
- `orders.customer_id -> customers.id`

期望忽略：

- `selected_user_ids.user_id -> users.id`
- `selected_status_codes -> orders`
- `o.deleted_at IS NULL`
- `c.tenant_id = p_tenant_id`

修复：

- FK 命名规则从只支持 `user_id` / `users_id` 扩展为也支持多角色后缀，例如 `created_user_id`、`updated_user_id`。
- parser 会识别对象体内的本地临时表声明，例如 `CREATE TEMPORARY TABLE selected_user_ids(...)` 和 `CREATE TEMP TABLE selected_status_codes AS ...`，并把这些表名加入 ignored rowsets。
- AliasExtractor 仍保留输入过滤表命名兜底：`tmp_`、`temp_`、`input_`、`param_`、`filter_` 等前缀的表或别名不会进入物理 alias map。

边界：

- 对于存储过程/函数内部显式创建的临时表，优先依赖 SQL 文本中的 `CREATE TEMP/TEMPORARY TABLE`，不需要 adaptor 额外查询数据库 metadata。
- ignored rowsets 是单次 `SqlStatementRecord` parse 内的局部集合；同名表在另一个函数、存储过程或普通 SQL 中不会被自动忽略。
- 已增加作用域测试：先解析一个函数，函数内 `selected_user_ids` 被忽略；随后解析普通 SQL，普通 SQL 中同名 `selected_user_ids` 可以被识别为 `selected_user_ids.user_id -> users.id`。
- 命名兜底仍是 heuristic；如果真实业务表也使用 `tmp_`、`input_`、`filter_` 等前缀，可能被跳过。

## 4. 测试执行结果

执行命令：

```bash
mvn test
```

结果：

```text
Tests run: 23, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## 5. 测试发现并修复的问题

本轮长 SQL 测试发现了一个真实问题：

- 原逻辑在 `parseInSubquery` 中通过“整条 SQL 是否包含 `EXISTS`”来判断子查询 evidence type。
- 当同一存储过程同时包含 `IN` 和 `EXISTS` 时，`IN` 子查询关系会被误标成 `SQL_LOG_EXISTS`。

修复：

- `IN_SUBQUERY` regex 匹配出来的关系固定标记为 `SQL_LOG_SUBQUERY_IN`。
- `EXISTS` 内部的等值关系继续由 equality parser 识别。

本轮 DDL 测试发现并修复了五类问题：

- inline references 不生成关系。
- ALTER TABLE 外键不生成关系。
- composite FK 只生成第一列关系。
- quoted schema-qualified table 名称清理不正确。
- PK/unique/source index 没有作为已有 FK candidate 的辅助 evidence。

## 6. 当前 parser 能力结论

当前 `SimpleSqlRelationParser` 可以识别：

- 长过程/函数/触发器/视图 body 中的普通 aliased equality join。
- `LEFT JOIN`、`RIGHT JOIN`、`FULL JOIN` 中的显式等值条件。
- `WHERE` 中的隐式等值关系。
- `JOIN ... USING`，但只作为表级弱关系。
- `NATURAL JOIN`，但只作为表级弱关系。
- 简单 `IN (SELECT alias.column FROM table alias ...)` 子查询。
- 多表逗号共现。
- CTE body 内部的真实物理表 JOIN。
- 过滤 CTE 名称，避免输出指向 CTE 伪表的关系。
- CTE 简单输出列 lineage 回溯。
- 多层 CTE 简单输出列 lineage 传播。
- FROM/JOIN derived table 简单输出列 lineage 回溯。
- 多层嵌套 derived table 简单输出列 lineage 传播。
- 表达式投影的负向保护，避免把 `COALESCE`、聚合、窗口函数等输出列误判为精确 FK-like。
- 显式 JOIN 中无别名、混合别名的表引用。
- 逗号 FROM 列表中无别名、混合别名的表引用。
- quoted qualified identifier，例如 `"public"."orders"` 和 `` `shop`.`orders` ``。
- MySQL 反引号和 PostgreSQL 双引号的基础清理。
- schema-qualified 表名。
- 复杂 comma join 中多个业务 FK predicate 和非业务过滤 predicate 混合。
- 多角色 FK 命名，例如 `created_user_id`、`updated_user_id`。
- 对象体内 `CREATE TEMP/TEMPORARY TABLE` 创建的本地临时表跳过。
- 常见输入过滤表/临时过滤表前缀的启发式兜底跳过。
- DDL inline references、table-level FK、ALTER TABLE FK。
- DDL composite FK 按列顺序生成候选关系。
- DDL quoted schema-qualified identifier。
- DDL PK/unique/source index 辅助 evidence。
- DDL partial/expression/functional/prefix index 负向保护。

## 7. 当前边界

当前 regex parser 仍不是完整 SQL parser，以下场景不保证准确：

- `SELECT *` 或 `alias.*` 的完整列展开。
- `UNION` / `INTERSECT` / `EXCEPT` 分支输出列和来源表之间的 lineage。
- 递归 CTE 中递归分支的稳定 lineage。
- `LATERAL` / `CROSS APPLY` / `OUTER APPLY` 的列来源回溯。
- `MERGE`、`UPDATE ... FROM`、`DELETE ... USING` 的写入侧关系。
- 数据修改 CTE 的 `RETURNING` 列 lineage。
- JSON_TABLE、unnest、set-returning function 等函数型行集的列来源。
- 动态 SQL，例如 MySQL `PREPARE` 或 PL/pgSQL `EXECUTE format(...)`。
- 复杂表达式投影，例如 `COALESCE(o.user_id, x.user_id) IN (...)`。
- 多列 tuple comparison，例如 `(a.x, a.y) IN (SELECT b.x, b.y ...)`。
- 非等值关联，例如 range join、JSON path join。
- 窗口函数、聚合、`GROUP BY` 本身不表达表关系，只有其内部引用的 JOIN/子查询可能表达关系。
- 输入过滤表识别优先依赖同一对象体内的 `CREATE TEMP/TEMPORARY TABLE`；命名兜底仍是 heuristic，如果真实业务表也使用 `tmp_`、`input_`、`filter_` 等前缀，可能被跳过。
- DDL parser 仍不是完整数据库方言 parser；复杂分区表、继承表、排除约束、跨方言特殊索引参数、动态生成 DDL 等仍建议交给数据库 adaptor 的元数据 collector 或未来 parser-backed 实现。

后续如果要覆盖这些场景，应按设计文档替换为 JSqlParser 或数据库专用 parser，而不是继续堆叠 regex。
