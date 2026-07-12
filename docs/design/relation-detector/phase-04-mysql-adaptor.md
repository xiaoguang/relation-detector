# Phase 4：MySQL Adaptor 详细设计

## 目标

实现 MySQL adaptor，使工具能够从 MySQL 5.7/8.0+ 获取关系证据。该 adaptor 必须通过 Phase 3 的 `DatabaseAdaptor` API 接入，不直接依赖 CLI。

当前 MySQL adaptor 不只负责采集，也负责 MySQL 方言 parser 实现：token-event parser 位于 `com.relationdetector.mysql.tokenevent`，MySQL 5.7 / 8.0 full-grammar module 分别位于 `com.relationdetector.mysql.fullgrammar.v5_7` 和 `com.relationdetector.mysql.fullgrammar.v8_0`。core 只通过 runner 和 `FullGrammarDialectModule` registry 调度，不在 core 里 hard-code MySQL 版本实现。

MySQL 同时实现 SPI v4 的 `MySqlScriptFramer`。它使用 MySQL generated script lexer 的 typed lexeme 识别 `DELIMITER` directive，并以当前 delimiter 切分 server statement；quoted text 与 comment 中的 delimiter 不参与分割。这个 framing 在 SQL/DDL grammar 之前完成，不再由 core raw-SQL splitter 猜测 routine 或 object 边界。

MySQL 目前有两个严格 full-grammar profile：`mysql/5.7` 和 `mysql/8.0`。二者都使用 `adaptor-mysql` 内 vendored grammars-v4 MySQL grammar 固定快照、typed parse-tree visitor、expression analyzer 和 DDL collector；`mysql/5.7` 以 MySQL 5.7 官方文档作为版本边界 source-of-truth，在 grammar 层禁用 CTE、recursive CTE、window function、`JSON_TABLE`、invisible index 等 8.0-only 能力。未知版本仍依赖 token-event 的宽松兼容能力，或者后续新增独立 strict full-grammar profile。

## 支持范围

- MySQL 5.7。
- MySQL 8.0+。
- 单 schema/database 扫描。
- 只读元数据权限优先。
- include/exclude 表过滤。

## Identifier 规则

MySQL 特点：

- database 通常等价于 schema。
- 反引号包裹标识符：`` `orders` ``。
- 大小写敏感受操作系统和 `lower_case_table_names` 影响。

设计：

- adaptor 提供 `IdentifierRules`。
- 默认 normalizedName 使用连接读取到的实际名称。
- 比较时优先使用 adaptor 规范化结果，不在 core 中强行转小写。
- 解析 SQL 时去除反引号并保留原始名。

## 元数据采集

主要读取：

- `information_schema.TABLES`
- `information_schema.COLUMNS`
- `information_schema.REFERENTIAL_CONSTRAINTS`
- `information_schema.TABLE_CONSTRAINTS`
- `information_schema.KEY_COLUMN_USAGE`
- `information_schema.STATISTICS`

采集结果分两层：

- `MetadataSnapshot.relationships()` 继续输出显式 FK 关系，保持既有 JSON relationship 兼容。
- `MetadataSnapshot.tableFacts/columnFacts/indexFacts/constraintFacts` 保存完整 catalog facts，用于后续增强已有候选关系，例如 `TARGET_UNIQUE`、`SOURCE_INDEX`、`COLUMN_TYPE_COMPATIBLE`。这些 facts 不会单独创造 FK-like，避免“同名列 + 索引”导致误报。

### 表和列

查询目标 schema 下的 base table 和 view：

- table name。
- table type。
- column name。
- data type。
- nullable。
- character maximum length。
- numeric precision/scale。
- column default。

### 主键和唯一约束

从 `TABLE_CONSTRAINTS` 和 `KEY_COLUMN_USAGE` 获取：

- `PRIMARY KEY`
- `UNIQUE`

用途：

- 判断 target 列是否唯一。
- 辅助 JOIN 推断方向。
- 作为 `TARGET_UNIQUE` evidence。

### 外键

从 `KEY_COLUMN_USAGE` 获取：

- `TABLE_SCHEMA`
- `TABLE_NAME`
- `COLUMN_NAME`
- `REFERENCED_TABLE_SCHEMA`
- `REFERENCED_TABLE_NAME`
- `REFERENCED_COLUMN_NAME`
- `CONSTRAINT_NAME`

输出 evidence：

```text
METADATA_FOREIGN_KEY
relationType: FK_LIKE
relationSubType: DECLARED_FK
score: 0.98
```

### 索引

从 `STATISTICS` 获取：

- index name。
- column name。
- non unique。
- sequence in index。

