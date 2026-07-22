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
- `--parser-mode`：可选，`auto`、`full-grammar` 或 `token-event`，覆盖 YAML 中的 `parser.mode`。
- `--grammar-profile`：可选，显式指定 full-grammar profile，例如 `postgresql/16`、`postgresql/17`、`postgresql/18`、`mysql/5.7`、`mysql/8.0`、`oracle/19c`。
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
  catalog: shop

filters:
  includeTables:
    - orders
    - users
    - audit_logs
  excludeTables:
    - tmp_orders
    - archive_orders

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
    timeoutSeconds: 30
    maxCandidatePairs: 1000

parser:
  mode: auto
  grammarProfile: mysql/8.0
  databaseVersion: 8.0.36

execution:
  parallelism: 1

output:
  format: json
  minConfidence: 0.30
  includeEvidence: true
  includeWarnings: true
  includeObservationCounts: true

namingMatch:
  enabled: true
  systemRulesEnabled: true
  ruleFiles:
    - config/naming-rules/customer.yml
  rules:
    - id: created-by-user
      rule: USER_CONFIGURED
      appliesTo: [RELATIONSHIP_CANDIDATE]
      sourceColumn:
        equalsAny: [created_by, updated_by, approved_by]
      targetTable:
        aliases: [users, employees]
      targetColumn:
        equals: id
      directionHint: true
    - id: sales-rep-explicit
      rule: USER_CONFIGURED
      appliesTo: [RELATIONSHIP_CANDIDATE, DDL_COLUMN_INVENTORY, METADATA]
      sourceEndpoint: orders.sales_rep_id
      targetEndpoint: employees.id
      directionHint: true

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
- 至少存在一种可执行 source：带 JDBC 的 metadata、带文件或 JDBC database-DDL 的 DDL、带文件或
  JDBC database-object 的 object，或至少一个已解析文件的 logs。`dataProfile` 不是独立 source。
- 启用文件 source 时文件必须存在。
- `ScanInputPathResolver` 是 `files + paths + include` 的唯一展开 owner。CLI 以配置文件父目录调用
  `ScanConfig.resolve(baseDirectory)`；直接 Java API 可传显式 base directory，无参 `resolve()` / `scan()`
  以当前工作目录为 base。运行态只消费稳定排序、规范绝对路径且去重的 `*Files`，缺失、非普通文件或
  不可读输入在 scan 前失败。
