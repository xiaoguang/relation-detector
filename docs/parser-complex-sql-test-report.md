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
relation-core/src/test/java/com/relationdetector/core/DialectSqlRelationParserComplexMatrixTest.java
relation-core/src/test/java/com/relationdetector/core/DialectParserEvidenceConfidenceTest.java
relation-core/src/test/java/com/relationdetector/core/AntlrShadowGoldenComparisonTest.java
relation-core/src/test/java/com/relationdetector/core/AntlrDdlGoldenComparisonTest.java
relation-core/src/test/java/com/relationdetector/core/DdlRelationExtractionVisitorIndependenceTest.java
relation-core/src/test/java/com/relationdetector/core/DdlRelationParserRunnerTest.java
relation-core/src/test/java/com/relationdetector/core/ScanEngineDiagnosticsTest.java
adaptor-mysql/src/test/java/com/relationdetector/mysql/MySqlDdlParserTest.java
adaptor-mysql/src/test/java/com/relationdetector/mysql/MySqlAntlrShadowZeroMissingTest.java
adaptor-postgres/src/test/java/com/relationdetector/postgres/PostgresDdlParserTest.java
adaptor-postgres/src/test/java/com/relationdetector/postgres/PostgresAntlrShadowZeroMissingTest.java
```

测试类型：

- 白盒单元测试。
- 直接调用 `SimpleSqlRelationParser`。
- 使用长 SQL 字符串模拟过程、触发器、视图、函数对象体。
- 使用短 SQL 矩阵覆盖 JOIN 关键字、逗号 JOIN、别名和引用标识符组合。
- 使用方言化复杂 SQL 矩阵覆盖 MySQL/PostgreSQL 合法但差异明显的写法。
- 使用 evidence/confidence 专项测试断言证据类型、来源类型、joinKind 和最终评分。
- 使用 ANTLR shadow golden comparison 确保 shadow path 不丢 primary parser 当前识别的关系。
- 使用 MySQL/PostgreSQL adaptor-level zero-missing 测试，确保真实 adaptor 暴露的方言 ANTLR parser 在复杂 SQL fixture 上 `missingSimpleRelations` 持续为 0。
- 使用 DDL golden comparison、DDL 独立 visitor 测试和 DDL runner fallback 测试，覆盖 `simple-ddl`、`antlr-ddl-shadow`、`antlr-ddl-primary` 这条独立切换链路。
- 使用扫描级诊断测试覆盖 DDL/SQL/log parser 抛异常时的 warning 输出和原始 SQL/DDL 保留。
- 使用 MySQL/PostgreSQL adaptor DDL 测试覆盖方言 parser 失败时通过 `AdaptorContext` 上报 `DDL_PARSE_FAILED`。

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

### 3.9 SQL ANTLR 持续归零 fixture

新增 adaptor-level fixture 直接调用 `MySqlDatabaseAdaptor.sqlRelationParser()` 和 `PostgresDatabaseAdaptor.sqlRelationParser()`，不是只调用 core parser。目的是真实验证“一库一解析”的 wiring：

- MySQL multi-table `UPDATE orders o, users u JOIN accounts a ... WHERE o.user_id = u.id`：覆盖 MySQL `table_references` 中逗号表引用和显式 JOIN 混用。期望 ANTLR shadow 不缺 Simple baseline，并抽出 `orders.user_id -> users.id`、`users.account_id -> accounts.id`。
- MySQL `DELETE FROM o USING orders AS o LEFT JOIN users AS u ...`：覆盖 MySQL multi-table DELETE 的 `USING table_references` 写法。期望保留 `LEFT_JOIN` 语义，不把 `u.id IS NULL` 当成关系。
- MySQL CTE + `JOIN LATERAL (SELECT ro.user_id AS buyer_id)`：覆盖 CTE alias 进入 LATERAL derived table 的列血缘。期望回溯到 `orders.user_id -> users.id`，且不输出 `recent_orders`、`buyer_projection`、`lateral` 伪表。
- PostgreSQL recursive CTE：覆盖递归 rowset 自引用。期望保守输出 `employees.manager_id -> employees.id`，且不输出 `employee_paths` 伪表。
- PostgreSQL `LEFT JOIN LATERAL`：覆盖 correlated derived table。期望回溯 `orders.user_id -> users.id`，且不输出 lateral alias 伪表。
- PostgreSQL `MERGE INTO ... USING ... ON t.source_order_id = s.id`：覆盖 PostgreSQL MERGE join condition。期望输出 `target_orders.source_order_id -> source_orders.id`。

验收标准：

- 每个 fixture 的 `missingSimpleRelations` 必须为空。
- ANTLR shadow 可以输出额外弱关系，但如果额外关系来自 CTE、derived alias、function rowset 或 `LATERAL` 伪表，测试必须失败。
- 新增 fixture batch 后仍要重新执行全量 `mvn test`；只有持续归零并人工审核 extra，才能继续维持 MySQL/PostgreSQL SQL `antlr-primary + fallbackOnFailure=true` 灰度。

### 3.10 DDL ANTLR primary 链路

DDL 切换链路与 SQL 独立：

- `simple-ddl`：只运行现有 DDL parser。
- `antlr-ddl-shadow`：返回现有 DDL parser 结果，同时运行 ANTLR DDL extractor 并比较。
- `antlr-ddl-primary`：返回 ANTLR DDL 结果；缺失 baseline 时按配置 fallback 并输出 `ANTLR_DDL_PRIMARY_FALLBACK`。

当前 DDL golden fixture 覆盖：

- `CREATE TABLE ... FOREIGN KEY`
- inline `REFERENCES`
- `ALTER TABLE ... ADD CONSTRAINT`
- `CREATE INDEX`
- `CREATE UNIQUE INDEX`
- primary key / unique target evidence

验收标准：

- `missingSimpleDdlRelations` 必须为空。
- fallback warning 必须保留 `rawStatement` 和缺失的 DDL fingerprints。

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
- AliasExtractor 不再使用 `tmp_`、`temp_`、`input_`、`param_`、`filter_` 等命名前缀做全局跳过。真实业务表即使使用这些前缀，也会正常进入物理 alias map。

边界：

- 对于存储过程/函数内部显式创建的临时表，优先依赖 SQL 文本中的 `CREATE TEMP/TEMPORARY TABLE`，不需要 adaptor 额外查询数据库 metadata。
- ignored rowsets 是单次 `SqlStatementRecord` parse 内的局部集合；同名表在另一个函数、存储过程或普通 SQL 中不会被自动忽略。
- 已增加作用域测试：先解析一个函数，函数内 `selected_user_ids` 被忽略；随后解析普通 SQL，普通 SQL 中同名 `selected_user_ids` 可以被识别为 `selected_user_ids.user_id -> users.id`。
- 未在同一 SQL body 中通过 `CREATE TEMP/TEMPORARY TABLE` 显式创建的表，不会仅凭名字被当成临时表跳过。

### 3.16 方言化复杂 SQL 矩阵

测试文件：

```text
relation-core/src/test/java/com/relationdetector/core/DialectSqlRelationParserComplexMatrixTest.java
```

覆盖 MySQL：

- 反引号 identifier。
- 多层 CTE：`recent_orders -> regional_orders -> final SELECT`。
- multi-table `UPDATE ... JOIN ... SET ...`。
- multi-table `DELETE ... FROM ... LEFT JOIN ... WHERE ... IS NULL`。
- derived table 显式输出列名：`AS projected(order_id, buyer_id)`。
- recursive CTE 语法形态。

覆盖 PostgreSQL：

- 双引号 quoted identifier。
- 多层 CTE 和 quoted CTE 名称。
- `WITH RECURSIVE` 员工层级查询。
- `JOIN LATERAL (SELECT outer_alias.column ...) x ON true`。
- `unnest(...) WITH ORDINALITY AS input_ids(...)`。
- `MERGE INTO target USING source ON ...`。

未来 fixture：

- SQL Server `[schema].[table]`、`CROSS APPLY`、`OUTER APPLY` 作为 disabled test 保留，不要求当前通过。

新增测试发现并修复的问题：

- 递归 CTE 中 `employees.manager_id -> employees.id` 是合法 self-FK-like，但旧逻辑把所有同表谓词都过滤掉。现在只过滤同表同列，允许同表不同列，并增加受限启发：同表内 `*_id -> id` 可作为 self-FK-like。
- `JOIN LATERAL (SELECT o.user_id AS user_id) x` 中，derived body 没有自己的 `FROM`，但可以引用外层 alias。`SqlLineageResolver` 现在会把 derived table 之前已经出现的外层 alias 作为只读上下文，安全回溯 `x.user_id -> orders.user_id`。
- `MERGE INTO target_orders t USING source_orders s ON ...` 的 target alias 原来没有进入 alias map；同时 `WHEN MATCHED THEN UPDATE SET ...` 会被误读为 `UPDATE SET` 表。现在新增 `MERGE INTO` target 提取，并禁止 `UPDATE SET` 被当成 mutation target。
- `JOIN LATERAL` 和 `JOIN unnest(...)` 会被 regex 误读为物理表 `LATERAL` / `unnest`。现在 alias extractor 会跳过 rowset 修饰词和函数型 rowset，避免输出伪表关系。

### 3.17 Evidence 和 confidence 复杂断言

测试文件：

```text
relation-core/src/test/java/com/relationdetector/core/DialectParserEvidenceConfidenceTest.java
```

覆盖：

- `NATIVE_LOG` 中的 `LEFT JOIN` 输出 `SQL_LOG_JOIN`，source type 为 `NATIVE_LOG`，`attributes.joinKind=LEFT_JOIN`。
- view/procedure 对象分别输出 `VIEW_JOIN` / `PROCEDURE_JOIN`，source type 为 `DATABASE_OBJECT`。
- `IN (SELECT ...)` 和 `EXISTS (...)` 分别输出 `SQL_LOG_SUBQUERY_IN` / `SQL_LOG_EXISTS`。
- 给同一 SQL join 补充 `TARGET_UNIQUE`、`NAMING_MATCH`、`VALUE_CONTAINMENT_HIGH` 后，最终 confidence 保持当前公式结果 `0.7934`。
- 同一关系重复出现时，`rawEvidence` 保留每一次原始证据，摘要 evidence 记录 `count`，并生成有上限的 `REPEATED_OBSERVATION`。

### 3.18 ANTLR shadow golden comparison

测试文件：

```text
relation-core/src/test/java/com/relationdetector/core/AntlrShadowGoldenComparisonTest.java
adaptor-mysql/src/test/java/com/relationdetector/mysql/MySqlAntlrParserSelectionTest.java
adaptor-postgres/src/test/java/com/relationdetector/postgres/PostgresAntlrParserSelectionTest.java
```

覆盖：

- MySQL CTE + JOIN。
- MySQL `DELETE ... LEFT JOIN`。
- PostgreSQL nested CTE。
- PostgreSQL `MERGE`。

断言：

- `ShadowSqlRelationParser` 最终输出与 primary `SimpleSqlRelationParser` baseline 一致。
- ANTLR path 至少产生 `TABLE_REFERENCE` 和 `COLUMN_EQUALITY`。
- diagnostics 中包含 `PARSER_COMPARISON`。
- MySQL structured parser 输出 `attributes.grammar=MySqlRelationSql`、`attributes.parser=MySqlRelationSqlParser`、`attributes.eventVisitor=MySqlStructuredSqlEventVisitor`。
- PostgreSQL structured parser 输出 `attributes.grammar=PostgresRelationSql`、`attributes.parser=PostgresRelationSqlParser`、`attributes.eventVisitor=PostgresStructuredSqlEventVisitor`。
- MySQL/PostgreSQL shadow diagnostics 分别报告 `MySqlRelationExtractionVisitor` / `PostgresRelationExtractionVisitor`。
- PostgreSQL parser selection 测试包含负向断言：MySQL 反引号 SQL 不应被 PostgreSQL structured event visitor 当成表引用。

这个测试不是为了证明 ANTLR visitor 已经完全替代 primary parser，而是为了保护迁移过程：未来每迁移一种规则，ANTLR 可以多识别关系，但不能少于 golden baseline。

## 4. 测试执行结果

执行命令：

```bash
mvn test
```

结果：

```text
relation-core: Tests run: 80, Failures: 0, Errors: 0, Skipped: 1
adaptor-mysql: Tests run: 8, Failures: 0, Errors: 0, Skipped: 0
adaptor-postgres: Tests run: 9, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## 5. 测试发现并修复的问题

