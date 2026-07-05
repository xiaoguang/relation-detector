# Phase 8：输出和用户体验详细设计

## 目标

完善 CLI 使用体验、JSON 输出、table 输出、错误码、warning 摘要、README 和示例配置，使用户可以稳定运行工具并理解每条关系的来源与置信度。

## CLI 命令

主命令：

```bash
relation-detector scan --config config.yml
```

常用参数：

```bash
relation-detector scan \
  --config config.yml \
  --format json \
  --output result.json \
  --parser-mode auto \
  --grammar-profile postgresql/18 \
  --database-version 18.1 \
  --plugin-dir plugins/
```

参数：

- `--config`：必填，YAML 配置路径。
- `--format`：可选，`json` 或 `table`，默认读取配置。
- `--output`：可选，输出文件；未指定时输出到 stdout。
- `--plugin-dir`：可选，外部 adaptor jar 目录。
- `--min-confidence`：可选，覆盖配置中的最小置信度。
- `--parser-mode`：可选，`auto`、`full-grammer` 或 `token-event`，覆盖 YAML 中的 `parser.mode`。
- `--grammar-profile`：可选，显式指定 full-grammer profile，例如 `postgresql/16`、`postgresql/17`、`postgresql/18`、`mysql/5.7`、`mysql/8.0`、`oracle/19c`。
- `--database-version`：可选，显式指定数据库版本，例如 `16.5`、`18.1`；优先级高于 JDBC metadata。

## YAML 配置

完整示例：

```yaml
database:
  type: mysql
  adaptorId: mysql
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
    fromDatabase: true
    files:
      - schema.sql
  objects:
    enabled: true
    fromDatabase: true
    files:
      - routines.sql
  logs:
    enabled: true
    files:
      - mysql-general.log
      - app-sql.sql
  dataProfile:
    enabled: false
    sampleRows: 10000
    timeoutSeconds: 30
    maxCandidatePairs: 1000

parser:
  mode: auto
  grammarProfile: mysql/8.0
  databaseVersion: 8.0.36

output:
  format: json
  minConfidence: 0.30
  includeEvidence: true
  includeWarnings: true
  includeObservationCounts: true

derivedPaths:
  enabled: false
  relationships: true
  dataLineage: true
  namingEvidence: true
  includeNamingEdgesInRelationshipPaths: true
  maxPathLength: 5
  maxPathsPerPair: 0
  maxFacts: 0
  confidenceDecay: 0.75
  minConfidence: 0.10
```

配置校验：

- `database.type` 必填。
- 至少启用一种 source。
- 启用文件 source 时文件必须存在。
- `parser.sql.mode`、`parser.sql.fallbackOnFailure`、`parser.ddl.mode`、`parser.ddl.fallbackOnFailure` 已移除；配置中出现这些 key 时应显式报错。MySQL/PostgreSQL/Oracle SQL/DDL 均通过统一 `parser.mode` 选择 full-grammer 或 token-event，ANTLR 只作为底层 lexer/parser 支撑。
- 当前统一 parser 配置为 `parser.mode: auto|full-grammer|token-event`。默认 `auto`：能根据 `parser.grammarProfile`、`parser.databaseVersion` 或 JDBC metadata 选择版本化 full-grammer profile 时优先使用 full-grammer；不能选择 profile、版本不支持或 full-grammer hard failure 时使用 token-event fallback 并记录 warning。profile 已选中后的 syntax warning / partial result 属于所选 parser，不触发 fallback。CLI 可通过 `--parser-mode`、`--grammar-profile`、`--database-version` 覆盖 YAML。
- `parser.grammarProfile` 使用用户可见 profile id；当前内置 profile 包括 `mysql/5.7`、`mysql/8.0`、`postgresql/16`、`postgresql/17`、`postgresql/18`、`oracle/12c`、`oracle/19c`、`oracle/21c`、`oracle/26ai`、`sqlserver/2016`、`sqlserver/2017`、`sqlserver/2019`、`sqlserver/2022`、`sqlserver/2025`。
- PostgreSQL full-grammer 是严格大版本语法证明：`postgresql/16` 不用于通过 17/18 专属 correctness，`postgresql/17` 不用于通过 18 专属 correctness。token-event 可以作为未知版本或 unsupported version 的宽松 fallback。
- MySQL `SQL_MODE` 是 MySQL grammar runtime 的方言开关，不是 `parser.mode`。配置、CLI 和输出 diagnostics 中的 parser mode 只使用 `auto|full-grammer|token-event`。
- SQL Server 已有 token-event adaptor 和 versioned full-grammer sample-data golden；Oracle 已有初始 token-event adaptor 和 `INCOMPLETE_VERSIONED` full-grammer。没有对应 adaptor 前，不再用 simple parser 作为兼容假象。
- `sources.ddl.fromDatabase` 默认 `true`。开启时，支持的 adaptor 会读取数据库内表定义；MySQL 当前使用 `SHOW CREATE TABLE`，产生的 evidence source type 为 `DATABASE_DDL`。
- `sources.logs.filterSystemQueries` 默认 `true`。开启时，native log 中仅访问系统 catalog/schema 的 metadata 查询会被跳过，不记录 parse warning。
- `sources.logs.systemSchemas` 可覆盖当前数据库类型的默认系统 schema。MySQL 默认 `information_schema/performance_schema/mysql/sys`；PostgreSQL 默认 `pg_catalog/information_schema/pg_toast`。
- `sources.logs.metadataQueryMarkers` 可配置日志文本标记，例如 `ApplicationName=DBeaver`、`DatabaseMetaData`，用于跳过工具或 JDBC metadata 查询。
- `derivedPaths.enabled` 默认 `false`。开启后输出传递推导视图：`derivedRelationships`、`derivedDataLineages`，并可向 top-level `namingEvidence` 增加 `TRANSITIVE_NAMING_PATH`。`maxPathLength` 默认 `5`；`maxPathsPerPair=0` 和 `maxFacts=0` 表示不限制。
- 启用 JDBC source 时 jdbcUrl、username、password 必须可解析。
- `sampleRows`、`timeoutSeconds` 必须为正数。

