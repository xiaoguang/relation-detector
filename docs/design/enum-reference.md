# ENUM 详细说明

本文档定义项目中需要稳定维护的枚举。实现人员应优先以本文档为准创建 Java enum、JSON 字段值和测试 fixture。

## 维护总原则

- JSON 输出中的 enum 值必须稳定，采用全大写下划线格式，例如 `FK_LIKE`。
- 已发布 enum 值不要改名；如果语义变化，应新增 enum 值并保留旧值兼容。
- enum 用于表达离散状态，不要把可变文本、文件名、SQL 片段放进 enum。
- 每个 enum 都应有单元测试覆盖 JSON 序列化和反序列化。
- 文档中标记为“预留”的值可以先不实现完整逻辑，但不要在 v1 输出中伪造结果。

## 1. DatabaseType

表示用户要扫描的数据库类型，来自 YAML 的 `database.type`，用于选择 adaptor。

建议 Java enum：

```java
public enum DatabaseType {
  MYSQL,
  POSTGRESQL,
  SQLSERVER,
  ORACLE
}
```

| 值 | 含义 | v1 状态 | 示例配置 |
| --- | --- | --- | --- |
| `MYSQL` | MySQL 数据库。覆盖 MySQL 5.7/8.0+。 | 完整实现 | `type: mysql` |
| `POSTGRESQL` | PostgreSQL 数据库。覆盖 PostgreSQL 12+。 | 完整实现 | `type: postgresql` |
| `SQLSERVER` | Microsoft SQL Server。 | 预留 | `type: sqlserver` |
| `ORACLE` | Oracle Database。 | 预留 | `type: oracle` |

维护说明：

- 配置中可以允许小写别名，例如 `mysql`，但内部统一转成 `MYSQL`。
- v1 只有 `MYSQL` 和 `POSTGRESQL` 应能实际扫描。
- 用户选择 `SQLSERVER` 或 `ORACLE` 时，如果没有对应 adaptor，应返回“adaptor 未找到”错误，不应偷偷降级到其他数据库。

## 2. OutputFormat

表示 CLI 输出格式，来自命令行 `--format` 或 YAML `output.format`。

```java
public enum OutputFormat {
  JSON,
  TABLE
}
```

| 值 | 含义 | 使用场景 |
| --- | --- | --- |
| `JSON` | 输出结构化 JSON。 | 机器消费、CI 集成、后续生成图。 |
| `TABLE` | 输出终端表格。 | 人工查看、调试、快速确认结果。 |

维护说明：

- `--format` 命令行参数优先级高于 YAML。
- 如果后续增加 `CSV`、`DOT`、`MERMAID`，应作为新增 enum 值，不改变 `JSON` 和 `TABLE` 行为。
- v1 不生成 Graphviz/Mermaid，避免输出范围扩散。

## 3. RelationType

表示最终关系的大类。它回答“这两张表之间是什么性质的关系”。

```java
public enum RelationType {
  FK_LIKE,
  CO_OCCURRENCE
}
```

| 值 | 含义 | 置信度特点 | 例子 |
| --- | --- | --- | --- |
| `FK_LIKE` | 类外键关系。一个表的列很可能引用另一个表的主键或唯一键。 | 可以从中到极高。显式 FK 最高，推断关系较低。 | `orders.user_id -> users.id` |
| `CO_OCCURRENCE` | 表共现关系。两个表经常同时出现在 SQL、视图、过程或触发器中，但没有可靠列级引用证据。 | 通常较低。 | `users -> audit_logs` |

使用规则：

- 有明确列级引用证据时，优先输出 `FK_LIKE`。
- 只有表共同出现、没有明确列关系时，输出 `CO_OCCURRENCE`。
- 方向不可靠时，不要强行输出列级 `FK_LIKE`，应退化为表级 `CO_OCCURRENCE`。

反例：

- `orders.user_id` 命名像外键，但没有 JOIN、索引、唯一性、数据画像等辅助证据时，不应单独输出高置信 `FK_LIKE`。
- 一个复杂报表 SQL 同时读取 10 张表，不能把所有表对都当成强关系，只能按阈值生成低置信共现或跳过。