本轮长 SQL 测试发现了一个真实问题：

- 原逻辑在 `parseInSubquery` 中通过“整条 SQL 是否包含 `EXISTS`”来判断子查询 evidence type。
- 当同一存储过程同时包含 `IN` 和 `EXISTS` 时，`IN` 子查询关系会被误标成 `SQL_LOG_EXISTS`。

修复：

- `IN_SUBQUERY` regex 匹配出来的关系固定标记为 `SQL_LOG_SUBQUERY_IN`。
- SQL 日志/纯 SQL 中的 `EXISTS` 内部等值关系由 `parseExistsSubquery` 识别，并标记为 `SQL_LOG_EXISTS`。
- view/procedure/function/trigger 对象定义中的 `EXISTS` 内部等值关系继续保留对象来源证据，例如 `VIEW_JOIN`、`PROCEDURE_JOIN`、`TRIGGER_REFERENCE`。

本轮置信度算例测试又发现并修复了三类问题：

- DDL parser 中 `SOURCE_INDEX` 和 `TARGET_UNIQUE` 仍使用旧分值，已改为统一引用 `DefaultEvidenceScores` 中的 `0.10` 和 `0.18`。
- SQL 日志中的 `EXISTS` 子查询没有生成 `SQL_LOG_EXISTS` evidence，已增加专用解析，并避免同一谓词再被普通 equality parser 重复解析成 `SQL_LOG_JOIN`。
- trigger body 中的 `NEW.column` / `OLD.column` 无法解析回触发表，已在 trigger 解析上下文中把 `NEW` / `OLD` 映射到 `CREATE TRIGGER ... ON <table>` 的表。

