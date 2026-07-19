# Phase 4：MySQL Adaptor 详细设计

## 目标

实现 MySQL adaptor，使工具能够从 MySQL 5.7/8.0+ 获取关系证据。该 adaptor 必须通过 Phase 3 的 `DatabaseAdaptor` API 接入，不直接依赖 CLI。

当前 MySQL adaptor 不只负责采集，也负责 MySQL 方言 parser binding、typed adapter、visitor 和版本策略：
token-event parser 位于 `com.relationdetector.mysql.tokenevent`，MySQL 5.7 / 8.0 full-grammar module
分别位于 `com.relationdetector.mysql.fullgrammar.v5_7` 和 `com.relationdetector.mysql.fullgrammar.v8_0`。
所有 `.g4` 与 generated parser artifact 由 `relation-detector/grammar/*` 模块拥有，adaptor 只消费这些
artifact；core 只通过 runner 和 `FullGrammarDialectModule` registry 调度。

MySQL 同时实现当前 SPI v6 的 `MySqlScriptFramer`。它使用 MySQL generated script lexer 的 typed lexeme 识别 `DELIMITER` directive，并以当前 delimiter 切分 server statement；quoted text 与 comment 中的 delimiter 不参与分割。这个 framing 在 SQL/DDL grammar 之前完成，不再由 core raw-SQL splitter 猜测 routine 或 object 边界。

MySQL 目前有两个严格 full-grammar profile：`mysql/5.7` 和 `mysql/8.0`。二者使用 grammar 模块中
固定的 grammars-v4 MySQL snapshot，以及 adaptor 内的 typed parse-tree visitor、expression analyzer
和 DDL collector；`mysql/5.7` 以 MySQL 5.7 官方文档作为版本边界 source-of-truth，在 grammar 层
禁用 CTE、recursive CTE、window function、`JSON_TABLE`、invisible index 等 8.0-only 能力。

## 支持范围

- MySQL 5.7。
- MySQL 8.0+。
- 单 schema/database 扫描。
- 只读元数据权限优先。
- include/exclude 表过滤。

## Identifier 规则

MySQL 特点：

- MySQL 的 SQL/catalog 元数据把 database 暴露为 `TABLE_SCHEMA`，但 relation-detector 的稳定
  `TableId` 约定将 MySQL database 放在 `catalog`，`schema` 通常为空。
- 反引号包裹标识符：`` `orders` ``。
- 大小写敏感受操作系统和 `lower_case_table_names` 影响。

设计：

- adaptor 提供 `IdentifierRules`。
- 默认 normalizedName 使用连接读取到的实际名称。
- 比较时优先使用 adaptor 规范化结果，不在 core 中强行转小写。
- 解析 SQL 时去除反引号并保留原始名。
- 配置中的 database scope、JDBC catalog selection、metadata fact 和 SQL endpoint 必须最终落到
  同一 catalog 轴；不得把 `ScanScope.schema` 无条件当成 canonical schema，再与写入 catalog 的
  `TABLE_SCHEMA` 做显示等价。
- 配置优先读取 `database.catalog`；旧 `database.schema` 只作为兼容回退。两者同时非空且不同时，
  `MySqlDatabaseAdaptor.canonicalizeScope()` 必须在首次 JDBC 调用前拒绝配置。
- canonical scope 固定为 `catalog=<database>, schema=null`。metadata、database DDL、object、FK、
  known-physical inventory 和 live DDL qualification 均保留该 namespace pair。
- `TableId.normalizedName` 只保存 schema/table 形式；MySQL schema 为空时就是 table name，不能重复
  拼入 catalog，也不能使用 `TableId.of(database, table)` 把 database 放到 schema 轴。

当前实现已统一这条映射。MySQL adaptor 的 `IdentifierRules` 声明
`QualifiedNameSemantics.CATALOG_TABLE`，因此 token-event/full-grammar 中显式写出的
`database.table` 由 `CanonicalIdentifierResolver` 解析为 catalog/table。relationship、lineage、
naming、profile、known-physical inventory 和 live DDL qualification 复用同一 canonical endpoint
provider；不使用 endpoint 名称特例，也不把 metadata 降级回 schema。

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
- 两条 DDL parser 链路都输出 `DDL_FOREIGN_KEY` / `DDL_INDEX` / `DDL_COLUMN` 结构事件；最终 relationship
  仍由 core 的 `DdlRelationExtractionVisitor` 生成，`DDL_COLUMN` 只补充 inventory/naming evidence。

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

当前 MySQL correctness golden 分三类：root token-event baseline、MySQL 5.7 strict full-grammar 和
MySQL 8.0 strict full-grammar。三者路径和当前数量以 verification session 的
`reports/correctness-test-summary.md` 为唯一生成源；本 Phase 文档
只维护版本职责，不复制会漂移的计数。5.7 资产分为原样兼容、5.7 语义等价改写和 8.0-only
版本边界负向 fixture。

当前 MySQL 5.7 / 8.0 full-grammar 都有独立 versioned golden，不再由 root token-event baseline 兜底。full-grammar 相对 token-event 能识别更多 procedure body、复杂 business query、sample-data DDL/SQL、derived projection、INSERT/UPDATE 写入映射和表达式来源；MySQL 8.0 还覆盖 CTE、window、`JSON_TABLE`、invisible index 等高版本语法。参数、literal、局部变量、JSON path、动态 SQL 和显式临时表仍不进入 v1 physical lineage。