- `execution.parallelism` 默认 `1`，表示 scan 内按源顺序串行解析；设为正整数后，独立 file/object/log statement 可以并行解析，但每个 task 使用独立 visitor、collector、`AdaptorContext` 和 warning list，最终按原始 source order 合并。JDBC collection 本身不并行。CLI 的 `--parallelism <n>` 可覆盖该配置。
- `parser.sql.mode`、`parser.sql.fallbackOnFailure`、`parser.ddl.mode`、`parser.ddl.fallbackOnFailure` 已移除；配置中出现这些 key 时应显式报错。MySQL/PostgreSQL/Oracle/SQL Server SQL/DDL 均通过统一 `parser.mode` 选择 full-grammar 或 token-event，ANTLR 只作为底层 lexer/parser 支撑。
- 当前统一 parser 配置为 `parser.mode: auto|full-grammar|token-event`。默认 `auto`：能根据 `parser.grammarProfile`、`parser.databaseVersion` 或 JDBC metadata 选择版本化 full-grammar profile 时优先使用 full-grammar；不能选择 profile、版本不支持或 full-grammar hard failure 时使用 token-event fallback 并记录 warning。profile 已选中后的 syntax warning / partial result 属于所选 parser，不触发 fallback。CLI 可通过 `--parser-mode`、`--grammar-profile`、`--database-version` 覆盖 YAML。
- `parser.grammarProfile` 使用用户可见 profile id；当前内置 profile 包括 `mysql/5.7`、`mysql/8.0`、`postgresql/16`、`postgresql/17`、`postgresql/18`、`oracle/12c`、`oracle/19c`、`oracle/21c`、`oracle/26ai`、`sqlserver/2016`、`sqlserver/2017`、`sqlserver/2019`、`sqlserver/2022`、`sqlserver/2025`。
- PostgreSQL full-grammar 是严格大版本语法证明：`postgresql/16` 不用于通过 17/18 专属 correctness，`postgresql/17` 不用于通过 18 专属 correctness。token-event 可以作为未知版本或 unsupported version 的宽松 fallback。
- MySQL `SQL_MODE` 是 MySQL grammar runtime 的方言开关，不是 `parser.mode`。配置、CLI 和输出 diagnostics 中的 parser mode 只使用 `auto|full-grammar|token-event`。
- SQL Server 已有 token-event adaptor 和 versioned full-grammar sample-data golden；Oracle 已有 token-event adaptor 和 `INCOMPLETE_VERSIONED` full-grammar。没有对应 adaptor 前，不再用 simple parser 作为兼容假象。
- `sources.ddl.fromDatabase` 默认 `true`。开启时，支持的 adaptor 会读取数据库内表定义；MySQL 当前使用 `SHOW CREATE TABLE`，产生的 evidence source type 为 `DATABASE_DDL`。
- `sources.logs.filterSystemQueries` 默认 `true`。开启时，native log 中仅访问系统 catalog/schema 的 metadata 查询会被跳过，不记录 parse warning。
- `sources.logs.systemSchemas` 可覆盖当前数据库类型的默认系统 schema。MySQL 默认 `information_schema/performance_schema/mysql/sys`；PostgreSQL 默认 `pg_catalog/information_schema/pg_toast`。
- `sources.logs.metadataQueryMarkers` 可配置日志文本标记，例如 `ApplicationName=DBeaver`、`DatabaseMetaData`，用于跳过工具或 JDBC metadata 查询。
- `namingMatch.enabled` 默认 `true`。开启后，`NamingEvidenceExtractor` 使用合并后的 `NamingRuleSet` 生成 top-level `namingEvidence`；relationship 只能引用该证据池中的 `evidenceRef`，不能本地重新计算 `NAMING_MATCH`。
- `namingMatch.systemRulesEnabled` 默认 `true`。系统默认规则来自 `naming-rules/system-default.yml`，当前表达 `TABLE_ID`、`ID_SUFFIX_TO_ID`、`SELF_ROLE_ID` 三类 direct 规则。客户规则可通过 `ruleFiles` 和 inline `rules` 追加；重复 `id`、只配置 source 或 target 半边、或配置 `TRANSITIVE_NAMING_PATH` 都必须报错。
- 客户规则第一阶段统一使用 `rule: USER_CONFIGURED`，支持列名 equals / equalsAny / suffix / suffixAny、表 aliases、显式 `sourceEndpoint` / `targetEndpoint`。`TRANSITIVE_NAMING_PATH` 不是客户配置规则，只能由 derived path 引擎产生。
- `derivedPaths.enabled` 默认 `false`。开启后输出传递推导视图：`derivedRelationships`、`derivedDataLineages`，并可向 top-level `namingEvidence` 增加 `TRANSITIVE_NAMING_PATH`。JSON 同时提供只读轻量视图 `derivedNamingEvidence`，方便统计和阅读 derived naming；完整 grouped evidence / rawEvidence 仍只保存在 top-level `namingEvidence`，relationship 也只引用 top-level `namingEvidence.id`。relationship 推导内部可反向遍历 referenced-by 图，但 JSON 中的 derived relationship source/target 仍保持 FK-like 正向；审计路径放在 `path`、`traversalPath` 和 attributes 中。`maxPathLength` 默认 `5`；`maxPathsPerPair=0` 和 `maxFacts=0` 表示不限制。
- 启用 live-backed source 时必须有非空 `jdbcUrl`；username/password 是 driver-dependent 认证输入，
  不强制两者同时非空。
- `timeoutSeconds`、execution 和 profiling 数量限制必须为正数；derived path length 必须大于零，
  其它 derived 数量上限不得为负数。
- `output.minConfidence`、derived confidence/decay 和 profiling ratio 必须是 `[0,1]` 内有限数。
- `ScanConfigurationValidator` 是 YAML override 后、batch 和 direct API 共用的主要行为校验入口；
  `ScanConfig.resolve()`、`ResolvedScanConfig` 构造与 `ScanEngine.scan()` 都会调用它。数值/source/parser
  约束同时由 immutable config record 构造器防守。`NamingRuleSetResolver` 由 core 统一拥有 rule-file
  加载：CLI 只解析相对路径，direct API 按显式 base directory 或当前工作目录解析；system、file、inline
  typed rules 在 JDBC 前合并并检查重复 ID。parser compatibility view 只复制最终 typed rules，不再保留
  rule-file 路径，因此同一文件不会二次加载。
- PostgreSQL/SQL Server live scope resolver 为验证 connection catalog 而在连接建立后抛出的
  `LiveSourceConfigurationException` 仍属于不可恢复配置错误。direct API 原样抛出；single-scan 和
  batch CLI 都必须映射为 `CONFIG_FORMAT_ERROR`，不能降级 warning 或归入通用 argument/runtime error。
