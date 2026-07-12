# 数据库表关系探测工具执行计划

## 1. 项目目标

构建一个 Java 17 + Maven 的 CLI 命令行工具，用于自动探测数据库中的表关系，并为每条关系输出置信度、证据来源和解释信息。

v1 成熟支持 MySQL 和 PostgreSQL；Oracle 已接入初始 adaptor、Oracle token-event fallback 和 `INCOMPLETE_VERSIONED` versioned full-grammar；SQL Server 已接入 adaptor、token-event baseline、2016/2017/2019/2022/2025 versioned full-grammar sample-data golden，以及首批 grammar-level 官方版本边界。系统架构继续保留数据库 adaptor 扩展接口，后续可以持续补强 Oracle 官方严格 grammar、更多 SQL Server 官方逐版本 T-SQL family 和真实数据库 runtime smoke。

本工具同时是后续语义层系统的事实采集与证据生成子系统。更上层的 Evidence-Grounded Semantic Layer 负责把 relationship、Data Lineage、metadata、SQL source 和注释组织成可审核的业务语义对象，用于自然语言问答、SQL draft 生成和指标候选审核。整体设计见 [Evidence-Grounded Semantic Layer 整体设计](../design/semantic-layer-overall-design.md)。当前语义层代码已独立在 `semantic-layer/semantic-core` 和 `semantic-layer/semantic-cli` 中落地到离线 evidence graph / KG JSON 构建阶段；semantic catalog store、semantic search、question plan、SQL draft validation 和在线问答仍是后续阶段。语义层不替代 relation-detector 的事实判断，也不自动执行 SQL。LLM 只能基于 evidence 做语义解释、同义词扩展和问题规划，不能创造数据库事实。

工具需要综合分析以下数据来源：

- 数据库元数据：主键、外键、唯一约束、索引、列定义。
- DDL：`CREATE TABLE`、`ALTER TABLE`、`CREATE INDEX` 等结构 SQL。
- 数据库对象定义：存储过程、函数、视图、触发器。
- 原生日志和 SQL 文本：MySQL general/slow log、PostgreSQL statement log、清洗后的纯 SQL 文本。
- 可选数据画像：列定义相似度、值域包含率、重合率、唯一性、空值比例、基数关系等。

输出结果以表+列级关系为主，例如：

```text
orders.user_id -> users.id
```

当无法可靠确定列时，退化为表级关系，例如：

```text
orders -> users
```

## 2. 技术选型

- Java 版本：Java 17。
- 构建工具：Maven。
- CLI 框架：当前为轻量手写 CLI；后续可替换为 picocli。
- 配置文件：`SimpleYamlConfigLoader` 保留历史类名，内部使用 Jackson `YAMLMapper`，并保持当前 YAML 配置语义。
- JSON 序列化：`JsonResultWriter` 使用 Jackson `ObjectMapper`，必须保持输出 schema 兼容。
- SQL/DDL 解析：统一通过 `parser.mode: auto|full-grammar|token-event` 选择。`token-event` 是无版本信息或 profile 不支持时的兜底链路；`full-grammar` 使用 MySQL/PostgreSQL/Oracle/SQL Server adaptor 注册的大版本 grammar profile。ANTLR 是底层 lexer/parser 技术，不是业务 parser 模式名。
- 插件发现：Java SPI / `ServiceLoader`。
- 测试：
  - JUnit 5。
  - correctness fixture golden、CLI E2E golden、versioned full-grammar golden、confidence/merger/lineage/DDL 语义单测。
  - AssertJ / Testcontainers 是后续增强方向，用于更强断言和真实数据库集成测试。

上层语义层建议使用 PostgreSQL + JSONB + pgvector 保存 SemanticTable、SemanticColumn、SemanticEntity、SemanticMetric、SemanticJoinPath、EvidenceRef、Lexicon、Embedding 和 QuestionTrace。第一版可以先使用 JSON 文件落地 semantic catalog，再逐步迁移到数据库存储。

Java SPI 是 Java 标准库自带的插件发现机制。核心模块只定义接口，例如 `DatabaseAdaptor`；MySQL、PostgreSQL、Oracle 和未来的 SQL Server adaptor 各自实现接口，并在 jar 包的 `META-INF/services/...` 文件里声明实现类。CLI 启动时通过 `ServiceLoader` 自动发现 adaptor。这样新增数据库时可以增加一个 adaptor jar，而不必修改 core 代码。

## 3. Maven 模块结构

仓库采用一个根 Maven reactor，但代码按产品边界收口为两个同级目录：

```text
repo-root/
  pom.xml
  relation-detector/
    contracts/
    core/
    cli/
    adaptor-mysql/
    adaptor-postgres/
    adaptor-oracle/
    adaptor-sqlserver/
    sample-data/
    test-fixtures/
    scripts/
  semantic-layer/
    semantic-core/
    semantic-cli/
```

模块职责：

- `relation-detector/contracts`
  - 定义稳定的数据库 adaptor API。
  - 第三方数据库 adaptor 只依赖该模块即可开发。

- `relation-detector/core`
  - 关系候选归并。
  - 证据合并。
  - 最终置信度计算。
  - 输出模型。
  - parser 选择、token-event 事件模型、full-grammar profile registry、relationship/Data Lineage/DDL semantic extractor。