## 4. RelationSubType

表示关系的主要可信形态。它回答“这条关系主要是根据什么类型的证据成立的”。

```java
public enum RelationSubType {
  DECLARED_FK,
  DDL_DECLARED_FK,
  INFERRED_JOIN_FK,
  SUBQUERY_INFERRED_FK,
  PROFILE_SUPPORTED_FK,
  NAMING_SUPPORTED_FK,
  TABLE_CO_OCCURRENCE
}
```

| 值 | 所属 RelationType | 含义 | 典型 evidence | 例子 |
| --- | --- | --- | --- | --- |
| `DECLARED_FK` | `FK_LIKE` | 数据库元数据中明确存在外键。 | `METADATA_FOREIGN_KEY` | MySQL `information_schema` 显示 `orders.user_id` 引用 `users.id`。 |
| `DDL_DECLARED_FK` | `FK_LIKE` | DDL 文件中明确声明外键。 | `DDL_FOREIGN_KEY` | `FOREIGN KEY (user_id) REFERENCES users(id)`。 |
| `INFERRED_JOIN_FK` | `FK_LIKE` | 从 JOIN 条件推断出的类外键关系。 | `SQL_LOG_JOIN`、`VIEW_JOIN`、`PROCEDURE_JOIN` | `JOIN users u ON o.user_id = u.id`。 |
| `SUBQUERY_INFERRED_FK` | `FK_LIKE` | 从 `IN`、`EXISTS`、相关子查询推断出的类外键关系。 | `SQL_LOG_SUBQUERY_IN`、`SQL_LOG_EXISTS` | `o.user_id IN (SELECT id FROM users)`。 |
| `PROFILE_SUPPORTED_FK` | `FK_LIKE` | 数据画像强支持的推断关系。 | `VALUE_CONTAINMENT_HIGH`、`TARGET_UNIQUE` | 抽样发现 `orders.user_id` 99.5% 都存在于 `users.id`。 |
| `NAMING_SUPPORTED_FK` | `FK_LIKE` | 命名规则与其他弱证据共同支持的关系。 | `NAMING_MATCH`、`SOURCE_INDEX`、`TARGET_UNIQUE` | `user_id` 命中 `users.id`，且目标列唯一。 |
| `TABLE_CO_OCCURRENCE` | `CO_OCCURRENCE` | 只能证明两个表共现，不能证明列级引用。 | `SQL_LOG_TABLE_CO_OCCURRENCE` | `FROM users, audit_logs` 且没有连接条件。 |

多证据时的主导优先级：

1. `DECLARED_FK`
2. `DDL_DECLARED_FK`
3. `PROFILE_SUPPORTED_FK`
4. `INFERRED_JOIN_FK`
5. `SUBQUERY_INFERRED_FK`
6. `NAMING_SUPPORTED_FK`
7. `TABLE_CO_OCCURRENCE`

维护说明：

- `RelationSubType` 不替代 evidence 列表。它只放“主导形态”。
- 一条关系可以有很多 evidence，但只能有一个 `relationSubType`。
- 显式数据库 FK 永远不应被数据画像或日志证据覆盖成其他 subtype。
- `PROFILE_SUPPORTED_FK` 是“画像强支持的推断关系”，不是“画像发现的新关系”。画像默认只对已有候选运行。

## 5. EvidenceType

表示单条证据的类型。它回答“这条证据从哪里来、说明了什么”。

```java
public enum EvidenceType {
  METADATA_FOREIGN_KEY,
  DDL_FOREIGN_KEY,
  VIEW_JOIN,
  PROCEDURE_JOIN,
  TRIGGER_REFERENCE,
  SQL_LOG_JOIN,
  SQL_LOG_SUBQUERY_IN,
  SQL_LOG_EXISTS,
  SQL_LOG_TABLE_CO_OCCURRENCE,
  NAMING_MATCH,
  SOURCE_INDEX,
  TARGET_UNIQUE,
  COLUMN_TYPE_COMPATIBLE,
  VALUE_CONTAINMENT_HIGH,
  VALUE_OVERLAP_HIGH,
  NEGATIVE_VALUE_MISMATCH,
  REPEATED_OBSERVATION
}
```