## JSON 输出

顶层：

```json
{
  "database": {
    "type": "mysql",
    "schema": "shop"
  },
  "generatedAt": "2026-06-14T00:00:00Z",
  "summary": {
    "relationshipCount": 1,
    "dataLineageCount": 1,
    "namingEvidenceCount": 1,
    "relationshipObservationCount": 2,
    "dataLineageObservationCount": 2,
    "namingEvidenceObservationCount": 2,
    "warningCount": 0,
    "sources": ["metadata", "ddl", "logs"]
  },
  "relationships": [],
  "dataLineages": [],
  "derivedRelationships": [],
  "derivedDataLineages": [],
  "namingEvidence": [],
  "warnings": []
}
```

`relationshipObservationCount`、`dataLineageObservationCount`、`namingEvidenceObservationCount`
以及 derived path 对应的 observation count 是调试字段，只统计 merged fact 背后的 raw evidence observation 数量，用来解释“一个最终关系/血缘/命名证据/推导路径由多少次原始出现合并而来”。它们不代表新的业务事实，不参与 confidence 计算；可通过 `output.includeObservationCounts: false` 关闭。

关系：

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
  "relationSubType": "PROFILE_SUPPORTED_FK",
  "confidence": 0.91,
  "rawEvidence": [
    {
      "type": "SQL_LOG_JOIN",
      "sourceType": "NATIVE_LOG",
      "score": 0.55,
      "source": "mysql-slow-log",
      "detail": "line 10: o.user_id = u.id",
      "attributes": {}
    },
    {
      "type": "SQL_LOG_JOIN",
      "sourceType": "NATIVE_LOG",
      "score": 0.55,
      "source": "mysql-slow-log",
      "detail": "line 38: o.user_id = u.id",
      "attributes": {}
    }
  ],
  "evidence": [
    {
      "type": "SQL_LOG_JOIN",
      "sourceType": "NATIVE_LOG",
      "score": 0.55,
      "source": "mysql-slow-log",
      "detail": "line 10: o.user_id = u.id",
      "attributes": {
        "count": 2,
        "firstDetail": "line 10: o.user_id = u.id",
        "lastDetail": "line 38: o.user_id = u.id",
        "sampleDetails": [
          "line 10: o.user_id = u.id",
          "line 38: o.user_id = u.id"
        ],
        "sampleTruncated": false
      }
    },
    {
      "type": "NAMING_MATCH",
      "sourceType": "DERIVED",
      "score": 0.20,
      "source": "naming:orders.user_id->users.id:ID_SUFFIX_TO_ID",
      "detail": "Naming evidence naming:orders.user_id->users.id:ID_SUFFIX_TO_ID",
      "evidenceRef": "naming:orders.user_id->users.id:ID_SUFFIX_TO_ID",
      "attributes": {
        "evidenceRef": "naming:orders.user_id->users.id:ID_SUFFIX_TO_ID",
        "namingRule": "ID_SUFFIX_TO_ID",
        "suggestedSourceEndpoint": "orders.user_id",
        "suggestedTargetEndpoint": "users.id",
        "directionHint": true
      }
    },
    {
      "type": "REPEATED_OBSERVATION",
      "sourceType": "NATIVE_LOG",
      "score": 0.05,
      "source": "mysql-slow-log",
      "detail": "Repeated SQL_LOG_JOIN observed 2 times",
      "attributes": {
        "count": 2,
        "maxScore": "0.10",
        "formula": "maxScore * (1 - 1 / count)",
        "baseEvidenceType": "SQL_LOG_JOIN"
      }
    }
  ],
  "warnings": []
}
```

命名证据：

```json
{
  "id": "naming:orders.user_id->users.id:ID_SUFFIX_TO_ID",
  "source": {
    "table": "orders",
    "column": "user_id"
  },
  "target": {
    "table": "users",
    "column": "id"
  },
  "rule": "ID_SUFFIX_TO_ID",
  "directionHint": true,
  "rawEvidence": [],
  "evidence": []
}
```

字段血缘：

```json
{
  "sources": [
    {
      "table": "orders",
      "column": "pay_amount"
    }
  ],
  "target": {
    "table": "users",
    "column": "total_spent"
  },
  "flowKind": "VALUE",
  "transformType": "AGGREGATE",
  "confidence": 0.80,
  "rawEvidence": [],
  "evidence": []
}
```

规则：

- `confidence` 保留两位或四位小数，内部计算用高精度。
- 表级关系的 `column` 为 `null`。
- relationship、data lineage 和 naming evidence 都有 `rawEvidence` / grouped `evidence` 双层模型；`rawEvidence` 保留归并前每一次观测，适合审计、排错和回放。
- `evidence` 默认输出，除非用户关闭 evidence；它保留归并后的摘要证据，并参与最终 confidence 计算。
- top-level `namingEvidence` 是完整命名证据池；relationship 中的 `NAMING_MATCH` 只保存 `evidenceRef` 和方向摘要，不重复完整 raw observations。
- 重复观测不会把同一个基础分无限叠加；摘要 evidence 记录 `count` 和样本 detail，并额外使用 `REPEATED_OBSERVATION` 表示最多 0.10 的递减增益。
- JSON 输出由 Jackson `ObjectMapper` 生成；输出字段保持稳定，后续新增字段应向后兼容。

## Table 输出

适合终端阅读：

```text
SOURCE              TARGET        TYPE     SUBTYPE              CONF  EVIDENCE
orders.user_id      users.id      FK_LIKE  DECLARED_FK          0.98  METADATA_FOREIGN_KEY
users               audit_logs    CO_OCCURRENCE  TABLE_CO_OCCURRENCE  0.25  SQL_LOG_TABLE_CO_OCCURRENCE