- `relation-detector/cli`
  - 当前轻量手写命令行入口；后续可替换为 picocli。
  - Jackson YAML 配置加载，类名仍为 `SimpleYamlConfigLoader`。
  - adaptor 加载。
  - JSON/table 输出。

- `relation-detector/adaptor-mysql`
  - MySQL 元数据采集。
  - MySQL 对象定义采集。
  - MySQL general/slow log 解析。
  - MySQL 方言规则。
  - MySQL token-event parser。
  - MySQL 8.0 full-grammar module。

- `relation-detector/adaptor-postgres`
  - PostgreSQL 元数据采集。
  - PostgreSQL 对象定义采集。
  - PostgreSQL statement log 解析。
  - PostgreSQL 方言规则。
  - PostgreSQL token-event parser。
  - PostgreSQL 16/17/18 full-grammar modules，公共实现位于 adaptor 内 `fullgrammar/common`。

- `relation-detector/adaptor-oracle`
  - Oracle 初始 adaptor。
  - Oracle token-event fallback，使用 `grammar/oracle-token-event` 中的 `OracleRelationSql.g4` typed grammar。
  - Oracle 12c/19c/21c/26ai `INCOMPLETE_VERSIONED` full-grammar profile，当前覆盖对应版本 sample-data golden、profile smoke 和首批 version-only golden，并使用各自 generated parser。
  - 当前 metadata/object collector 保守返回空；更广泛的 Oracle 官方语法覆盖和真实 Oracle runtime smoke 属于后续补强。

- `relation-detector/adaptor-sqlserver`
  - SQL Server adaptor。
  - SQL Server token-event fallback，使用 `grammar/sqlserver-token-event` 中的 compact `SqlServerRelationSql.g4` typed grammar。
  - SQL Server 2016/2017/2019/2022/2025 full-grammar profile，当前覆盖对应版本 sample-data golden，并使用各自 generated parser。
  - 当前 sample-data 使用 SQL Server 2016-compatible 保守 T-SQL 子集；2017 `STRING_AGG`、2022 `DATETRUNC` / `GENERATE_SERIES`、2025 `VECTOR(...)` 已作为 version-only fixture 和 grammar-level boundary 验收。更多 Microsoft 官方逐版本 T-SQL family 和真实 SQL Server runtime smoke 属于后续补强。

- `relation-detector/test-fixtures`
  - 样例 schema。
  - 样例 DDL。
  - 样例 SQL 日志。
  - 集成测试数据。

- `semantic-layer/semantic-core`
  - 离线读取 relation-detector JSON，构建 evidence graph 与可审计 KG JSON。
  - 只依赖 `relation-detector/contracts` 与 Jackson，不依赖 `core`、`cli` 或任何 adaptor。

- `semantic-layer/semantic-cli`
  - 独立 `semantic build --input <scan-result.json> --output <dir>` 入口。
  - 消费已生成的 scan result，不触发 relation-detector parser/adaptor。

## 3.1 与语义层的边界

relation-detector 输出事实，语义层消费事实。两者边界如下：

| 层级 | 负责内容 | 不负责内容 |
| --- | --- | --- |
| relation-detector | metadata、DDL、SQL/object 解析、relationship、Data Lineage、confidence、evidence、warning | 业务实体命名、同义词扩展、指标口径审核、自然语言问答 |
| Semantic Evidence Builder | 把 relationship / lineage / metadata / SQL source / comment 组织成 evidence graph | 发明新的物理关系或字段 |
| LLM Semantic Enricher | 生成语义描述、实体候选、指标候选、同义词候选、join path 解释 | 直接创造数据库事实或把未审核指标当成正式口径 |
| Query Planner / SQL Draft | 基于 semantic catalog 选择表字段、join path、指标并生成 SQL draft | 绕过 SQL Validator 执行 SQL |

这个边界保证系统可以回答“应该用哪些表字段以及为什么”，并在证据不足时反问，而不是直接让大模型猜数据库结构。

## 4. 核心领域模型

### 4.0 Parser 模式与版本化 grammar

当前 parser 用户可见模式只有三种：

```text
auto | full-grammar | token-event
```

- `token-event`：宽松兼容链路。无方言时使用 `grammar/common-token-event`；MySQL/PostgreSQL/Oracle/SQL Server 使用 `grammar/*-token-event` 中各自的 typed structural grammar。adaptor 只持有 visitor 和装配。它适合作为未知版本、无 profile、unsupported version 或 full-grammar failure 的 fallback，最终 relationship / lineage / DDL 语义仍由 Java semantic extractor 处理。
- `full-grammar`：严格版本链路。它由数据库 adaptor 注册 `FullGrammarDialectModule`，使用对应大版本 vendored grammar 和 typed parse-tree visitor 生成同一组 `StructuredSqlEvent` / DDL events。
- `auto`：默认模式。能根据 `parser.grammarProfile`、`parser.databaseVersion` 或 JDBC metadata 选中 full-grammar profile 时优先使用 full-grammar，否则使用 token-event。

当前内置 full-grammar profile：