用途：

- 源列有索引时提供 `SOURCE_INDEX` evidence。
- 唯一索引提供 `TARGET_UNIQUE` evidence。
- 复合索引按列序保留，后续可支持复合关系。

### 数据库内 DDL：SHOW CREATE TABLE

当 `sources.ddl.enabled: true` 且 `sources.ddl.fromDatabase: true` 时，MySQL adaptor 会对 scope 内表执行：

```sql
SHOW CREATE TABLE `shop`.`orders`;
```

规则：

- 先从 `information_schema.TABLES` 读取 base table 列表，并遵守 `includeTables/excludeTables`。
- 每张表单独执行 `SHOW CREATE TABLE`；某一张表失败时记录 `MYSQL_SHOW_CREATE_TABLE_FAILED` warning，并继续读取其它表。
- 返回的 `DatabaseDdlDefinition.source` 固定为 `SHOW CREATE TABLE`。
- core 的 `DdlRelationParserRunner` 负责解析该 DDL，输出 evidence source type 为 `DATABASE_DDL`，区别于本地 DDL 文件的 `DDL_FILE`。

## DDL 解析补丁

MySQL DDL 特点：

- 反引号标识符。
- `ENGINE=InnoDB`、`CHARSET=utf8mb4` 等表选项。
- `KEY`/`INDEX` 语法。
- `CONSTRAINT ... FOREIGN KEY ... REFERENCES ...`。

MySQL adaptor 负责：

- `mysql.tokenevent.MySqlTokenEventStructuredDdlParser` 暴露 MySQL token-event DDL parser。
- `MySqlRelationSql.g4` 与 `MySqlTokenEventParseTreeVisitor` 处理 MySQL DDL 方言差异，例如反引号、inline `KEY/INDEX`、prefix index、functional/JSON index、VISIBLE/INVISIBLE、表选项。
- `mysql.fullgrammar.v5_7` 和 `mysql.fullgrammar.v8_0` 分别注册 MySQL 5.7 / 8.0 full-grammar DDL parser，用于 `parser.mode=auto|full-grammar` 且 profile 可选中时。
- 两条 DDL parser 链路都只输出 `DDL_FOREIGN_KEY` / `DDL_INDEX` 结构事件；最终 relationship 仍由 core 的 `DdlRelationExtractionVisitor` 生成。

## MySQL full-grammar 与 SQL_MODE

MySQL full-grammar runtime 中存在 `MySqlGrammarSqlMode` / `MySqlGrammarSqlModes` helper。它们只表示 MySQL 自身的 `SQL_MODE` 语法开关，例如 `ANSI_QUOTES`、`PIPES_AS_CONCAT`，不是系统运行模式。

系统 parser 运行模式只有：

```text
parser.mode = auto | full-grammar | token-event
```

二者不能混用：

- `parser.mode` 决定运行时选择 full-grammar 还是 token-event。
- MySQL `SQL_MODE` 决定 MySQL full-grammar 内部如何理解某些 token 和操作符。
- PostgreSQL 没有 MySQL `SQL_MODE` 等价机制，因此不会有对应 helper。

## Correctness 与 golden 状态

当前 MySQL correctness golden 分三类：

- root MySQL correctness：`test-fixtures/correctness/mysql`，83 个 fixture，SQL/DDL 为 65/18，覆盖 MySQL metadata/DDL/log/object/procedure/business SQL 和 sample-data 切片，是正式 token-event baseline；当前 relation 659、lineage 349、relationship `NAMING_MATCH` 252、top-level namingEvidence 321。
- MySQL 5.7 full-grammar：`test-fixtures/correctness/mysql/v5_7`，89 个 fixture，SQL/DDL 为 71/18，manifest 强制 `parserMode: full-grammar`、`grammarProfile: mysql/5.7`，是 MySQL 5.7 strict version golden；当前 relation 706、lineage 414、relationship `NAMING_MATCH` 257、top-level namingEvidence 327。它从 MySQL 8.0 fixture 迁移而来，分为原样兼容、5.7 语义等价改写和 8.0-only 版本边界负向 fixture。
- MySQL 8.0 full-grammar：`test-fixtures/correctness/mysql/v8_0`，89 个 fixture，SQL/DDL 为 71/18，manifest 强制 `parserMode: full-grammar`、`grammarProfile: mysql/8.0`，是 MySQL 8.0 strict version golden；当前 relation 923、lineage 398、relationship `NAMING_MATCH` 421、top-level namingEvidence 491。