- capability preflight 缺少请求的 producer/consumer 时统一抛 `AdaptorContractException`；single-scan
  映射为 `ADAPTOR_ERROR`，batch case 保留同一 code，batch 整体仍返回 `BATCH_PARTIAL_FAILURE`。
- 上述 adaptor error 分类与 `execution.parallelism` 无关。串行 statement task 直接传播
  `AdaptorContractException`；并行 task 的 `ScanTaskExecutor` 从 `ExecutionException.cause`
  识别并原样传播同一异常。single 与 batch 因此都稳定映射为 `ADAPTOR_ERROR`，不按 message
  字符串重新分类。
- YAML 中旧的离线画像字段会返回 `CONFIG_FORMAT_ERROR`，不会被未知字段策略静默忽略。

MySQL namespace兼容说明：live collector内部把database统一规范到catalog轴；旧
`database.schema`只作为输入兼容回退。顶层JSON的`database`对象在catalog非空时增加可选
`catalog`，并始终保留legacy `schema`。catalog-only配置因此输出`catalog=<database>`和空schema；
endpoint与scan summary使用同一canonical database。

`filters.includeTables` / `filters.excludeTables` 当前是精确物理表名列表，adaptor 只做
大小写/引用符规范化后的 exact match。`tmp_*` 之类 glob 只适用于文件路径
`paths + include`，不适用于 live table filter。

## JSON 输出

顶层：

```json
{
  "database": {
    "type": "MYSQL",
    "catalog": "sample_data",
    "schema": ""
  },
  "generatedAt": "2026-06-14T00:00:00Z",
  "summary": {
    "directRelationshipCount": 1,
    "derivedRelationshipCount": 0,
    "totalRelationshipCount": 1,
    "directDataLineageCount": 1,
    "derivedDataLineageCount": 0,
    "totalDataLineageCount": 1,
    "directNamingEvidenceCount": 1,
    "derivedNamingEvidenceCount": 0,
    "totalNamingEvidenceCount": 1,
    "directRelationshipObservationCount": 2,
    "derivedRelationshipObservationCount": 0,
    "totalRelationshipObservationCount": 2,
    "directDataLineageObservationCount": 2,
    "derivedDataLineageObservationCount": 0,
    "totalDataLineageObservationCount": 2,
    "directNamingEvidenceObservationCount": 2,
    "derivedNamingEvidenceObservationCount": 0,
    "totalNamingEvidenceObservationCount": 2,
    "warningCount": 0,
    "sources": ["metadata", "ddl", "logs"]
  },
  "relationships": [],
  "dataLineages": [],
  "derivedRelationships": [],
  "derivedDataLineages": [],
  "namingEvidence": [],
  "derivedNamingEvidence": [],
  "warnings": []
}
```

summary 只保留三段式字段：`direct*Count`、`derived*Count`、`total*Count`，relationship、dataLineage、namingEvidence 三类事实保持一致。

Observation count 也只保留三段式字段：`direct*ObservationCount`、`derived*ObservationCount`、`total*ObservationCount`。它统计 merged fact 背后的真实 occurrence：不同位置分别计数，同一位置折叠的 `occurrenceCount` 也累加。relationship、lineage、naming 和 derived path 都通过统一 occurrence helper 计算。它们不代表新的业务事实，不参与 confidence 计算；可通过 `output.includeObservationCounts: false` 关闭。

`output.includeWarnings` 控制一个完整的公开输出视图。为 `true` 时保留根、relationship 和 lineage warning；为 `false` 时三处 warning 数组均为空，且 `summary.warningCount=0`。该选项不会删除 `ScanResult` 内部 warning，也不会改变 CLI 根据真实 warning 数得出的退出状态。semantic-layer 严格 reader 因而可以同时消费完整 warning 输出和由 writer 生成的完整 suppressed 输出；人工拼接出 warning 数组与 count 不一致的 JSON 仍会被拒绝。

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
      "sourceType": "NAMING_HEURISTIC",
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
- relationship、data lineage 和 naming evidence 都有 `rawEvidence` / grouped `evidence` 双层模型。`rawEvidence` 保留归并前可区分的每次观测；完全相同的 observation 才折叠为 `occurrenceCount`。Naming 按有向 canonical endpoint pair 分组并保留全部 file/object/statement/block/line；Data Lineage 在 fact identity 前 canonical dedupe/sort source set。relationship、lineage、naming 和 derived 的事实身份均使用 dialect-aware canonical endpoint key；公开 `normalizedKey()` 只承担输出/evidence 兼容。
- `evidence` 默认输出，除非用户关闭 evidence；它保留归并后的摘要证据，并参与最终 confidence 计算。
- top-level `namingEvidence` 是完整命名证据池；relationship 中的 `NAMING_MATCH` 只保存 `evidenceRef` 和方向摘要，不重复完整 raw observations。
- 重复观测不会把同一个基础分无限叠加。不同 SQL/DDL/metadata 位置形成可区分 observation，
  才可通过 `REPEATED_OBSERVATION` 获得最多 0.10 的递减增益；同一位置的完全相同 parser
  重复事件只折叠为 `occurrenceCount`，不得提高 confidence。