| 数据库 | Profile | 归属 | correctness golden |
| --- | --- | --- | --- |
| MySQL | `mysql/5.7` | `relation-detector/adaptor-mysql/fullgrammar/v5_7` | `relation-detector/test-fixtures/correctness/mysql/v5_7` |
| MySQL | `mysql/8.0` | `relation-detector/adaptor-mysql/fullgrammar/v8_0` | `relation-detector/test-fixtures/correctness/mysql/v8_0` |
| PostgreSQL | `postgresql/16` | `relation-detector/adaptor-postgres/fullgrammar/v16` | `relation-detector/test-fixtures/correctness/postgres/v16` |
| PostgreSQL | `postgresql/17` | `relation-detector/adaptor-postgres/fullgrammar/v17` | `relation-detector/test-fixtures/correctness/postgres/v17` |
| PostgreSQL | `postgresql/18` | `relation-detector/adaptor-postgres/fullgrammar/v18` | `relation-detector/test-fixtures/correctness/postgres/v18` |
| Oracle | `oracle/12c` | `relation-detector/adaptor-oracle/fullgrammar/v12c` | `relation-detector/test-fixtures/correctness/oracle/v12c` |
| Oracle | `oracle/19c` | `relation-detector/adaptor-oracle/fullgrammar/v19c` | `relation-detector/test-fixtures/correctness/oracle/v19c` |
| Oracle | `oracle/21c` | `relation-detector/adaptor-oracle/fullgrammar/v21c` | `relation-detector/test-fixtures/correctness/oracle/v21c` |
| Oracle | `oracle/26ai` | `relation-detector/adaptor-oracle/fullgrammar/v26ai` | `relation-detector/test-fixtures/correctness/oracle/v26ai` |
| SQL Server | `sqlserver/2016` | `relation-detector/adaptor-sqlserver/fullgrammar/v2016` | `relation-detector/test-fixtures/correctness/sqlserver/v2016` |
| SQL Server | `sqlserver/2017` | `relation-detector/adaptor-sqlserver/fullgrammar/v2017` | `relation-detector/test-fixtures/correctness/sqlserver/v2017` |
| SQL Server | `sqlserver/2019` | `relation-detector/adaptor-sqlserver/fullgrammar/v2019` | `relation-detector/test-fixtures/correctness/sqlserver/v2019` |
| SQL Server | `sqlserver/2022` | `relation-detector/adaptor-sqlserver/fullgrammar/v2022` | `relation-detector/test-fixtures/correctness/sqlserver/v2022` |
| SQL Server | `sqlserver/2025` | `relation-detector/adaptor-sqlserver/fullgrammar/v2025` | `relation-detector/test-fixtures/correctness/sqlserver/v2025` |

root `relation-detector/test-fixtures/correctness/mysql`、root `relation-detector/test-fixtures/correctness/postgres`、root `relation-detector/test-fixtures/correctness/oracle` 与 root `relation-detector/test-fixtures/correctness/sqlserver` 是 token-event baseline，不代表严格数据库版本目录。严格 full-grammar 版本证明分别位于 `mysql/v5_7`、`mysql/v8_0`、`postgres/v16`、`postgres/v17`、`postgres/v18`、`oracle/v12c`、`oracle/v19c`、`oracle/v21c`、`oracle/v26ai`、`sqlserver/v2016`、`sqlserver/v2017`、`sqlserver/v2019`、`sqlserver/v2022`、`sqlserver/v2025`。Oracle 当前 versioned golden 是 `INCOMPLETE_VERSIONED` generated parser golden；SQL Server 已有首批官方版本边界，更多 T-SQL family 仍是后续工作。

### 4.1 表和列

`TableId`：

- database / catalog。
- schema。
- tableName。
- normalizedName。

`ColumnRef`：

- `TableId table`。
- columnName。
- normalizedName。
- dataType。
- nullable。
- length / precision / scale。

### 4.2 关系

`RelationshipCandidate`：

- source table。
- source column，可为空。
- target table。
- target column，可为空。
- relationType。
- relationSubType。
- confidence。
- evidence list。
- warnings。

### 4.3 关系类型

`relationType` 是大类：

- `FK_LIKE`
  - 表示像外键一样的引用关系。
  - 不要求数据库显式定义 FK。
  - 例子：`orders.user_id -> users.id`。

- `CO_OCCURRENCE`
  - 表示表共现关系。
  - 两张表经常在同一条 SQL、过程、视图或触发器中一起出现，但没有足够证据证明具体列引用。
  - 例子：`users -> audit_logs`。

`relationSubType` 是细分类型：

- `DECLARED_FK`
  - 来自数据库元数据中的显式外键。
  - 例子：MySQL `information_schema.KEY_COLUMN_USAGE` 中明确记录 `orders.user_id` 引用 `users.id`。

- `DDL_DECLARED_FK`
  - 来自 DDL 文件中的外键定义。

- `INFERRED_JOIN_FK`
  - 来自 JOIN 条件推断。
  - 例子：`JOIN users u ON orders.user_id = u.id`。

- `SUBQUERY_INFERRED_FK`
  - 来自 `IN`、`EXISTS`、相关子查询等模式推断。
  - 例子：`orders.user_id IN (SELECT users.id FROM users)`。