本轮 SQL 写法矩阵测试又发现并修复了三类问题：

- `tmp_`、`input_`、`filter_` 等命名兜底会误跳过真实业务表，例如 `filter_rules`。已移除全局命名前缀跳过，仅保留同一 SQL body 内显式 `CREATE TEMP/TEMPORARY TABLE` 的局部忽略。
- 多列 tuple comparison 不会生成列级关系。已支持纯 `alias.column` 的 tuple equality 和 tuple `IN`，并按列顺序生成候选关系。
- `UPDATE ... FROM` 和 `DELETE ... USING` 的目标表 alias 没有进入 alias map。已支持 `UPDATE <table> [alias]`、`DELETE FROM <table> [alias]` 和 `USING <table> [alias]` 的基础提取，并补充了无 alias 的回归用例。

本轮 RelationshipMerger 测试发现并修复了一个评分问题：

- 同一关系在日志中重复出现时，原先会把多个相同 `SQL_LOG_JOIN = 0.55` 都套入 confidence 公式，导致普通日志关系被频率刷高。
- 现在同一关系内按 `EvidenceType + EvidenceSourceType + source + score` 聚合 evidence，保留基础分一次，并在 `attributes.count` 中记录出现次数。
- 输出同时保留两份证据：`rawEvidence` 是未压缩审计轨迹，`evidence` 是用于评分和展示的摘要证据。
- 当 `count > 1` 时，聚合 evidence 记录 `firstDetail`、`lastDetail`、最多 5 条 `sampleDetails` 和 `sampleTruncated`，便于排查首次、末次和中间代表性出现位置。
- 重复出现额外生成 `REPEATED_OBSERVATION`，使用 `0.10 * (1 - 1 / count)` 的递减增益；它最多接近 0.10，不能无限推高置信度。