- JSON 输出由 Jackson `ObjectMapper` 生成；输出字段保持稳定，后续新增字段应向后兼容。

当前实现备注：

- relationship 和 naming evidence 的 evidence type 已按现有 evidence model 输出。
- data lineage 已有 `rawEvidence` / grouped `evidence` 双层结构和 stable source/target facts。当前 JSON writer 为 lineage evidence 统一输出 `type=DATA_LINEAGE`，并同时保留 `transformType/sourceType/score/source/detail/attributes`；消费者应使用 `flowKind` 与 `transformType` 理解值流语义，不能用 SQL 文本重新推断结构。
- 文件来源的 `rawEvidence.source` 应输出 repo-relative path；object/routine 来源可以输出对象名。不得重新输出本机绝对路径。

## Table 输出

适合终端阅读：

```text
SOURCE              TARGET        TYPE     SUBTYPE              CONF  EVIDENCE
orders.user_id      users.id      FK_LIKE  DECLARED_FK          0.98  METADATA_FOREIGN_KEY
orders.customer_id  customers.id  FK_LIKE  INFERRED_JOIN_FK     0.65  SQL_LOG_JOIN,NAMING_MATCH

Warnings: 2
- mysql-slow.log:128 parse failed: unsupported syntax
- routines.sql:42 ambiguous join condition
```

规则：

- `TableResultWriter` 保留 `RelationshipMerger` 已经产生的顺序：confidence 降序，同分时按
  source endpoint、target endpoint 排序。writer 本身不重新排序。
- table 输出仍受 `minConfidence` 过滤。
- 当前列宽是最小宽度，长 endpoint/evidence 会扩展行宽；writer 不做终端宽度探测、折行或
  截断。因此 table 是轻量人工阅读视图，不是窄终端自适应报表。
- `includeEvidence` / `includeWarnings` / `includeObservationCounts` 的结构化隐藏契约属于
  JSON writer；当前 table 始终显示 relationship evidence type 和根 warning 摘要。
- 没有 relationship 时输出 `No relationships detected.`，随后仍输出统一 warning 数量和每条根
  warning 的 source、line、message；空结果不能丢失诊断明细。
- `SQL_LOG_TABLE_CO_OCCURRENCE` / `SQL_LOG_COLUMN_CO_OCCURRENCE` 是兼容保留 evidence；当前生产 parser 默认不主动输出，普通 table 输出示例不再把它们作为现行关系来源展示。

## Warning 设计

warning 类型：

- `CONFIG_WARNING`
- `PERMISSION_WARNING`
- `LIVE_SOURCE_WARNING`
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
- `FULL_GRAMMAR_SQL_PARSE_WARNING`：full-grammar SQL parser 产生语法诊断或只返回 partial result。
- `FULL_GRAMMAR_DDL_PARSE_WARNING`：full-grammar DDL parser 产生语法诊断或只返回 partial result。
- `FULL_GRAMMAR_VERSION_UNSUPPORTED_SYNTAX`：严格版本 full-grammar 遇到高版本专属语法，例如 PG16 profile 解析 PG17-only SQL/DDL。profile 一旦选中，syntax diagnostic / partial result 仍属于该 full-grammar 结果，不触发 token-event fallback；只有 profile 选择失败或 parser hard failure 才 fallback。versioned correctness fixture 中这类情况应失败。

边界：

- “没有识别出关系”不一定是解析失败。例如一条纯过滤 SQL 没有 JOIN/IN/EXISTS 关系时，可以没有 warning。
- warning 不产生 evidence，也不参与 confidence 计算；它只告诉运维人员哪些输入没有被完整消费。
- `rawStatement` 可能很长，适合用于审计和排错；后续如果输出体积成为问题，可以新增截断策略，但默认必须优先保留可诊断性。
- 上述 parser warning 的 `rawStatement` 是显式审计选择，不能推广到 JDBC/live collector warning。
  connection、metadata、object、database-DDL 和 data profiler failure 统一通过
  `LiveDiagnosticSanitizer` 输出固定消息、SQLState、vendor code、exception class及白名单对象/endpoint
  上下文；不得输出 JDBC URL、rendered SQL、driver message、连接参数或业务值。
