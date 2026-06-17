# Phase 4：MySQL Adaptor 详细设计

## 目标

实现 MySQL adaptor，使工具能够从 MySQL 5.7/8.0+ 获取关系证据。该 adaptor 必须通过 Phase 3 的 `DatabaseAdaptor` API 接入，不直接依赖 CLI。

Phase 4 先完成 MySQL 数据采集和基础证据生成；复杂 SQL/DDL 解析规则在 Phase 6 统一增强，但 MySQL adaptor 需要提供方言补丁和日志提取能力。

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
- `information_schema.TABLE_CONSTRAINTS`
- `information_schema.KEY_COLUMN_USAGE`
- `information_schema.STATISTICS`

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

## DDL 解析补丁

MySQL DDL 特点：

- 反引号标识符。
- `ENGINE=InnoDB`、`CHARSET=utf8mb4` 等表选项。
- `KEY`/`INDEX` 语法。
- `CONSTRAINT ... FOREIGN KEY ... REFERENCES ...`。

MySQL adaptor 负责：

- 在通用 parser 失败时做预处理。
- 去除或忽略表选项。
- 保留约束、索引和列定义。
- 识别 MySQL 特有 `KEY idx_name (col)`。

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
