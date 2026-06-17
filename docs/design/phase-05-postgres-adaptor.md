# Phase 5：PostgreSQL Adaptor 详细设计

## 目标

实现 PostgreSQL adaptor，使工具能够从 PostgreSQL 12+ 获取关系证据。该 adaptor 与 MySQL adaptor 具备同等能力，但遵循 PostgreSQL 的 catalog、schema、标识符和日志规则。

Phase 5 先完成 PostgreSQL 数据采集和基础证据生成；复杂 SQL/DDL 解析规则在 Phase 6 统一增强。

## 支持范围

- PostgreSQL 12+。
- 单 schema 扫描，默认 `public`。
- 只读元数据权限优先。
- include/exclude 表过滤。

## Identifier 规则

PostgreSQL 特点：

- 未加双引号的标识符会折叠为小写。
- 加双引号的标识符保留大小写。
- database 与 schema 是不同概念。

设计：

- adaptor 负责解析和规范化双引号标识符。
- 未引用标识符 normalized 为小写。
- schema 缺省时使用配置 schema 或 `search_path` 中的首选 schema。
- core 不自行处理 PostgreSQL 大小写规则。

## 元数据采集

主要读取：

- `information_schema.tables`
- `information_schema.columns`
- `information_schema.table_constraints`
- `information_schema.key_column_usage`
- `information_schema.constraint_column_usage`
- `pg_catalog.pg_class`
- `pg_catalog.pg_namespace`
- `pg_catalog.pg_attribute`
- `pg_catalog.pg_index`
- `pg_catalog.pg_constraint`

### 表和列

采集：

- table schema。
- table name。
- table type。
- column name。
- data type。
- nullable。
- character maximum length。
- numeric precision/scale。
- udt name。

### 主键和唯一约束

优先从 `pg_catalog.pg_constraint` 和 `pg_index` 获取：

- primary key。
- unique constraint。
- unique index。

用途：

- 推断 JOIN 方向。
- 生成 `TARGET_UNIQUE` evidence。

### 外键

从 `pg_constraint` 获取：

- `contype = 'f'`
- source table。
- source columns。
- referenced table。
- referenced columns。
- constraint name。

输出 evidence：

```text
METADATA_FOREIGN_KEY
relationType: FK_LIKE
relationSubType: DECLARED_FK
score: 0.98
```

## DDL 解析补丁

PostgreSQL DDL 特点：

- 双引号标识符。
- `SERIAL`、`BIGSERIAL`。
- `GENERATED ... AS IDENTITY`。
- `CREATE INDEX ... USING btree/gin/gist`。
- `ALTER TABLE ONLY ... ADD CONSTRAINT ...`。

PostgreSQL adaptor 负责：

- 处理 `ONLY`。
- 处理 identity/serial 语法。
- 处理 index method。
- 保留 FK、unique、index 信息。

## 对象定义采集

支持对象：

- function。
- procedure。
- view。
- materialized view。
- rule。
- trigger function。

### 函数和过程

从 `pg_proc`、`pg_namespace` 读取：

- schema。
- name。
- kind。
- definition，可通过 `pg_get_functiondef(oid)` 获取。

权限不足：

- 记录 warning。
- 允许用户通过本地 SQL 文件补充。

### 视图

从 `pg_views` 或 `pg_get_viewdef` 读取：

- schema。
- view name。
- definition。

### 物化视图

从 `pg_matviews` 读取：

- schema。
- materialized view name。
- definition。

物化视图的定义 SQL 与普通 view 一样可能包含稳定 JOIN、CTE 和子查询，因此解析后 JOIN evidence 使用 `VIEW_JOIN`。对象类型仍保持 `MATERIALIZED_VIEW`，避免运维误以为它只是普通 view。

### Rule

从 `pg_rules` 读取：

- schema。
- table name。
- rule name。
- definition。

rule 会重写表操作，定义中可能包含 `DO ALSO` / `DO INSTEAD` 的 SQL。系统把它映射为 `DatabaseObjectType.RULE` / `StatementSourceType.RULE`，JOIN evidence 默认按 view 类定义处理。

### 触发器

从 `pg_trigger` 和 `pg_proc` 获取：

- 触发器所属表。
- 触发器函数定义。
- 触发事件。

PostgreSQL 触发器逻辑通常在函数中，因此需要把 trigger 与 trigger function 关联。

## 日志提取

支持：

- PostgreSQL `log_statement` 文本日志。
- 常见 `duration: ... statement:` 格式。
- 清洗后的纯 SQL 文本。

提取规则：

- 识别 `statement:` 后的 SQL。
- 识别 `execute <name>:` 后的 SQL。
- 跳过 connection/auth/checkpoint 等非 SQL 日志。
- 多行 SQL 按日志前缀或分号归并。

## 数据画像查询

默认关闭，只对已有候选关系运行。

PostgreSQL 实现：

- 使用 `LIMIT` 控制采样。
- 可选 `statement_timeout` 控制超时。
- 对候选 source distinct 值与 target 列做匹配。
- 利用 `pg_stats` 作为轻量辅助，但不能只依赖统计信息得出值域包含结论。

示意：

```sql
SET LOCAL statement_timeout = '30s';

SELECT COUNT(*) matched
FROM (
  SELECT DISTINCT source_col
  FROM source_table
  WHERE source_col IS NOT NULL
  LIMIT $1
) s
JOIN target_table t ON s.source_col = t.target_col;
```

## 权重修正

PostgreSQL adaptor 可以修正：

- `log_statement` 可信度。
- view/function 定义中的 JOIN 可信度。
- quoted identifier 解析置信度。

修正后仍由 core 统一合并。

## 验收标准

- 可通过 JDBC 读取 PostgreSQL 表、列、PK、FK、unique、index。
- 可读取 function/procedure/view/materialized view/rule/trigger function 定义。
- 可从 PostgreSQL statement log 提取 SQL。
- 能正确处理 schema 和双引号标识符。
- 权限不足时产生 warning，不导致整个扫描失败。

## 测试设计

- Testcontainers PostgreSQL 12+ 集成测试。
- 显式 FK 元数据采集测试。
- unique/index 采集测试。
- quoted identifier 测试。
- view definition 采集测试。
- materialized view definition 采集测试。
- rule definition 采集测试。
- trigger function 关联测试。
- statement log 提取测试。
- 权限不足 warning 测试。
- include/exclude 过滤测试。