- live object/database-DDL collector 和 `SourceCollectorPipeline` 都必须在 parser 前拒绝 null/blank
  definition。能取得对象身份时，输出 `LIVE_SOURCE_WARNING` / `DEFINITION_UNAVAILABLE`
  以及 catalog/schema/name/type；第三方 collector 如果返回 null 元素或 null list，pipeline
  也必须产生安全 warning，不得静默丢弃或调用下游 parser。
- 已枚举对象身份后，definition query 成功但返回零行也是 unavailable-result，
  必须使用同一 `DEFINITION_UNAVAILABLE` 契约。MySQL `SHOW CREATE TABLE`
  和 Oracle `DBMS_METADATA.GET_DDL` 的零行分支均输出带表身份的安全 warning，并跳过空
  `DatabaseDdlDefinition`。
- profiler 与普通 metadata/object/database-DDL collector 共用 `JdbcExceptionClassifier`的标准
  timeout、SQLState permission 和 query-failure 分类。Oracle 1031、SQL Server 229/916 必须由
  对应 adaptor 的调用边界传入，不能在共享 classifier 默认应用到其它方言。
  `LiveDiagnosticSanitizer` 统一输出固定消息及 SQLState/vendorCode/exceptionClass，不保留原始异常文本。
- `MetadataSnapshot.warnings()` 和 object/database-DDL callback warning 先进入临时 outcome。
  `AdaptorResultContractValidator` 只接受受限 type、severity、code 与安全属性，丢弃 plugin
  message/source/line，再由 `LiveDiagnosticSanitizer` 按 operation 重建固定内容。任一 warning
  envelope 非法时整个对应 SPI outcome 原子失败，不会把先前 fact 或 warning 写入 `ScanResult`。
  真实 driver/version 的 permission、timeout 与连接行为仍需环境 smoke 验证。
- `SqlLogExtractor`、`DialectScriptFramer` 与 structured parser 的 callback/result warning
  由 `AdaptorParseResultContractValidator` 使用独立 buffer、allowlist 和全批延迟提交闭环；
  任一 contract violation 不会泄漏前序 warning，也不能触发 token-event fallback。

严重程度：

- `INFO`
- `WARN`
- `ERROR`

`ERROR` 级 warning 不一定让 CLI 失败，只有不可恢复错误才返回非零。

## 错误码

`ErrorCode` enum 的目标 single-scan 映射是：`0` 成功、`1` 配置文件不可读、`2` 配置
格式/shape/执行期 live namespace 错误、`3` 命令参数错误、`4` adaptor 错误、`5` 输入文件错误、
`10` 数据库连接错误、`11` 其它 scan runtime 错误、`12` 输出写入错误；batch partial failure 为
`13`。参数解析位于顶层 try boundary 内，非法 option value 稳定返回 `3`。stderr 使用固定脱敏文本，
不传播 JDBC URL、SQL 或原始异常消息。

静态 loader、override 和 input/connection/output 分支已对应。执行期
`LiveSourceConfigurationException` 由 `SingleScanRunner` 转为 `CONFIG_FORMAT_ERROR`；batch 复用同一
runner，并由 `BatchScheduler` 保留 typed `CliFailure`，因此 case code 同为 `CONFIG_FORMAT_ERROR`、batch
总退出码仍为 `BATCH_PARTIAL_FAILURE`。所有 stderr 和 batch report error text 使用固定安全消息。

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
- table 输出稳定保留已合并 relationship 的顺序、confidence 和 evidence type；
  窄终端自适应不是当前能力。
- warning 摘要能帮助定位输入问题。
- 错误码稳定且有文档说明。

## 测试设计

- JSON schema 兼容性测试。
- `TableResultWriterTest` 验证已排序 `ScanResult` 的顺序保留、evidence 首次出现去重、长
  endpoint/evidence 完整输出、空关系 warning 明细和无副作用。
- 如果未来引入折行/截断，再增加终端宽度契约。
- `minConfidence` 过滤测试。
- `TableOutputCliTest` 验证 `--format table` 覆盖 YAML、`--output` 写文件和
  `--direct-output` 仅支持 JSON。
- warning 摘要测试。
- 错误码测试。
- README 示例命令 smoke test。