### 5.1 结构类证据

| 值 | 含义 | 默认分 | 何时产生 |
| --- | --- | ---: | --- |
| `METADATA_FOREIGN_KEY` | 数据库元数据中的显式外键。 | 0.98 | JDBC 读取到 FK 约束。 |
| `DDL_FOREIGN_KEY` | DDL 文件中的外键。 | 0.90 | 解析 `CREATE TABLE` 或 `ALTER TABLE` 得到 FK。 |
| `SOURCE_INDEX` | source 列存在索引。 | 0.10 | 元数据或 DDL 中发现 source 列有普通索引。 |
| `TARGET_UNIQUE` | target 列唯一。 | 0.18 | target 列是 PK、unique constraint 或 unique index。 |
| `COLUMN_TYPE_COMPATIBLE` | 两列定义兼容。 | 0.08 | 类型、长度、精度等兼容。 |

例子：

```text
orders.user_id -> users.id
Evidence:
- TARGET_UNIQUE: users.id is primary key
- SOURCE_INDEX: orders.user_id has index idx_orders_user_id
- COLUMN_TYPE_COMPATIBLE: both columns are BIGINT
```

维护说明：

- `SOURCE_INDEX`、`TARGET_UNIQUE`、`COLUMN_TYPE_COMPATIBLE` 通常是辅助证据，不能单独证明关系。
- `METADATA_FOREIGN_KEY` 是最强证据，最低最终置信度不低于 0.95。

### 5.2 SQL/对象定义证据

| 值 | 含义 | 默认分 | 何时产生 |
| --- | --- | ---: | --- |
| `VIEW_JOIN` | 视图定义中的 JOIN 关系。 | 0.72 | 解析 view SQL。 |
| `PROCEDURE_JOIN` | 存储过程或函数中的 JOIN 关系。 | 0.70 | 解析 procedure/function body。 |
| `TRIGGER_REFERENCE` | 触发器中的表/列引用。 | 0.65 | 解析 trigger body 或 trigger function。 |
| `SQL_LOG_JOIN` | SQL 日志中的 JOIN 关系。 | 0.55 | 解析 native log 或纯 SQL 文件。 |
| `SQL_LOG_SUBQUERY_IN` | SQL 日志中的 `IN` 子查询关系。 | 0.58 | 外层列出现在 `IN (SELECT ...)`。 |
| `SQL_LOG_EXISTS` | SQL 日志中的 `EXISTS` 子查询关系。 | 0.58 | `EXISTS` 内部存在关联条件。 |
| `SQL_LOG_TABLE_CO_OCCURRENCE` | SQL 日志中的表共现。 | 0.25 | 多表同 SQL 出现，但无可靠列关系。 |
| `REPEATED_OBSERVATION` | 同一关系在同一证据类型/来源下重复出现后的派生加分。 | 0.00-0.10 | `RelationshipMerger` 发现同组 evidence 的 `count > 1`。 |

例子：

```sql
SELECT *
FROM orders o
WHERE o.user_id IN (SELECT u.id FROM users u);
```

产生：

```text
EvidenceType: SQL_LOG_SUBQUERY_IN
RelationSubType: SUBQUERY_INFERRED_FK
```

维护说明：

- 同样是 JOIN，来自 view 的 evidence 通常比来自日志的 evidence 更强，因为 view 更稳定。
- 解析失败不要制造 evidence，应记录 `PARSE_WARNING`。
- `REPEATED_OBSERVATION` 不由 SQL parser、DDL parser 或 adaptor 直接产生，只能由 core 归并阶段产生。它必须带 `attributes.count`、`attributes.maxScore`、`attributes.formula`、`attributes.baseEvidenceType`，用于解释递减增益和绝对上限。

### 5.3 命名和数据画像证据