Warnings: 2
- mysql-slow.log:128 parse failed: unsupported syntax
- routines.sql:42 ambiguous join condition
```

规则：

- 默认按 confidence 降序。
- 同分时按 source table、target table 排序。
- `--verbose` 时显示更多 evidence detail。
- table 输出仍受 `minConfidence` 过滤。

## Warning 设计

warning 类型：

- `CONFIG_WARNING`
- `PERMISSION_WARNING`
- `PARSE_WARNING`
- `PROFILE_WARNING`
- `AMBIGUOUS_RELATION_WARNING`
- `ADAPTOR_CAPABILITY_WARNING`

warning 字段：

- code。
- message。
- source。
- line。
- severity。
- attributes：结构化诊断属性，默认 `{}`。

解析/提取失败时，`attributes` 应尽量包含：

- `rawStatement`：原始无法解析或抛错的 SQL/DDL 文本。对于 DDL 文件，通常是整个 DDL 文件内容；对于 SQL 日志、函数、过程、视图、触发器，通常是当前被投喂给 SQL parser 的那条语句。
- `statementSourceType`：语句来源，例如 `PROCEDURE`、`FUNCTION`、`VIEW`、`TRIGGER`、`NATIVE_LOG`、`PLAIN_SQL`。
- `endLine`：语句结束行。`line` 字段保留开始行。
- `exceptionClass`：异常类短名，例如 `IllegalArgumentException`、`NoSuchFileException`。
- `objectSchema/objectName/objectType`：可选，来自数据库对象定义的上下文，例如 procedure、function、view、trigger、event。
- `routineSchema/routineName/routineType`：可选，仅 procedure/function 额外提供的别名字段，方便运维按 routine 定位。

示例：

```json
{
  "type": "PARSE_WARNING",
  "severity": "WARN",
  "code": "SQL_PARSE_FAILED",
  "message": "unsupported tuple predicate",
  "source": "routines.sql",
  "line": 12,
  "attributes": {
    "statementSourceType": "PROCEDURE",
    "endLine": 26,
    "exceptionClass": "IllegalArgumentException",
    "objectSchema": "shop",
    "objectName": "rebuild_orders",
    "objectType": "PROCEDURE",
    "routineSchema": "shop",
    "routineName": "rebuild_orders",
    "routineType": "PROCEDURE",
    "rawStatement": "CREATE PROCEDURE rebuild_orders() BEGIN SELECT ... END"
  }
}
```

当前稳定 warning code：

- `DDL_PARSE_FAILED`：DDL parser 抛异常或 DDL 文件读取失败。
- `SQL_PARSE_FAILED`：单条 SQL、过程、函数、视图、触发器或日志语句进入关系 parser 后抛异常。
- `SQL_FILE_EXTRACT_FAILED`：普通 SQL/object 文件读取或切分失败。
- `LOG_EXTRACT_FAILED`：数据库原生日志读取或抽取失败。
- `OBJECT_DEFINITION_COLLECT_FAILED`：数据库对象定义整体收集失败。
- `MYSQL_ROUTINE_COLLECT_FAILED`、`MYSQL_VIEW_COLLECT_FAILED`、`MYSQL_TRIGGER_COLLECT_FAILED`：MySQL 对象定义部分收集失败。
- `POSTGRES_FUNCTION_COLLECT_FAILED`、`POSTGRES_VIEW_COLLECT_FAILED`：PostgreSQL 对象定义部分收集失败。
- `FULL_GRAMMAR_SQL_PARSE_WARNING`：full-grammer SQL parser 产生语法诊断或只返回 partial result。
- `FULL_GRAMMAR_DDL_PARSE_WARNING`：full-grammer DDL parser 产生语法诊断或只返回 partial result。
- `FULL_GRAMMAR_VERSION_UNSUPPORTED_SYNTAX`：严格版本 full-grammer 遇到高版本专属语法，例如 PG16 profile 解析 PG17-only SQL/DDL。普通运行时可以 fallback token-event；versioned correctness fixture 中这类情况应失败。

边界：

- “没有识别出关系”不一定是解析失败。例如一条纯过滤 SQL 没有 JOIN/IN/EXISTS 关系时，可以没有 warning。
- warning 不产生 evidence，也不参与 confidence 计算；它只告诉运维人员哪些输入没有被完整消费。
- `rawStatement` 可能很长，适合用于审计和排错；后续如果输出体积成为问题，可以新增截断策略，但默认必须优先保留可诊断性。

严重程度：

- `INFO`
- `WARN`
- `ERROR`

`ERROR` 级 warning 不一定让 CLI 失败，只有不可恢复错误才返回非零。

## 错误码

- `0`：成功。
- `1`：配置文件不存在或不可读。
- `2`：配置格式错误。
- `3`：参数错误。
- `4`：adaptor 未找到或冲突。
- `5`：输入文件错误。
- `10`：数据库连接失败。
- `11`：扫描运行失败。
- `12`：输出写入失败。

## README 内容

README 至少包含：

- 工具目标。
- 快速开始。
- MySQL 示例。
- PostgreSQL 示例。
- YAML 配置说明。
- JSON 输出说明。
- confidence 解释。
- adaptor 插件说明。
- 数据画像安全说明。
- 常见问题。

## 示例资产

在 `test-fixtures` 或 `examples` 提供：

- `mysql-config.yml`
- `postgres-config.yml`
- `schema.sql`
- `routines.sql`
- `mysql-general.log`
- `postgres-statement.log`
- `expected-output.json`

## 验收标准

- 用户能根据 README 跑通 MySQL 示例。
- 用户能根据 README 跑通 PostgreSQL 示例。
- JSON 输出遵守稳定 schema，可被标准 JSON parser 稳定反序列化；当前实现由 Jackson `ObjectMapper` 生成。
- table 输出在窄终端中仍可读。
- warning 摘要能帮助定位输入问题。
- 错误码稳定且有文档说明。

## 测试设计

- JSON schema 兼容性测试。
- table 排序测试。
- `minConfidence` 过滤测试。
- `--format` 覆盖配置测试。
- `--output` 写文件测试。
- warning 摘要测试。
- 错误码测试。
- README 示例命令 smoke test。