当前 MySQL 5.7 / 8.0 full-grammar 都有独立 versioned golden，不再由 root token-event baseline 兜底。full-grammar 相对 token-event 能识别更多 procedure body、复杂 business query、sample-data DDL/SQL、derived projection、INSERT/UPDATE 写入映射和表达式来源；MySQL 8.0 还覆盖 CTE、window、`JSON_TABLE`、invisible index 等高版本语法。参数、literal、局部变量、JSON path、动态 SQL 和显式临时表仍不进入 v1 physical lineage。

如果后续要支持 MySQL 8.4 等新的严格 full-grammar，应继续新增独立 version package 和对应 version golden，例如 `mysql/v8_4`，而不是修改 `mysql/v5_7`、`mysql/v8_0` 或 root token-event baseline。

## 对象定义采集

支持对象：

- procedure。
- function。
- view。
- trigger。
- event / scheduler job。

### 过程和函数

优先从 `information_schema.ROUTINES` 读取：

- `ROUTINE_SCHEMA`
- `ROUTINE_NAME`
- `ROUTINE_TYPE`
- `ROUTINE_DEFINITION`

如果权限不足：

- 记录 warning。
- 允许用户通过本地 SQL 文件补充。

### 视图

从 `information_schema.VIEWS` 读取：

- `TABLE_SCHEMA`
- `TABLE_NAME`
- `VIEW_DEFINITION`

视图中的 JOIN 通常比 SQL 日志更稳定，evidence 默认使用 `VIEW_JOIN`。

### 触发器

从 `information_schema.TRIGGERS` 读取：

- `TRIGGER_SCHEMA`
- `TRIGGER_NAME`
- `EVENT_OBJECT_TABLE`
- `ACTION_STATEMENT`

触发器引用可能表达业务写入关系，例如订单写审计表，evidence 默认使用 `TRIGGER_REFERENCE`。

### Event / Scheduler Job

从 `information_schema.EVENTS` 读取：

- `EVENT_SCHEMA`
- `EVENT_NAME`
- `EVENT_DEFINITION`

event body 可能包含 `INSERT ... SELECT`、`UPDATE`、`DELETE`、JOIN 或嵌套查询。系统把它映射为 `DatabaseObjectType.EVENT` / `StatementSourceType.EVENT`，证据默认按持久化过程逻辑处理，即 JOIN 使用 `PROCEDURE_JOIN`。

## 日志提取

支持：

- MySQL general log 文本。
- MySQL slow log 文本。
- 清洗后的纯 SQL 文本。

### general log

目标：

- 从包含连接 id、时间、命令类型的文本中提取 SQL。
- 只处理 `Query` 类记录。
- 跳过 `Connect`、`Quit` 等非 SQL 记录。

### slow log

目标：

- 跳过 `# Time`、`# User@Host`、`# Query_time` 等元信息。
- 提取 `SET timestamp=...;` 后的 SQL 或下一段 SQL。
- 保留原始文件名和行号范围，供 evidence detail 使用。

## 数据画像查询

默认关闭。

启用后只对已有候选关系运行：

- 显式 FK 不必画像，除非用户要求验证。
- JOIN/命名/共现候选可以画像。

MySQL 实现：

- 对 source 列抽样 distinct 值。
- 计算目标列匹配数量。
- 判断 target 是否 unique。
- 采样 SQL 必须加 limit 或使用受控策略。
- 必须设置 statement timeout 或通过 JDBC query timeout 控制。

示意：

```sql
SELECT COUNT(*) matched
FROM (
  SELECT DISTINCT source_col
  FROM source_table
  WHERE source_col IS NOT NULL
  LIMIT ?
) s
JOIN target_table t ON s.source_col = t.target_col
```

## 权重修正

MySQL adaptor 可以修正：

- slow log 中出现次数较高的 JOIN evidence。
- general log 中重复同一 SQL 的 evidence。
- 触发器引用的方向置信度。

修正后仍由 core 统一合并。

## 验收标准

- 可通过 JDBC 读取 MySQL 表、列、PK、FK、unique、index。
- 可读取 procedure/function/view/trigger/event 定义。
- 可从 MySQL general/slow log 提取 SQL。
- 可生成 `METADATA_FOREIGN_KEY`、`SOURCE_INDEX`、`TARGET_UNIQUE`、对象定义和日志相关 evidence。
- 权限不足时产生 warning，不导致整个扫描失败。

## 测试设计

- Testcontainers MySQL 8 集成测试。
- 显式 FK 元数据采集测试。
- unique/index 采集测试。
- view definition 采集测试。
- trigger action statement 采集测试。
- event definition 采集测试。
- general log 提取测试。
- slow log 提取测试。
- 权限不足 warning 测试。
- include/exclude 过滤测试。