| 值 | 含义 | 默认分 | 何时产生 |
| --- | --- | ---: | --- |
| `NAMING_MATCH` | 命名规则匹配。 | 0.20 | `user_id` 匹配 `users.id` 这类模式。 |
| `VALUE_CONTAINMENT_HIGH` | source 值域高度包含于 target。 | 0.30 | 抽样包含率高于阈值。 |
| `VALUE_OVERLAP_HIGH` | source 与 target 值重合率较高。 | 0.20 | 抽样重合率高于阈值。 |
| `NEGATIVE_VALUE_MISMATCH` | 数据画像显示明显不匹配。 | -0.30 | 大量 source 值不在 target 中，或类型/基数明显不合理。 |

维护说明：

- `NAMING_MATCH` 不能单独生成强关系。
- `VALUE_CONTAINMENT_HIGH` 是强辅助证据，但仍受采样限制，detail 必须写明样本规模。
- `NEGATIVE_VALUE_MISMATCH` 是负向证据，会降低最终分数，而不是删除关系。
- EvidenceType 的默认分值、定分理由、合并公式和完整 SQL 算例，以 [Phase 2：核心模型和评分详细设计](phase-02-core-model-scoring.md) 的“置信度计算”章节为准。维护枚举时必须同步检查该章节，避免 enum 文档和评分模型出现两套解释。

## 6. EvidenceSourceType

表示 evidence 的来源类别。它和 `EvidenceType` 不同：`EvidenceType` 说明“证据是什么”，`EvidenceSourceType` 说明“证据从哪里来”。

建议 Java enum：

```java
public enum EvidenceSourceType {
  METADATA,
  DDL_FILE,
  DATABASE_OBJECT,
  NATIVE_LOG,
  PLAIN_SQL,
  DATA_PROFILE,
  NAMING_HEURISTIC
}
```

| 值 | 含义 | 示例 |
| --- | --- | --- |
| `METADATA` | JDBC 从系统表或 catalog 读取到的信息。 | `information_schema.KEY_COLUMN_USAGE` |
| `DDL_FILE` | 本地 DDL 文件。 | `schema.sql` |
| `DATABASE_OBJECT` | 过程、函数、视图、触发器定义。 | `view user_orders` |
| `NATIVE_LOG` | 数据库原生日志。 | MySQL slow log、PostgreSQL statement log |
| `PLAIN_SQL` | 清洗后的纯 SQL 文本文件。 | `app-sql.sql` |
| `DATA_PROFILE` | 数据画像查询结果。 | `99.5% sampled values matched` |
| `NAMING_HEURISTIC` | 命名规则推断。 | `user_id` -> `users.id` |

维护说明：

- JSON 中 evidence 的 `source` 可以包含更具体的字符串，例如 `mysql-slow-log`；但内部推荐同时保留 `EvidenceSourceType`，便于统计和过滤。
- 不要把文件路径作为 enum 值。

## 7. StatementSourceType

表示一条 SQL 语句来自哪里，用于选择 evidence 类型和解析策略。

```java
public enum StatementSourceType {
  DDL_FILE,
  PROCEDURE,
  FUNCTION,
  VIEW,
  TRIGGER,
  NATIVE_LOG,
  PLAIN_SQL
}
```

| 值 | 含义 | 解析后的常见 evidence |
| --- | --- | --- |
| `DDL_FILE` | DDL 文件中的语句。 | `DDL_FOREIGN_KEY`、`SOURCE_INDEX`、`TARGET_UNIQUE` |
| `PROCEDURE` | 存储过程 body。 | `PROCEDURE_JOIN` |
| `FUNCTION` | 函数 body。 | `PROCEDURE_JOIN` |
| `VIEW` | 视图定义 SQL。 | `VIEW_JOIN` |
| `TRIGGER` | 触发器 body 或 trigger function。 | `TRIGGER_REFERENCE` |
| `NATIVE_LOG` | 数据库原生日志提取出的 SQL。 | `SQL_LOG_JOIN`、`SQL_LOG_EXISTS` |
| `PLAIN_SQL` | 清洗后的纯 SQL 文本。 | `SQL_LOG_JOIN`、`SQL_LOG_TABLE_CO_OCCURRENCE` |

维护说明：