本轮 DDL 测试覆盖并修复/固化了以下能力：

- inline references 不生成关系。
- ALTER TABLE 外键不生成关系。
- composite FK 只生成第一列关系。
- quoted schema-qualified table 名称清理不正确。
- PK/unique/source index 没有作为已有 FK candidate 的辅助 evidence。
- PostgreSQL `ALTER TABLE ONLY ... ADD CONSTRAINT ... FOREIGN KEY ... REFERENCES ... NOT VALID` 不能把 `ONLY` 当成表名。
- PostgreSQL `CREATE UNIQUE INDEX IF NOT EXISTS ... ON ... USING btree (...)` 应作为 target unique 辅助 evidence。
- PostgreSQL non-partial covering unique index `INCLUDE (...)` 只把 key column 当成唯一证据；partial covering unique index 仍跳过。
- MySQL 反引号表名、带连字符索引/约束名、`USING BTREE`、表选项、`ON DELETE` / `ON UPDATE` 动作不应干扰 FK 和索引证据。
- MySQL quoted prefix index，例如 `` KEY `idx-email` (`email`(10)) ``，仍不作为完整列 `SOURCE_INDEX`。

本轮方言复杂 SQL 矩阵又发现并修复了四类问题：

- 递归 CTE / self-reference 中的同表不同列关系被旧过滤条件漏掉；现在支持 `employees.manager_id -> employees.id` 这类受限 self-FK-like。
- LATERAL derived table 无本地 FROM 时无法追踪外层 alias；现在支持简单外层列投影，例如 `SELECT o.user_id AS user_id`。
- MERGE target alias 未进入 alias map；现在 `MERGE INTO target t USING source s ON t.source_id = s.id` 会输出普通 `SQL_LOG_JOIN` evidence。
- rowset 修饰词/函数被误当物理表；现在跳过 `LATERAL`、`unnest(...)`、`json_table(...)` 这类非持久表名。