- `PROFILE_SUPPORTED_FK`
  - 来自数据画像支持。
  - 例子：抽样发现 `orders.user_id` 的值 99.5% 都存在于 `users.id`。

- `NAMING_SUPPORTED_FK`
  - 来自命名规则和其他证据共同支持。
  - 例子：`user_id` 与 `users.id` 命名匹配，且源列有索引、目标列唯一。

- `TABLE_CO_OCCURRENCE`
  - 仅能证明两个表共现，不能证明具体引用列。

注意：`FK_LIKE` 不抹平证据差异。显式 FK、DDL FK、JOIN 推断、命名推断、数据画像推断都会保留不同的 evidence 和不同置信度。

## 5. 数据来源与证据提取

### 5.1 数据库元数据

数据库元数据指数据库系统表或信息视图中记录的结构信息，例如：

- 表。
- 列。
- 主键。
- 外键。
- 唯一约束。
- 普通索引。
- 列类型。
- 是否 nullable。

MySQL 主要读取：

- `information_schema.TABLES`
- `information_schema.COLUMNS`
- `information_schema.TABLE_CONSTRAINTS`
- `information_schema.KEY_COLUMN_USAGE`
- `information_schema.STATISTICS`

PostgreSQL 主要读取：

- `information_schema.tables`
- `information_schema.columns`
- `information_schema.table_constraints`
- `information_schema.key_column_usage`
- `information_schema.constraint_column_usage`
- `pg_catalog.pg_class`
- `pg_catalog.pg_attribute`
- `pg_catalog.pg_index`
- `pg_catalog.pg_constraint`

例子：

```sql
ALTER TABLE orders
ADD CONSTRAINT fk_orders_user
FOREIGN KEY (user_id) REFERENCES users(id);
```

输出关系：

```text
orders.user_id -> users.id
relationType: FK_LIKE
relationSubType: DECLARED_FK
evidence: METADATA_FOREIGN_KEY
confidence: 0.98
```

### 5.2 DDL

DDL 指结构定义 SQL，例如：

- `CREATE TABLE`
- `ALTER TABLE`
- `CREATE INDEX`
- `CREATE UNIQUE INDEX`

DDL 可以作为 JDBC 的补充，也可以在无法连接数据库时替代 JDBC 进行离线分析。

例子：

```sql
CREATE TABLE orders (
  id BIGINT PRIMARY KEY,
  user_id BIGINT,
  CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users(id)
);
```

输出关系：

```text
orders.user_id -> users.id
relationType: FK_LIKE
relationSubType: DDL_DECLARED_FK
evidence: DDL_FOREIGN_KEY
confidence: 0.90
```

如果 DDL 中没有外键，但有索引：

```sql
CREATE INDEX idx_orders_user_id ON orders(user_id);
```

单独的索引不直接生成强关系，但可以作为辅助证据，增强 JOIN、命名或数据画像推断出的关系。

### 5.3 过程、视图、触发器定义

这些对象内部通常包含稳定的业务 SQL，能提供高价值关系证据。

对象来源：

- 从数据库自动读取。
- 从本地 SQL 文件读取。

MySQL 对象：

- procedure。
- function。
- view。
- trigger。

PostgreSQL 对象：

- function。
- procedure。
- view。
- trigger function。

例子：

```sql
CREATE VIEW user_orders AS
SELECT u.id, o.id
FROM users u
JOIN orders o ON o.user_id = u.id;
```

输出关系：

```text
orders.user_id -> users.id
relationType: FK_LIKE
relationSubType: INFERRED_JOIN_FK
evidence: VIEW_JOIN
confidence: 0.72
```

触发器例子：

```sql
CREATE TRIGGER trg_order_audit
AFTER INSERT ON orders
FOR EACH ROW
INSERT INTO audit_logs(order_id, action)
VALUES (NEW.id, 'CREATE');
```

输出关系：

```text
audit_logs.order_id -> orders.id
relationType: FK_LIKE
relationSubType: INFERRED_JOIN_FK
evidence: TRIGGER_REFERENCE
confidence: 0.65
```

### 5.4 原生日志和 SQL 文本

支持的日志来源：

- MySQL general log。
- MySQL slow log。
- PostgreSQL statement log。
- 清洗后的纯 SQL 文本。

例子：

```sql
SELECT *
FROM orders o
JOIN users u ON o.user_id = u.id;
```

输出关系：

```text
orders.user_id -> users.id
relationType: FK_LIKE
relationSubType: INFERRED_JOIN_FK
evidence: SQL_LOG_JOIN
confidence: 0.55
```

子查询例子：

```sql
SELECT *
FROM orders o
WHERE o.user_id IN (
  SELECT u.id FROM users u
);
```

输出关系：

```text
orders.user_id -> users.id
relationType: FK_LIKE
relationSubType: SUBQUERY_INFERRED_FK
evidence: SQL_LOG_SUBQUERY_IN
confidence: 0.58
```

兼容保留的表级共现例子：

```sql
SELECT *
FROM users u, audit_logs l
WHERE l.action = 'LOGIN';
```