- `FUNCTION` 和 `PROCEDURE` 可共用 `PROCEDURE_JOIN` evidence，因为二者都属于持久化数据库逻辑。
- `VIEW` 的 JOIN 不应降级成普通 SQL 日志 JOIN。
- `TRIGGER` 解析失败时常见，失败应记录 warning，不应中断扫描。

## 8. DatabaseObjectType

表示从数据库或文件读取到的对象定义类型。

```java
public enum DatabaseObjectType {
  PROCEDURE,
  FUNCTION,
  VIEW,
  TRIGGER
}
```

| 值 | MySQL 对应对象 | PostgreSQL 对应对象 | 说明 |
| --- | --- | --- | --- |
| `PROCEDURE` | procedure | procedure | 持久化过程逻辑。 |
| `FUNCTION` | function | function | PostgreSQL 中很多 trigger 逻辑也在 function 中。 |
| `VIEW` | view | view/materialized view 可后续扩展 | 视图 SQL 通常是稳定 JOIN 证据。 |
| `TRIGGER` | trigger | trigger + trigger function | 触发器中的引用可表达写入关系。 |

维护说明：

- PostgreSQL trigger 需要关联 `TRIGGER` 与对应 `FUNCTION`，不要只看 trigger 元数据。
- 如果后续支持 materialized view，可新增 `MATERIALIZED_VIEW`，不要复用 `VIEW` 表达不同刷新语义。

## 9. LogFormatHint

表示日志文件格式提示。用于 `SqlLogExtractor` 判断如何从文件中抽取 SQL。

```java
public enum LogFormatHint {
  AUTO,
  PLAIN_SQL,
  MYSQL_GENERAL_LOG,
  MYSQL_SLOW_LOG,
  POSTGRES_STATEMENT_LOG
}
```

| 值 | 含义 | 使用场景 |
| --- | --- | --- |
| `AUTO` | 自动识别日志格式。 | 用户未显式指定格式。 |
| `PLAIN_SQL` | 清洗后的纯 SQL 文件。 | 每条 SQL 用分号或空行分隔。 |
| `MYSQL_GENERAL_LOG` | MySQL general log。 | 从 `Query` 行抽取 SQL。 |
| `MYSQL_SLOW_LOG` | MySQL slow log。 | 跳过 `# Time` 等元信息后抽取 SQL。 |
| `POSTGRES_STATEMENT_LOG` | PostgreSQL statement log。 | 从 `statement:` 或 `execute ...:` 后抽取 SQL。 |

维护说明：

- `AUTO` 识别失败时不应猜测，应给出 warning 或要求用户指定。
- 原生日志解析只负责抽 SQL，不负责生成关系；关系解析交给 SQL parser。
- 后续新增云厂商日志格式时，新增 enum 值，例如 `ALIYUN_RDS_MYSQL_AUDIT_LOG`。

## 10. DirectionConfidence

表示解析器对关系方向的把握程度。它不直接输出给最终用户，但会影响是否生成列级 `FK_LIKE`。

```java
public enum DirectionConfidence {
  CERTAIN,
  HIGH,
  MEDIUM,
  LOW,
  AMBIGUOUS
}
```

| 值 | 含义 | Core 处理 |
| --- | --- | --- |
| `CERTAIN` | 方向确定。通常来自显式 FK。 | 可以输出列级 `FK_LIKE`。 |
| `HIGH` | 方向高度可信。通常来自 target unique + source 命名/索引。 | 可以输出列级 `FK_LIKE`。 |
| `MEDIUM` | 方向有一定依据，但不充分。 | 可结合其他 evidence；不足时降级。 |
| `LOW` | 方向依据很弱。 | 通常不生成列级 `FK_LIKE`。 |
| `AMBIGUOUS` | 方向不明确或多义。 | 退化为表级 `CO_OCCURRENCE` 并记录 warning。 |

例子：

- `orders.user_id = users.id`，`users.id` 是 PK：`HIGH`。
- 显式 FK `orders.user_id -> users.id`：`CERTAIN`。
- `a.code = b.code`，两边都非 unique：`AMBIGUOUS`。

维护说明：

- 不要把 `LOW` 的方向关系直接输出为强 `FK_LIKE`。
- `DirectionConfidence` 是解析中间状态，不等于最终 confidence 分数。