本轮方言化 ANTLR parser 一期落地又固化了以下能力：

- MySQL/PostgreSQL 不再只通过同一个 `RelationSql.g4` 伪装方言；现在有独立 generated grammar：`MySqlRelationSql.g4` 和 `PostgresRelationSql.g4`。
- `MySqlAntlrSqlParser` / `PostgresAntlrSqlParser` 分别调用自己的 lexer/parser，并在 `StructuredParseResult.attributes` 中暴露 `grammar`、`lexer`、`parser` 和 `eventVisitor`。
- `StructuredSqlEventVisitor` 已从 parser 主体拆出；MySQL/PostgreSQL 各自有 event visitor，用于隔离 quoted identifier 规则。
- `RelationExtractionVisitor` 已不再委托 primary parser；它独立消费 ANTLR event 并产出基础 equality 关系。MySQL/PostgreSQL 方言子类仍保留，作为后续迁移方言专属规则的落点。
- PostgreSQL event visitor 不把 MySQL backtick identifier 当成 PostgreSQL 表引用。

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
- LATERAL/correlated derived table 中无本地 FROM 的简单外层列投影 lineage，例如 `JOIN LATERAL (SELECT o.user_id AS user_id) x`。
- 表达式投影的负向保护，避免把 `COALESCE`、聚合、窗口函数等输出列误判为精确 FK-like。
- 递归 CTE 中可安全回溯的 self-FK-like，例如 `employees.manager_id -> employees.id`。
- 显式 JOIN 中无别名、混合别名的表引用。
- 逗号 FROM 列表中无别名、混合别名的表引用。
- quoted qualified identifier，例如 `"public"."orders"` 和 `` `shop`.`orders` ``。
- MySQL 反引号和 PostgreSQL 双引号的基础清理。
- schema-qualified 表名。
- 复杂 comma join 中多个业务 FK predicate 和非业务过滤 predicate 混合。
- 多角色 FK 命名，例如 `created_user_id`、`updated_user_id`。
- 对象体内 `CREATE TEMP/TEMPORARY TABLE` 创建的本地临时表跳过。
- 不再因 `tmp_`、`input_`、`filter_` 等命名前缀全局跳过表。
- 纯列引用 tuple equality，例如 `(o.tenant_id, o.user_id) = (u.tenant_id, u.id)`。
- 纯列引用 tuple `IN`，例如 `(o.tenant_id, o.user_id) IN (SELECT u.tenant_id, u.id FROM users u)`。
- PostgreSQL 风格 `UPDATE ... FROM` 和 `DELETE ... USING` 中的基础 alias；有 alias 和无 alias 都有测试覆盖。
- PostgreSQL 风格 `MERGE INTO ... USING ... ON ...` 中的基础 alias 和 ON predicate。
- 函数型 rowset 负向保护，例如 `unnest(...) WITH ORDINALITY` 不作为物理表输出关系。
- 同一关系重复 evidence 的 `rawEvidence` 保留、`attributes.count` 聚合、`sampleDetails` 样本和 `REPEATED_OBSERVATION` 递减增益。
- DDL inline references、table-level FK、ALTER TABLE FK。
- DDL composite FK 按列顺序生成候选关系。
- DDL quoted schema-qualified identifier。
- DDL PK/unique/source index 辅助 evidence。
- DDL partial/expression/functional/prefix index 负向保护。
- DDL PostgreSQL `ALTER TABLE ONLY`、`CREATE UNIQUE INDEX IF NOT EXISTS`、covering unique index。
- DDL MySQL 反引号索引/约束、索引选项、referential actions 和 quoted prefix index。
- MySQL adaptor 私有归一化：`CREATE UNIQUE INDEX ... USING BTREE ON ... INVISIBLE` 可在 MySQL parser 中生成 `TARGET_UNIQUE`，但 core fallback 不识别该私有写法。
- PostgreSQL adaptor 私有归一化：`CREATE UNIQUE INDEX ... ON ONLY ...` 可在 PostgreSQL parser 中生成 `TARGET_UNIQUE`，但 core fallback 不识别该私有写法。
- ANTLR 结构化 parser 已进入 shadow mode：`AntlrStructuredSqlParser` 产生 `TABLE_REFERENCE`、`COLUMN_EQUALITY`、`PARSER_COMPARISON` 等事件，MySQL/PostgreSQL adaptor 负责选择自己的 ANTLR SQL/DDL parser。
- MySQL/PostgreSQL SQL shadow parser 已拆分 grammar 入口：MySQL 使用 `MySqlRelationSqlLexer/Parser`，PostgreSQL 使用 `PostgresRelationSqlLexer/Parser`，core fallback 仍保留 `RelationSqlLexer/Parser`。
- MySQL/PostgreSQL SQL parser 已拆分 structured event visitor 和 relation extraction visitor；`RelationExtractionVisitor` 现在独立消费 ANTLR 事件并产出基础 equality 关系，不再委托 `SimpleSqlRelationParser`。
- SQL parser 可通过 `parser.sql.mode` 选择 `simple`、`antlr-shadow`、`antlr-primary`。MySQL/PostgreSQL 默认灰度为 `antlr-primary + fallbackOnFailure=true`；缺失 Simple baseline 时会记录 `ANTLR_PRIMARY_FALLBACK` 并按配置回退。SQL Server/Oracle 仍为 future adaptor，不随本轮切 primary。
- 新 SQL 来源类型已纳入证据映射：materialized view/rule 使用 `VIEW_JOIN`，event/package 使用 `PROCEDURE_JOIN`，migration 使用普通 SQL 来源。
- 动态 SQL 识别：`PREPARE`、`EXECUTE`、`EXECUTE IMMEDIATE` 会生成 `DYNAMIC_SQL_UNRESOLVED` warning，并保留 `attributes.rawStatement`。
- 解析/提取失败诊断：DDL parser、SQL parser、object 文件、native log 失败会生成 warning，并在可取得输入文本时保留 `attributes.rawStatement`。
- JSON warning 输出包含 `attributes`，可携带 `rawStatement`、`statementSourceType`、`endLine`、`exceptionClass` 等诊断字段。