当前生产 typed parser 默认不因“同一 SQL 中出现多张表但没有列级谓词”主动生成正式 relationship evidence，避免报表、审计和临时分析 SQL 造成 false positive。`SQL_LOG_TABLE_CO_OCCURRENCE` 仅作为 `RESERVED_COMPATIBILITY / NOT_PRODUCED` 证据保留，可用于历史结果、外部 adaptor 导入或未来显式 opt-in 审计模式。

这里的替代关系需要区分两种情况：

- 列级谓词共现不是被丢弃，而是被更具体的 predicate evidence 替代：`JOIN` / comma join / `JOIN USING` 使用 `SQL_LOG_JOIN`，correlated `EXISTS` 使用 `SQL_LOG_EXISTS`，scalar / tuple `IN (SELECT ...)` 使用 `SQL_LOG_SUBQUERY_IN`。这些 evidence 覆盖了原来泛化 `SQL_LOG_COLUMN_CO_OCCURRENCE` 想表达的“列级关联出现过”，同时保留 SQL 语法来源。
- 纯表级共现没有等价替代。没有列级谓词时，当前生产 parser 不生成正式 relationship；只有历史结果、外部导入或未来显式 opt-in 审计模式才可能使用 `SQL_LOG_TABLE_CO_OCCURRENCE`。

兼容导入时的弱表级关系形态如下：

```text
users -> audit_logs
relationType: CO_OCCURRENCE
relationSubType: TABLE_CO_OCCURRENCE
evidence: SQL_LOG_TABLE_CO_OCCURRENCE
confidence: 0.25
```

### 5.5 可选数据画像

数据画像默认关闭，需要用户显式启用。原因：

- 会读取业务数据。
- 可能影响生产库性能。
- 可能涉及敏感信息。

启用后采用抽样、上限和超时控制，不做默认全量扫描。

画像证据包括：

- 列定义相似：
  - 类型一致。
  - 长度一致。
  - precision/scale 一致。
  - 字符集/排序规则一致。
  - nullable 形态合理。

- 目标列唯一：
  - 目标列是 PK 或 unique。
  - 源列有普通索引。
  - 符合多对一引用特征。

- 值域包含：
  - 源列 A 的非空取值大部分或全部存在于目标列 B。
  - 例子：`orders.user_id` 的值全部出现在 `users.id` 中。

- 重合率：
  - 抽样计算 `source distinct values` 中能在目标列命中的比例。
  - 高于阈值则加分，低于阈值则降低置信度或记录反证。

- 基数形态：
  - 源列重复较多。
  - 目标列唯一或接近唯一。
  - 体现多对一引用关系。

- 空值比例：
  - 源列允许为空且有一定空值，说明可能是可选关系。
  - 不直接否定关系，但进入解释信息。

例子：

```text
orders.user_id -> users.id
已有证据:
- SQL_LOG_JOIN: 0.55
- NAMING_MATCH: 0.20
- SOURCE_INDEX: 0.10

启用画像后新增:
- DATA_TYPE_COMPATIBLE
- TARGET_UNIQUE
- VALUE_CONTAINMENT_99_5_PERCENT

最终 confidence 从 0.70 左右提升到 0.90 左右。
```

## 6. 无法确定列时退化为表级关系

退化为表级关系的含义是：工具发现两个表之间存在潜在业务关联，但无法可靠判断是哪两个列产生关联，因此不强行编造列级关系。

例子 1：仅表共现。

```sql
SELECT *
FROM orders, users
WHERE orders.status = 'PAID';
```

这里 `orders` 和 `users` 出现在同一条 SQL 中，但没有明确连接条件。输出：

```text
orders -> users
relationType: CO_OCCURRENCE
relationSubType: TABLE_CO_OCCURRENCE
confidence: 0.25
```

例子 2：复杂条件无法稳定归因。

```sql
SELECT *
FROM orders o
JOIN users u
  ON o.created_by = u.id OR o.updated_by = u.id;
```

如果解析器无法确定主关系，可以输出表级弱关系，同时保留 warning：

```text
orders -> users
relationType: CO_OCCURRENCE
relationSubType: TABLE_CO_OCCURRENCE
warning: ambiguous join condition
```

例子 3：动态 SQL。

```sql
SET @sql = CONCAT('SELECT * FROM ', table_name);
PREPARE stmt FROM @sql;
```

无法静态确定具体表和列时，不生成列级关系；如果能识别相关表，只生成低置信度表级关系。

## 7. 置信度模型

置信度由 core 统一计算，adaptor 可以提供数据库特定的 evidence 权重修正。

### 7.1 基础证据分

建议初始基础分：

- `METADATA_FOREIGN_KEY`: 0.98
- `DDL_FOREIGN_KEY`: 0.90
- `VIEW_JOIN`: 0.72
- `PROCEDURE_JOIN`: 0.70
- `TRIGGER_REFERENCE`: 0.65
- `SQL_LOG_JOIN`: 0.55
- `SQL_LOG_SUBQUERY_IN`: 0.58
- `SQL_LOG_EXISTS`: 0.58
- `SQL_LOG_COLUMN_CO_OCCURRENCE`: 0.40
- `SQL_LOG_TABLE_CO_OCCURRENCE`: 0.25
- `NAMING_MATCH`: 0.20
- `SOURCE_INDEX`: 0.10
- `TARGET_UNIQUE`: 0.18
- `COLUMN_TYPE_COMPATIBLE`: 0.08
- `VALUE_CONTAINMENT_HIGH`: 0.30
- `VALUE_OVERLAP_HIGH`: 0.20
- `NEGATIVE_VALUE_MISMATCH`: -0.30