## 11. WarningType

表示扫描过程中出现的非致命问题类型。

```java
public enum WarningType {
  CONFIG_WARNING,
  PERMISSION_WARNING,
  PARSE_WARNING,
  PROFILE_WARNING,
  AMBIGUOUS_RELATION_WARNING,
  ADAPTOR_CAPABILITY_WARNING
}
```

| 值 | 含义 | 例子 | 是否中断 |
| --- | --- | --- | --- |
| `CONFIG_WARNING` | 配置可用但存在可疑项。 | include/exclude 同时命中同一表。 | 否 |
| `PERMISSION_WARNING` | 权限不足导致某些来源不可读。 | 不能读取 MySQL routine definition。 | 否 |
| `PARSE_WARNING` | 单条 SQL/DDL 解析失败。 | parser 不支持某段 PL/pgSQL。 | 否 |
| `PROFILE_WARNING` | 数据画像部分失败或跳过。 | 某候选画像查询超时。 | 否 |
| `AMBIGUOUS_RELATION_WARNING` | 关系方向或列映射不明确。 | `a.x = b.y` 两侧都非 unique。 | 否 |
| `ADAPTOR_CAPABILITY_WARNING` | adaptor 不支持用户开启的某项能力。 | adaptor 不支持 native log。 | 视配置而定 |

维护说明：

- warning 是“扫描还能继续”的问题。
- 不可恢复错误应使用错误码退出，不要只给 warning。
- 解析/提取失败应把原始失败 SQL/DDL 放入 `WarningMessage.attributes.rawStatement`；异常类型放入 `exceptionClass`，来源类型放入 `statementSourceType`。
- parser 正常返回空关系不自动等价为 `PARSE_WARNING`，因为很多 SQL 不包含可用关系证据。
- warning detail 不应包含 password 或采样到的真实业务值。

## 12. WarningSeverity

表示 warning 的严重程度。

```java
public enum WarningSeverity {
  INFO,
  WARN,
  ERROR
}
```

| 值 | 含义 | 例子 |
| --- | --- | --- |
| `INFO` | 诊断信息，不影响结果可信度。 | data profile disabled by config。 |
| `WARN` | 有能力缺失或局部失败，结果可能不完整。 | 某个 SQL 文件部分语句解析失败。 |
| `ERROR` | 单个来源或子任务失败，但整体扫描仍可继续。 | 读取对象定义失败，但元数据扫描成功。 |

维护说明：

- `ERROR` severity 不等于进程退出码非 0。
- 只有配置无法读取、数据库无法连接、输出无法写入等不可恢复错误才应让 CLI 返回非零。

## 13. ErrorCode

CLI 退出码建议用 enum 或常量集中管理。虽然 Java 进程退出码是 int，但代码中应避免散落魔法数字。

```java
public enum ErrorCode {
  OK(0),
  CONFIG_FILE_ERROR(1),
  CONFIG_FORMAT_ERROR(2),
  ARGUMENT_ERROR(3),
  ADAPTOR_ERROR(4),
  INPUT_FILE_ERROR(5),
  DATABASE_CONNECTION_ERROR(10),
  SCAN_RUNTIME_ERROR(11),
  OUTPUT_WRITE_ERROR(12)
}
```

| 值 | 退出码 | 含义 |
| --- | ---: | --- |
| `OK` | 0 | 成功。 |
| `CONFIG_FILE_ERROR` | 1 | 配置文件不存在、不可读。 |
| `CONFIG_FORMAT_ERROR` | 2 | YAML 格式错误或必填字段缺失。 |
| `ARGUMENT_ERROR` | 3 | 命令行参数错误。 |
| `ADAPTOR_ERROR` | 4 | adaptor 未找到、冲突或加载失败。 |
| `INPUT_FILE_ERROR` | 5 | DDL、日志、对象定义文件不存在或不可读。 |
| `DATABASE_CONNECTION_ERROR` | 10 | JDBC 连接失败。 |
| `SCAN_RUNTIME_ERROR` | 11 | 扫描运行中发生不可恢复错误。 |
| `OUTPUT_WRITE_ERROR` | 12 | 输出文件写入失败。 |