## 7. 当前边界

当前 MySQL/PostgreSQL 默认输出已进入 `antlr-primary + fallbackOnFailure=true` 灰度：ANTLR relation extractor 独立产出 table-reference、equality、IN/tuple IN、raw equality fallback、CTE/derived lineage 等关系；如果 `missingSimpleRelations` 非空则回退 Simple baseline。以下场景仍不保证准确：

- `SELECT *` 或 `alias.*` 的完整列展开。
- `UNION` / `INTERSECT` / `EXCEPT` 分支输出列和来源表之间的 lineage。
- 递归 CTE 中递归分支的稳定 lineage。
- 复杂 `LATERAL` / correlated subquery lineage，例如 LATERAL body 内有自己的多表 JOIN、聚合、窗口函数或表达式投影。
- `CROSS APPLY` / `OUTER APPLY` 的列来源回溯。
- `MERGE` 的复杂写入侧 lineage，例如 `WHEN MATCHED UPDATE SET t.col = s.col` 或 `RETURNING` 输出列来源。
- 复杂 `UPDATE` / `DELETE`，例如 CTE write、`RETURNING` lineage、多目标 MySQL delete。
- 数据修改 CTE 的 `RETURNING` 列 lineage。
- JSON_TABLE、unnest、set-returning function 等函数型行集的列来源。
- 动态 SQL，例如 MySQL `PREPARE` 或 PL/pgSQL `EXECUTE format(...)`。当前会报告 `DYNAMIC_SQL_UNRESOLVED`，但不会猜测拼接后的关系。
- 复杂表达式投影，例如 `COALESCE(o.user_id, x.user_id) IN (...)`。
- 复杂多列 tuple comparison，例如 tuple 项包含函数、表达式、derived table 投影、`ANY/ALL`、`UNION` 或列数不一致。
- 非等值关联，例如 range join、JSON path join。
- 窗口函数、聚合、`GROUP BY` 本身不表达表关系，只有其内部引用的 JOIN/子查询可能表达关系。
- 输入过滤表识别依赖同一对象体内的 `CREATE TEMP/TEMPORARY TABLE`；不再使用命名前缀兜底。
- DDL parser 仍不是完整数据库方言 parser；复杂分区表、继承表、排除约束、跨方言特殊索引参数、动态生成 DDL 等仍建议交给数据库 adaptor 的元数据 collector 或未来 parser-backed 实现。

后续如果要覆盖这些场景，应按设计文档逐步把具体规则迁移到 ANTLR event/visitor 语义层，或为 MySQL/PostgreSQL 接入更完整的官方/社区 grammar；不要继续堆叠 regex。