另外，`REPEATED_OBSERVATION` 是 core merge 阶段派生出来的重复观测增益，分值范围为 `0.00-0.10`，不是 parser、metadata collector 或 profiler 直接产出的原始证据源。

详细解释见 [Phase 2：核心模型和评分详细设计](../design/relation-detector/phase-02-core-model-scoring.md) 的“置信度计算”章节。该章节逐项说明了每个 EvidenceType 为什么取该分值，并给出 metadata、DDL、日志 JOIN、存储过程、`IN` 子查询、表共现、重复观测和负向数据画像的完整 SQL 算例。

分数设计的核心原则：

- 数据库当前 metadata 最强，因为它来自 live catalog。
- DDL 文件次之，因为它可能不是当前库真实状态。
- view/procedure/trigger 这类数据库对象定义强于普通日志，因为它们更稳定，但仍可能服务报表、审计、同步或批处理。
- SQL 日志证明真实执行过，但单条 SQL 可能是临时分析或 ETL，所以保持中等置信度。
- 命名、索引、唯一性、类型兼容是辅助证据，不应单独输出高置信 FK。
- 数据画像能增强或降低置信度，但受抽样、软删除、租户过滤、历史脏数据影响，默认低于显式结构证据。

### 7.2 合并公式

正向证据使用：

```text
confidence = 1 - product(1 - evidenceScore)
```

显式数据库 FK 最低不低于 0.95，最终最高封顶 0.99。

负向证据用于降低最终分数，例如：

- 源列大量值不存在于目标列。
- 类型明显不兼容。
- 目标列非唯一且基数形态不符合引用关系。

### 7.3 评分解释

每条结果必须保留 evidence 列表，不能只输出最终分数。

例子：

```json
{
  "source": "orders.user_id",
  "target": "users.id",
  "relationType": "FK_LIKE",
  "relationSubType": "PROFILE_SUPPORTED_FK",
  "confidence": 0.91,
  "evidence": [
    {
      "type": "SQL_LOG_JOIN",
      "score": 0.55,
      "source": "mysql-slow-log",
      "detail": "JOIN orders.user_id = users.id appeared 143 times"
    },
    {
      "type": "TARGET_UNIQUE",
      "score": 0.18,
      "source": "metadata",
      "detail": "users.id is primary key"
    },
    {
      "type": "VALUE_CONTAINMENT_HIGH",
      "score": 0.30,
      "source": "data-profile",
      "detail": "99.5% sampled orders.user_id values exist in users.id"
    }
  ]
}
```

## 8. Adaptor API 设计

`DatabaseAdaptor` 应提供：

- 数据库类型标识。
- 连接能力声明。
- 标识符规范化规则。
- 元数据采集器。
- DDL structured parser。
- 数据库对象定义采集器。
- 原生日志解析器。
- SQL structured parser。
- full-grammar dialect module，可按数据库大版本独立注册。
- 数据画像能力。
- evidence 权重修正能力。

核心原则：

- adaptor API 允许全链路扩展。
- 默认流程由 core 编排。
- core 负责统一输出模型、候选合并和最终评分。
- adaptor 可以覆盖或增强采集、解析、证据生成、权重修正。

后续新增或补强数据库时，实现独立 adaptor 模块；Oracle 和 SQL Server 已有初始模块：

```text
relation-detector/adaptor-oracle/
relation-detector/adaptor-sqlserver/
```

并通过 Java SPI 注册。

## 9. CLI 设计

主命令：

```bash
relation-detector scan --config config.yml --format table
```

JSON 输出：

```bash
relation-detector scan --config config.yml --format json --output result.json
```

加载外部 adaptor：

```bash
relation-detector scan --config config.yml --plugin-dir plugins/
```

建议配置：

```yaml
database:
  type: mysql
  jdbcUrl: jdbc:mysql://localhost:3306/shop
  username: readonly
  password: ${DB_PASSWORD}
  schema: shop

filters:
  includeTables:
    - orders
    - users
    - audit_logs
  excludeTables:
    - tmp_*
    - archive_*

sources:
  metadata:
    enabled: true
  ddl:
    enabled: true
    files:
      - schema.sql
  objects:
    enabled: true
    fromDatabase: true
    files:
      - routines.sql
  logs:
    enabled: true
    filterSystemQueries: true
    systemSchemas:
      - information_schema
      - performance_schema
      - mysql
      - sys
    metadataQueryMarkers:
      - ApplicationName=DBeaver
      - DatabaseMetaData
    files:
      - mysql-general.log
      - app-sql.sql
  dataProfile:
    enabled: false
    sampleRows: 10000
    timeoutSeconds: 30

parser:
  mode: auto              # auto | full-grammar | token-event
  grammarProfile: ""      # 可选，例如 postgresql/16、mysql/5.7 或 mysql/8.0
  databaseVersion: ""     # 可选，例如 16.5；缺失时可由 JDBC metadata 补充

output:
  format: json
  minConfidence: 0.30
  includeEvidence: true
  includeWarnings: true
  includeObservationCounts: true
```