如果后续要支持 MySQL 8.4 等新的严格 full-grammar，应继续新增独立 version package 和对应 version golden，例如 `mysql/v8_4`，而不是修改 `mysql/v5_7`、`mysql/v8_0` 或 root token-event baseline。

## 对象定义采集

支持对象：

- procedure。
- function。
- view。
- trigger。
- event / scheduler job。

`information_schema` 只用于枚举对象身份。collector 随后逐对象执行对应 `SHOW CREATE`，把完整
`CREATE PROCEDURE/FUNCTION/TRIGGER/EVENT/VIEW` declaration 交给 typed script framer 和 parser。
因此 routine 参数、trigger timing/target table 等 scope 信息不会因只读取 body fragment 而丢失。
单个对象的 `SHOW CREATE` 失败时产生脱敏 warning 并跳过该对象，不用 fragment 冒充完整声明。
warning 保留已知 object catalog/schema/name/type 和异常分类字段，但不包含 rendered SQL、JDBC URL、
driver message 或参数值。

### 过程和函数

从 `information_schema.ROUTINES` 枚举：

- `ROUTINE_SCHEMA`
- `ROUTINE_NAME`
- `ROUTINE_TYPE`
随后按类型执行 `SHOW CREATE PROCEDURE` 或 `SHOW CREATE FUNCTION`。

如果权限不足：

- 记录 warning。
- 允许用户通过本地 SQL 文件补充。

### 视图

从 `information_schema.VIEWS` 枚举 schema/name，随后执行 `SHOW CREATE VIEW`。完整声明中的查询体由
当前 parser mode 处理。

- `TABLE_SCHEMA`
- `TABLE_NAME`

视图中的 JOIN 通常比 SQL 日志更稳定，evidence 默认使用 `VIEW_JOIN`。

### 触发器

从 `information_schema.TRIGGERS` 枚举 schema/name，随后执行 `SHOW CREATE TRIGGER`。

- `TRIGGER_SCHEMA`
- `TRIGGER_NAME`

触发器引用可能表达业务写入关系，例如订单写审计表，evidence 默认使用 `TRIGGER_REFERENCE`。

### Event / Scheduler Job

从 `information_schema.EVENTS` 枚举 schema/name，随后执行 `SHOW CREATE EVENT`：

- `EVENT_SCHEMA`
- `EVENT_NAME`

event body 可能包含 `INSERT ... SELECT`、`UPDATE`、`DELETE`、JOIN 或嵌套查询。系统把它映射为
`DatabaseObjectType.EVENT` / `StatementSourceType.EVENT` 并保留对象 provenance；当前 predicate evidence
仍使用 `SQL_LOG_JOIN|SQL_LOG_EXISTS|SQL_LOG_SUBQUERY_IN`，不会仅因对象类型改成保留但未产出的
`PROCEDURE_JOIN`。

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

MySQL live 实现：

- 对候选 source/target 列执行只读聚合，不返回业务值。
- 精确返回 source 非空行数、source distinct、matched distinct 和 target distinct。
- target unique/index gate 来自 metadata candidate selection，不由 profile SQL重新证明。
- 使用 JDBC query timeout 控制单候选执行时间；候选总量和每个 source 的 target 数由 live profile
  配置限制。

示意：

```sql
SELECT
  (SELECT COUNT(*) FROM source_table WHERE source_col IS NOT NULL) AS source_non_null_rows,
  (SELECT COUNT(DISTINCT source_col) FROM source_table WHERE source_col IS NOT NULL) AS source_distinct,
  (SELECT COUNT(DISTINCT s.source_col)
     FROM source_table s JOIN target_table t ON t.target_col = s.source_col
    WHERE s.source_col IS NOT NULL) AS matched_distinct,
  (SELECT COUNT(DISTINCT target_col) FROM target_table WHERE target_col IS NOT NULL) AS target_distinct
```

## 权重修正

MySQL adaptor 可以修正：

- slow log 中出现次数较高的 JOIN evidence。
- general log 中重复同一 SQL 的 evidence。
- 触发器引用的方向置信度。

修正后仍由 core 统一合并。

## 验收标准

- 可通过 JDBC 读取 MySQL 表、列、PK、FK、unique、index。
- 可从 catalog 枚举 procedure/function/view/trigger/event，并通过对应的
  `SHOW CREATE` 获取完整 parser-grade declaration。
- 可从 MySQL general/slow log 提取 SQL。
- 可生成 `METADATA_FOREIGN_KEY`、`SOURCE_INDEX`、`TARGET_UNIQUE`、对象定义和日志相关 evidence。
- 权限不足时产生 warning，不导致整个扫描失败。

## 测试设计

- 当前自动化测试使用 fake JDBC/proxy验证查询绑定、catalog identity、partial warning和输出契约；
  Testcontainers/真实 MySQL 8 权限组合仍是环境性验收项。
- 显式 FK 元数据采集测试。
- unique/index 采集测试。
- view definition 采集测试。
- trigger action statement 采集测试。
- event definition 采集测试。
- general log 提取测试。
- slow log 提取测试。
- 权限不足 warning 测试。
- include/exclude 过滤测试。