维护说明：

- 已使用的退出码不要复用给新含义。
- warning 不应直接改变退出码，除非配置要求“warning as error”。

## 14. AdaptorCapability

表示 adaptor 支持的能力。当前设计中也可以用 boolean record，但为了第三方 adaptor 扩展和配置校验，建议实现为 enum set。

```java
public enum AdaptorCapability {
  METADATA,
  DDL_PARSING,
  DATABASE_OBJECTS,
  NATIVE_LOGS,
  DATA_PROFILING,
  EVIDENCE_WEIGHT_ADJUSTMENT
}
```

| 值 | 含义 |
| --- | --- |
| `METADATA` | 支持 JDBC 元数据采集。 |
| `DDL_PARSING` | 支持该数据库方言的 DDL 解析或预处理。 |
| `DATABASE_OBJECTS` | 支持读取过程、函数、视图、触发器定义。 |
| `NATIVE_LOGS` | 支持该数据库原生日志格式提取 SQL。 |
| `DATA_PROFILING` | 支持对候选关系做数据画像。 |
| `EVIDENCE_WEIGHT_ADJUSTMENT` | 支持数据库特定 evidence 权重修正。 |

维护说明：

- capabilities 用于提前校验用户配置。
- 用户开启 adaptor 不支持的能力时，应给明确错误或 warning。
- 不要通过 capabilities 表示版本号；版本兼容应由 adaptor 自己判断。

## 15. ScanSourceKind

表示用户配置中启用的数据来源类型。

```java
public enum ScanSourceKind {
  METADATA,
  DDL,
  OBJECTS,
  LOGS,
  DATA_PROFILE
}
```

| 值 | 对应配置 | 含义 |
| --- | --- | --- |
| `METADATA` | `sources.metadata` | JDBC 元数据采集。 |
| `DDL` | `sources.ddl` | 本地 DDL 文件。 |
| `OBJECTS` | `sources.objects` | 过程、函数、视图、触发器定义。 |
| `LOGS` | `sources.logs` | 原生日志或纯 SQL 文本。 |
| `DATA_PROFILE` | `sources.dataProfile` | 可选数据画像。 |

维护说明：

- `DATA_PROFILE` 依赖已有候选关系，不能作为第一批独立扫描来源。
- 至少应启用 `METADATA`、`DDL`、`OBJECTS`、`LOGS` 中的一种；只开启 `DATA_PROFILE` 没有意义，应配置失败。

## 16. 枚举之间的关系

一次典型关系输出：

```json
{
  "source": { "table": "orders", "column": "user_id" },
  "target": { "table": "users", "column": "id" },
  "relationType": "FK_LIKE",
  "relationSubType": "PROFILE_SUPPORTED_FK",
  "confidence": 0.91,
  "evidence": [
    {
      "type": "SQL_LOG_JOIN",
      "sourceType": "NATIVE_LOG",
      "score": 0.55
    },
    {
      "type": "VALUE_CONTAINMENT_HIGH",
      "sourceType": "DATA_PROFILE",
      "score": 0.30
    }
  ]
}
```

解释：

- `RelationType = FK_LIKE`：最终关系像外键。
- `RelationSubType = PROFILE_SUPPORTED_FK`：主导可信形态是数据画像强支持。
- `EvidenceType = SQL_LOG_JOIN`：一条证据来自日志 JOIN。
- `EvidenceType = VALUE_CONTAINMENT_HIGH`：另一条证据来自数据画像。
- `EvidenceSourceType` 表示证据来源类别，不替代 evidence type。

## 17. 实现检查清单

- 每个 enum 都有 JSON 序列化/反序列化测试。
- 配置中的小写值能映射到内部大写 enum。
- 未知 enum 值给出明确错误，不静默忽略。
- 输出 JSON 使用稳定 enum 字符串。
- `RelationSubType` 根据主导证据优先级计算，不由最后一条 evidence 覆盖。
- 负向 evidence 不应变成 `RelationSubType`。
- warning severity 不直接决定进程退出码。
- 预留数据库类型没有 adaptor 时必须明确报错。