## 10. JSON 输出设计

顶层结构：

```json
{
  "database": {
    "type": "mysql",
    "schema": "shop"
  },
  "generatedAt": "2026-06-14T00:00:00Z",
  "summary": {
    "directRelationshipCount": 0,
    "derivedRelationshipCount": 0,
    "totalRelationshipCount": 0,
    "directDataLineageCount": 0,
    "derivedDataLineageCount": 0,
    "totalDataLineageCount": 0,
    "directNamingEvidenceCount": 0,
    "derivedNamingEvidenceCount": 0,
    "totalNamingEvidenceCount": 0,
    "directRelationshipObservationCount": 0,
    "derivedRelationshipObservationCount": 0,
    "totalRelationshipObservationCount": 0,
    "directDataLineageObservationCount": 0,
    "derivedDataLineageObservationCount": 0,
    "totalDataLineageObservationCount": 0,
    "directNamingEvidenceObservationCount": 0,
    "derivedNamingEvidenceObservationCount": 0,
    "totalNamingEvidenceObservationCount": 0,
    "warningCount": 0,
    "sources": []
  },
  "relationships": [],
  "dataLineages": [],
  "namingEvidence": [],
  "warnings": []
}
```

关系结构：

```json
{
  "source": {
    "table": "orders",
    "column": "user_id"
  },
  "target": {
    "table": "users",
    "column": "id"
  },
  "relationType": "FK_LIKE",
  "relationSubType": "DECLARED_FK",
  "confidence": 0.98,
  "rawEvidence": [],
  "evidence": [
    {
      "type": "METADATA_FOREIGN_KEY",
      "score": 0.98,
      "source": "information_schema",
      "detail": "fk_orders_user"
    }
  ]
}
```

`namingEvidence` 是独立命名证据池；relationship 中的 `NAMING_MATCH` evidence 只通过 `evidenceRef` 引用该池，不重复完整 raw observations。

表级关系中 column 允许为空：

```json
{
  "source": {
    "table": "orders",
    "column": null
  },
  "target": {
    "table": "users",
    "column": null
  },
  "relationType": "CO_OCCURRENCE",
  "relationSubType": "TABLE_CO_OCCURRENCE",
  "confidence": 0.25,
  "evidence": []
}
```

Data Lineage 是独立顶层数组，不混入 relationship，也不改变 relationship confidence：

```json
{
  "sources": [
    { "table": "orders", "column": "pay_amount" }
  ],
  "target": {
    "table": "users",
    "column": "total_spent"
  },
  "flowKind": "VALUE",
  "transformType": "AGGREGATE",
  "confidence": 0.80,
  "evidence": []
}
```

## 11. 实施阶段

### Phase 1：工程骨架

- 创建 Maven 多模块工程。
- 配置 Java 17。
- 添加基础依赖。
- 建立 `contracts`、`core`、`cli`。
- 实现基础 CLI 命令和 YAML 配置加载。
- 建立 JSON/table 输出框架。

验收：

- `relation-detector scan --help` 可运行。
- 可读取 YAML 配置。
- 可输出空结果 JSON。

### Phase 2：核心模型和评分

- 实现 `TableId`、`ColumnRef`、`Evidence`、`RelationshipCandidate`。
- 实现 `relationType` 和 `relationSubType`。
- 实现证据合并。
- 实现置信度公式。
- 实现 warning 模型。

验收：

- 单元测试覆盖评分合并。
- 同一关系多 evidence 能正确归并。
- 输出保留 evidence 解释。

### Phase 3：Adaptor API 和 SPI

- 定义 `DatabaseAdaptor`。
- 定义采集器、解析器、画像接口。
- 实现 Java SPI 加载。
- 实现 `--plugin-dir` 外部 jar 加载。
- 提供 mock adaptor 测试。

验收：

- 内置 adaptor 可发现。
- 外部 mock adaptor jar 可加载。
- 不改 `relation-detector/core` / `relation-detector/cli` 即可接入新 adaptor。

### Phase 4：MySQL adaptor

- 实现 MySQL 元数据读取。
- 实现 MySQL DDL 解析补丁。
- 实现 MySQL procedure/function/view/trigger 定义读取。
- 实现 MySQL general/slow log SQL 提取。
- 实现 MySQL 标识符规范化。
- 实现 MySQL 数据画像查询。

验收：

- Testcontainers MySQL 集成测试通过。
- 样例 schema 可输出显式 FK、JOIN 推断关系、共现关系。

### Phase 5：PostgreSQL adaptor

- 实现 PostgreSQL 元数据读取。
- 实现 PostgreSQL DDL 解析补丁。
- 实现 PostgreSQL function/procedure/view/trigger 定义读取。
- 实现 PostgreSQL statement log SQL 提取。
- 实现 PostgreSQL 标识符规范化。
- 实现 PostgreSQL 数据画像查询。

验收：

- Testcontainers PostgreSQL 集成测试通过。
- 样例 schema 输出与 MySQL 同等能力的关系结果。

### Phase 6：SQL/DDL/对象解析增强

- 建立 token-event SQL/DDL structured parser。
- 建立版本化 full-grammar profile 与 adaptor module 注入。
- 支持 JOIN 条件、表别名、schema 限定名、CTE、derived table、`EXISTS`、scalar/tuple `IN`、DML rowset、对象 SQL。
- 支持 Data Lineage v1：数据库内部字段到字段的 UPDATE / INSERT SELECT / MERGE 写入血缘。
- 支持 DDL FK / index 结构事件与 DDL relationship extraction。
- 解析失败或动态 SQL 时记录 warning，单条语句失败不影响整体扫描。

验收：

- fixture SQL/DDL 覆盖常见 JOIN、子查询、视图、触发器、DML 写入、DDL index/FK。
- 单条 SQL 失败不影响整体扫描。
- versioned full-grammar golden 直接证明对应 profile 的 SQL/DDL 行为；运行时选中 profile 后 full-grammar 是 primary parser，token-event 只做 fallback。

### Phase 7：可选数据画像

- 实现配置开关。
- 实现采样行数上限。
- 实现查询超时。
- 实现列定义相似度。
- 实现值域包含率。
- 实现重合率。
- 实现唯一性和基数判断。
- 实现负向证据。

验收：

- 默认不读取业务数据。
- 开启画像后能提升或降低对应关系置信度。
- 大表查询受 sampleRows 和 timeout 控制。

### Phase 8：输出和用户体验

- 完善 JSON 输出。
- 完善 table 输出。
- 增加 `minConfidence` 过滤。
- 增加 warning 摘要。
- 增加错误码。
- 增加 README 和示例配置。

验收：

- 用户可以用样例配置完整跑通。
- JSON 可被稳定反序列化。
- table 输出适合终端阅读。

## 12. 测试计划

### 单元测试

- adaptor SPI 加载。
- MySQL/PostgreSQL 标识符规范化。
- DDL 外键解析。
- DDL 索引解析。
- JOIN 条件解析。
- 表别名解析。
- schema 限定名解析。
- `IN` 子查询解析。
- `EXISTS` 子查询解析。
- 表共现识别。
- 命名弱证据规则。
- 数据画像评分。
- 负向证据降分。
- 置信度合并算法。

### 集成测试

- Testcontainers MySQL。
- Testcontainers PostgreSQL。
- 样例 schema 覆盖：
  - 显式 FK。
  - DDL FK。
  - 唯一索引。
  - 普通索引。
  - 视图 JOIN。
  - 存储过程 JOIN。
  - 触发器引用。
- SQL 日志 JOIN。
- SQL 日志 `IN` 子查询。
- SQL 日志共现。
- UPDATE / INSERT SELECT / MERGE Data Lineage。
- full-grammar profile selection 与 token-event fallback。
- 数据画像值域包含。

### CLI 测试

- `--help`。
- YAML 配置读取。
- JSON 输出。
- table 输出。
- `--plugin-dir`。
- `minConfidence` 过滤。
- 输入文件不存在。
- 数据库连接失败。
- 部分 SQL 解析失败。

## 13. 验收标准

v1 完成时应满足：

- MySQL 和 PostgreSQL adaptor 可运行。
- 可以通过 JDBC 读取元数据。
- 可以读取 DDL 文件替代 JDBC 结构信息。
- 可以读取过程、视图、触发器定义。
- 可以解析 MySQL/PostgreSQL 原生日志和纯 SQL 文本；Oracle 当前支持 file/object/sample SQL 的 token-event correctness，并通过各版本 sample-data full-grammar golden 验证 generated parser 链路。
- 可以输出列级 FK-like 关系。
- 可以输出数据库内部字段 Data Lineage。
- 无法确定列时可以输出低置信度表级共现关系。
- 可以通过可选数据画像增强或降低置信度。
- 每条关系都有 evidence 解释。
- JSON/table 输出稳定。
- 新增 mock adaptor 不需要修改 `relation-detector/core` / `relation-detector/cli`。

## 14. 暂不纳入 v1 的范围

- SQL Server 完整实现。
- Oracle Oracle official complete full-grammar、真实 Oracle runtime smoke 和元数据/对象采集完整实现。
- 图形化界面。
- Graphviz/Mermaid 图输出。
- 分布式日志采集。
- 云数据库审计日志直接接入。
- 默认全量扫描业务数据。
- 机器学习模型评分。

## 15. 关键假设

- v1 优先保证 MySQL 和 PostgreSQL 结果准确、可测试、可解释。
- 生产环境默认只读元数据权限。
- 数据画像默认关闭，用户显式开启后才读取业务数据。
- DDL、对象定义和 SQL 日志文件可以作为数据库连接不足时的补充或替代输入。
- 显式 FK、DDL FK、JOIN 推断、命名推断、数据画像推断都属于 FK-like 大类，但必须通过 relationSubType、evidence 和 confidence 区分强弱。
- core 负责统一合并和最终评分，adaptor 负责数据库差异和 evidence 修正。
