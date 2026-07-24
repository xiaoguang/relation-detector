# 数据关系发现工具调用 relation-detector 参数设计

本文定义外部“数据关系发现工具”如何调用 relation-detector、如何透传用户过滤规则和数据库版本方言参数，以及如何消费 relation-detector 生成的结果。

## 1. 集成边界

外部工具只负责收集用户意图、连接信息、文件输入和展示结果；关系发现事实由 relation-detector 生成。推荐集成方式是：

```text
数据关系发现工具
  -> 生成 relation-detector YAML 配置
  -> 调用 relation-detector CLI
  -> 读取 result.json
  -> 展示 / 存储 / 传给 semantic-layer
```

工具端不要重新解析 SQL、不要自己补 `NAMING_MATCH`、不要根据表名/列名自行创造 relationship 或 lineage。用户配置规则应通过 `namingMatch`、`filters`、`parser`、`derivedPaths`、`sources` 等字段透传给 relation-detector。

## 2. 调用方式

基础命令：

```bash
relation-detector/scripts/run-cli.sh scan \
  --config /path/to/relation-detector-config.yml \
  --format json \
  --output /path/to/relation-detector-result.json
```

CLI 覆盖参数：

| CLI 参数 | 作用 | 推荐使用场景 |
| --- | --- | --- |
| `--config <file>` | 指向工具生成的 YAML 配置 | 必填 |
| `--format json|table` | 覆盖 `output.format` | 生产集成固定用 `json` |
| `--output <file>` | 输出 JSON 文件路径 | 工具端持久化结果 |
| `--plugin-dir <dir>` | 外部 adaptor jar 目录 | 仅插件化 adaptor 场景 |
| `--min-confidence <n>` | 覆盖 `output.minConfidence` | 用户临时调阈值 |
| `--parser-mode <mode>` | 覆盖 `parser.mode` | 用户强制 token-event 或 full-grammar |
| `--grammar-profile <id>` | 覆盖 `parser.grammarProfile` | 用户明确数据库版本方言 |
| `--database-version <v>` | 覆盖 `parser.databaseVersion` | JDBC 无法获取版本或离线文件扫描 |

推荐规则：稳定参数写入 YAML；临时调试参数用 CLI 覆盖。

## 3. 工具侧参数模型

外部工具可以维护自己的请求对象，但字段应能无损映射到 relation-detector YAML。

```yaml
relationDetectionRequest:
  database:
    type: mysql | postgresql | oracle | sqlserver | common
    jdbcUrl: optional
    username: optional
    password: optional
    catalog: optional
    schema: optional
    adaptorId: optional

  parser:
    mode: auto | full-grammar | token-event
    grammarProfile: optional
    databaseVersion: optional

  filters:
    includeTables: []
    excludeTables: []

  sources:
    metadata: {}
    ddl: {}
    objects: {}
    logs: {}
    dataProfile: {}

  namingMatch:
    enabled: true
    systemRulesEnabled: true
    ruleFiles: []
    rules: []

  derivedPaths:
    enabled: false

  output:
    minConfidence: 0.30
    includeEvidence: true
    includeWarnings: true
    includeObservationCounts: true
```

工具侧可以增加 UI 字段，例如任务名、租户、创建人、结果保存位置；这些字段不要写入 relation-detector YAML，除非 relation-detector 已支持对应配置。

## 4. YAML 映射

### 4.1 database

MySQL把database映射到`catalog`轴：工具侧应优先填写`database.catalog`。旧
`database.schema`仍可作为兼容输入，但两者同时非空且不同会在JDBC连接前失败。PostgreSQL、
Oracle和SQL Server继续按各自catalog/schema语义传入。结果JSON顶层在catalog非空时输出可选
`database.catalog`，并始终保留legacy `database.schema`字段。

```yaml
database:
  type: MYSQL
  catalog: sample_data
  jdbcUrl: ${JDBC_URL}
  username: ${DB_USER}
  password: ${DB_PASSWORD}
  adaptorId: optional-custom-adaptor
```

`database.type` 支持：

| 类型 | 用途 |
| --- | --- |
| `MYSQL` | MySQL token-event / full-grammar |
| `POSTGRESQL` | PostgreSQL token-event / full-grammar |
| `ORACLE` | Oracle token-event / full-grammar |
| `SQLSERVER` | SQL Server token-event / full-grammar |
| `COMMON` | portable common token-event benchmark / 通用 SQL 文件扫描 |

环境变量写法 `${NAME}` 由配置加载器解析。缺失环境变量会报配置错误。

### 4.2 parser

```yaml
parser:
  mode: auto
  grammarProfile: mysql/8.0
  databaseVersion: "8.0"
```

| 字段 | 含义 |
| --- | --- |
| `mode=auto` | 默认。能选中 full-grammar profile 时使用 full-grammar，否则 token-event |
| `mode=full-grammar` | 优先使用版本化 full-grammar；profile 缺失或 hard failure 时按当前 parser 策略 fallback |
| `mode=token-event` | 强制使用宽松 token-event parser |
| `grammarProfile` | 明确指定方言版本 grammar |
| `databaseVersion` | 传入数据库版本，用于 profile 选择 |

当前建议暴露给用户的 `grammarProfile`：

| 数据库 | profiles |
| --- | --- |
| MySQL | `mysql/5.7`, `mysql/8.0` |
| PostgreSQL | `postgresql/16`, `postgresql/17`, `postgresql/18` |
| Oracle | `oracle/12c`, `oracle/19c`, `oracle/21c`, `oracle/26ai` |
| SQL Server | `sqlserver/2016`, `sqlserver/2017`, `sqlserver/2019`, `sqlserver/2022`, `sqlserver/2025` |
| Common | 无 full-grammar；使用 `mode: token-event` |

工具端应把用户选择的“数据库版本方言”直接映射到 `grammarProfile` 和 `databaseVersion`，不要在工具端自行降级或改写 SQL。

### 4.3 filters

```yaml
filters:
  includeTables:
    - orders
    - order_items
  excludeTables:
    - audit_logs
```

`includeTables` / `excludeTables` 用于限制扫描和输出候选范围。当前契约是物理表名的 exact list，adaptor 按 identifier rules 规范化大小写/引用符后精确匹配，不解释 glob 或正则。工具端应把用户选择的精确表集透传到这里，不应在读取结果后再粗暴删除 evidence，因为那会破坏 relationship / lineage / namingEvidence 的引用闭环。文件路径的 `paths + include` 是独立的 glob 契约，不应与 table filter 混用。

### 4.4 sources

relation-detector 支持 metadata、DDL、对象定义、SQL 日志/文件、数据画像五类输入。工具可以按用户选择组合使用。

#### 文件 / 文件夹输入

```yaml
sources:
  metadata:
    enabled: false

  ddl:
    enabled: true
    fromDatabase: false
    paths:
      - sample-data/mysql/8.0/01-schema
    include:
      - "**/*.sql"

  objects:
    enabled: true
    fromDatabase: false
    paths:
      - sample-data/mysql/8.0/02-procedures
    include:
      - "**/*.sql"

  logs:
    enabled: true
    format: PLAIN_SQL
    filterSystemQueries: true
    paths:
      - sample-data/mysql/8.0/03-data
      - sample-data/mysql/8.0/04-queries
    include:
      - "**/*.sql"

  dataProfile:
    enabled: false
```

`files` 适合少量显式文件；`paths + include` 适合目录扫描。`ScanInputPathResolver` 是两种入口
共用的唯一展开 owner：CLI 以配置文件目录作为 base，直接 Java API 可传显式 base directory，
无参 `resolve()` / `scan()` 以当前工作目录作为 base。missing、非普通文件和不可读输入都会在
扫描前失败。

#### 数据库连接输入

```yaml
sources:
  metadata:
    enabled: true

  ddl:
    enabled: true
    fromDatabase: true

  objects:
    enabled: true
    fromDatabase: true

  logs:
    enabled: false

  dataProfile:
    enabled: true
    timeoutSeconds: 30
    maxCandidatePairs: 1000
    maxTargetsPerSourceColumn: 3
    minContainmentRatio: 0.98
    minOverlapRatio: 0.80
    maxMismatchRatio: 0.50
    minDistinctValues: 20
    minRowsForNegative: 100
    verifyDeclaredForeignKeys: false
    discoverFromNamingEvidence: false
    skipUnindexedLargeTargets: true
```

数据画像可能访问真实数据，工具 UI 应明确提示权限和成本。默认建议关闭；只在用户明确授权并需要 `VALUE_CONTAINMENT_HIGH` / `VALUE_OVERLAP_HIGH` 这类正向数据证据，或需要用 `NEGATIVE_VALUE_MISMATCH` 验证非条件声明 FK 时开启。

### 4.5 namingMatch

```yaml
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
```

规则：

- 系统默认 direct 规则是 `TABLE_ID`、`ID_SUFFIX_TO_ID`、`SELF_ROLE_ID`。
- 用户配置规则必须使用 `rule: USER_CONFIGURED`。
- `TRANSITIVE_NAMING_PATH` 只能由 derived path 引擎生成，不能由用户配置。
- `namingEvidence` 是 `NAMING_MATCH` 的唯一证据池；relationship 只能引用 top-level `namingEvidence.id`。
- 命名证据不能单独生成 relationship，只能辅助已有结构证据定向、解释和加分。

工具端应提供三类配置能力即可覆盖第一阶段需求：

| 配置能力 | YAML 表达 |
| --- | --- |
| 列名等值 / 后缀 | `sourceColumn.equalsAny`、`sourceColumn.suffixAny`、`targetColumn.equals` |
| 表别名字典 | `targetTable.aliases` |
| 显式 endpoint pair | `sourceEndpoint` + `targetEndpoint` |

### 4.6 derivedPaths

```yaml
derivedPaths:
  enabled: true
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

含义：

| 字段 | 含义 |
| --- | --- |
| `enabled` | 是否开启传递推导 |
| `relationships` | 输出 `derivedRelationships` |
| `dataLineage` | 输出 `derivedDataLineages` |
| `namingEvidence` | 将 `TRANSITIVE_NAMING_PATH` 写入 top-level `namingEvidence`，并输出轻量 `derivedNamingEvidence` |
| `includeNamingEdgesInRelationshipPaths` | relationship 推导是否允许使用 naming 辅助边 |
| `maxPathLength` | 最大路径长度，当前建议固定 5 |
| `maxPathsPerPair=0` | 0 表示不限制同一 source/target 的路径数量 |
| `maxFacts=0` | 0 表示不限制总推导事实数 |
| `confidenceDecay` | 每跳置信度衰减 |
| `minConfidence` | 推导结果最小置信度；按未舍入的衰减置信度过滤低于阈值的 relationship、lineage 和 naming path，等于阈值时保留。过滤先于路径/事实配额，最终输出值再统一保留四位小数。 |

默认关闭。工具 UI 可以把它做成“显示间接关系 / 间接血缘”的高级开关。

### 4.7 output

```yaml
output:
  format: json
  minConfidence: 0.20
  includeEvidence: true
  includeWarnings: true
  includeObservationCounts: true
```

生产集成建议：

- `format: json`
- `includeEvidence: true`，用于审计和 semantic-layer。
- `includeWarnings: true`，用于提示 parser / 权限 / 配置问题。
- `includeObservationCounts: true`，用于调试合并后的事实由多少次原始观察支持；如果只做轻量展示可以关闭。

## 5. 完整配置示例

### 5.1 MySQL 8.0 文件扫描

```yaml
database:
  type: MYSQL
  catalog: sample_data

parser:
  mode: full-grammar
  grammarProfile: mysql/8.0
  databaseVersion: "8.0"

filters:
  excludeTables:
    - audit_logs

sources:
  metadata:
    enabled: false
  ddl:
    enabled: true
    fromDatabase: false
    paths:
      - relation-detector/sample-data/mysql/8.0/01-schema
    include:
      - "**/*.sql"
  objects:
    enabled: true
    fromDatabase: false
    paths:
      - relation-detector/sample-data/mysql/8.0/02-procedures
    include:
      - "**/*.sql"
  logs:
    enabled: true
    format: PLAIN_SQL
    filterSystemQueries: true
    paths:
      - relation-detector/sample-data/mysql/8.0/03-data
      - relation-detector/sample-data/mysql/8.0/04-queries
    include:
      - "**/*.sql"
  dataProfile:
    enabled: false

namingMatch:
  enabled: true
  systemRulesEnabled: true

derivedPaths:
  enabled: true
  relationships: true
  dataLineage: true
  namingEvidence: true
  includeNamingEdgesInRelationshipPaths: true
  maxPathLength: 5
  maxPathsPerPair: 0
  maxFacts: 0
  confidenceDecay: 0.75
  minConfidence: 0.10

output:
  format: json
  minConfidence: 0.0
  includeEvidence: true
  includeWarnings: true
  includeObservationCounts: true
```

调用：

```bash
relation-detector/scripts/run-cli.sh scan \
  --config /path/to/mysql-8-relation-detection.yml \
  --format json \
  --output /path/to/mysql-8-relation-detection-result.json
```

### 5.2 数据库连接扫描

```yaml
database:
  type: POSTGRESQL
  schema: public
  jdbcUrl: ${JDBC_URL}
  username: ${DB_USER}
  password: ${DB_PASSWORD}

parser:
  mode: auto
  grammarProfile: postgresql/18
  databaseVersion: "18"

filters:
  includeTables:
    - sales_orders
    - sales_order_items
    - customers

sources:
  metadata:
    enabled: true
  ddl:
    enabled: true
    fromDatabase: true
  objects:
    enabled: true
    fromDatabase: true
  logs:
    enabled: false
  dataProfile:
    enabled: false

output:
  format: json
  minConfidence: 0.30
  includeEvidence: true
  includeWarnings: true
  includeObservationCounts: true
```

## 6. 结果 JSON 如何使用

relation-detector JSON 顶层结构：

```json
{
  "database": {
    "type": "MYSQL",
    "catalog": "sample_data",
    "schema": ""
  },
  "generatedAt": "...",
  "summary": {
    "directRelationshipCount": 10,
    "derivedRelationshipCount": 4,
    "totalRelationshipCount": 14,
    "directDataLineageCount": 8,
    "derivedDataLineageCount": 3,
    "totalDataLineageCount": 11,
    "directNamingEvidenceCount": 20,
    "derivedNamingEvidenceCount": 5,
    "totalNamingEvidenceCount": 25,
    "warningCount": 0,
    "sources": ["ddl", "object-files", "logs"]
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

### 6.1 relationships

用途：展示物理表/字段间关系，例如 FK-like、join 推断关系、共现关系。

核心字段：

| 字段 | 用法 |
| --- | --- |
| `source` / `target` | 关系端点。FK-like 方向保持 `child/dependent -> parent/referenced` |
| `relationType` | `FK_LIKE` 或 `CO_OCCURRENCE` |
| `relationSubType` | 更细的关系来源，例如 DDL FK、SQL join、profile supported |
| `confidence` | 当前关系置信度 |
| `evidence` | grouped evidence，适合展示“为什么有这条关系” |
| `rawEvidence` | 原始 observation，适合审计定位来源 SQL/DDL |
| `warnings` | 单条关系相关 warning |

如果 relationship evidence 里出现 `type=NAMING_MATCH`，它应包含 `evidenceRef`，指向 top-level `namingEvidence[].id`。工具展示时不要把 relationship 内的 `NAMING_MATCH` 当作独立规则执行结果，它只是引用。

### 6.2 dataLineages

用途：展示字段级数据流向和写入来源。

核心字段：

| 字段 | 用法 |
| --- | --- |
| `sources[]` | 一个或多个来源字段 |
| `target` | 写入目标字段 |
| `flowKind` | `VALUE` 表示值来源；`CONTROL` 表示过滤、定位、分组等控制来源 |
| `transformType` | `DIRECT`、`AGGREGATE`、`CASE_WHEN`、`ARITHMETIC` 等 |
| `confidence` | 当前 lineage 置信度 |
| `attributes` | source object、statement id、写入上下文等扩展信息 |
| `evidence` / `rawEvidence` | grouped / raw 双层证据 |

工具 UI 建议把 `VALUE` 和 `CONTROL` 分开展示。`CONTROL` 不表示值被复制过去，而是该字段参与了结果形成条件。

### 6.3 namingEvidence

用途：展示命名规则命中的证据池。它不是 relationship fact。

核心字段：

| 字段 | 用法 |
| --- | --- |
| `id` | 稳定 evidence id，relationship 通过 `evidenceRef` 引用 |
| `source` / `target` | 命名提示方向 |
| `rule` | `TABLE_ID`、`ID_SUFFIX_TO_ID`、`SELF_ROLE_ID`、`USER_CONFIGURED`、`TRANSITIVE_NAMING_PATH` |
| `directionHint` | 是否可作为方向提示 |
| `evidence` / `rawEvidence` | grouped / raw observations |

`derivedNamingEvidence` 是轻量索引，只用于快速查看 `TRANSITIVE_NAMING_PATH`；完整证据仍在 `namingEvidence` 中。

### 6.4 derivedRelationships / derivedDataLineages

用途：展示开启 `derivedPaths` 后推导出的间接关系和间接血缘。

使用规则：

- 不覆盖 direct facts。
- UI 应标注为“推导 / 间接”，不要和直接 SQL/DDL/metadata 证据混为一类。
- 展示 `path`、`pathLength`、`attributes.traversalPath`，让用户能解释推导链路。
- 对图谱构建而言，可作为辅助边或待审核候选边。

### 6.5 warnings

用途：展示配置、权限、parser、profile、ambiguous relation 等非致命问题。

工具端至少应展示：

- `code`
- `severity`
- `message`
- `source` / `statementId` / attributes，如果存在

有 warning 不一定代表任务失败，但会影响用户对结果完整性的判断。

## 7. 如何传给 semantic-layer

relation-detector JSON 是 semantic-layer 的事实输入。生成 KG：

```bash
java -jar semantic-layer/semantic-cli/target/relation-detector-semantic-cli-0.1.0-SNAPSHOT.jar \
  semantic e2e \
  --input /path/to/relation-detector-result.json \
  --output /path/to/semantic-output \
  --name my-scan
```

输出：

```text
/path/to/semantic-output/semantic-kg/my-scan/semantic-kg.json
/path/to/semantic-output/semantic-extraction/my-scan/semantic-extraction-evidence-bundle.json
```

需要 LLM 辅助语义抽取时，先生成 evidence bundle / prompt：

```bash
java -jar semantic-layer/semantic-cli/target/relation-detector-semantic-cli-0.1.0-SNAPSHOT.jar \
  semantic extract \
  --config semantic-layer/examples/semantic-extraction-codex-session.yml
```

生产 API 模式使用 `openai-api` provider；开发和人工验证可以使用 `codex-session` provider。semantic-layer 可以改写业务可读名称，但不能改写 relation-detector 的 physical relationship / lineage / naming evidence 事实。

## 8. 工具端展示建议

推荐把结果拆成四个视图：

| 视图 | 数据来源 | 展示重点 |
| --- | --- | --- |
| 关系图 | `relationships` + `derivedRelationships` | FK-like、CO、直接/间接、confidence、evidence |
| 字段血缘 | `dataLineages` + `derivedDataLineages` | VALUE/CONTROL、transform、source object |
| 命名证据 | `namingEvidence` + `derivedNamingEvidence` | 系统规则、用户规则、transitive naming、evidenceRef |
| 问题列表 | `warnings` | parser/config/profile/profile warning |

默认结果列表建议只显示 direct facts；derived facts 放在“显示推导结果”开关下。

## 9. 错误处理

CLI enum 来自 contracts `ErrorCode`，single-scan 和 batch 已按下表稳定映射：

| 退出码 | 含义 | 工具端处理 |
| --- | --- | --- |
| `0` | 成功 | 读取 JSON |
| `1` | 配置文件不可读 | 检查配置路径和权限 |
| `2` | 配置格式错误 | 展示配置校验错误 |
| `3` | CLI 参数错误 | 检查命令构造 |
| `4` | adaptor 错误 | 检查 database.type / plugin / adaptor |
| `5` | 输入文件错误 | 检查 DDL/object/log 路径和 include 结果 |
| `10` | 数据库连接错误 | 检查连接配置和网络/认证 |
| `11` | 扫描运行错误 | 展示 stderr，保留配置和日志供排查 |
| `12` | 输出写入错误 | 检查输出目录和权限 |
| `13` | batch 部分失败 | 读取 batch report，定位失败 job |

非法 option value 位于顶层 catch boundary 内并返回 `3`。stderr 只包含固定脱敏消息；工具端可依赖
退出码分类，但仍应把未知非零值视为 CLI failure，并保留版本和运行清单供排查。

即使退出码为 `0`，也应读取 JSON 顶层 `warnings` 并提示用户。

## 10. 集成约束

- 工具端必须把用户过滤规则、数据库版本方言、命名规则、derived 开关作为结构化配置透传，不要拼到自然语言提示词里。
- 工具端不要在 relation-detector 输出后自行创造或删除 evidence；如果需要过滤展示，应保留原始 JSON。
- `schema.table.column` 与 `table.column` 默认不是同一个 endpoint；工具端不要自动合并 schema-qualified 和裸表名。
- `COMMON` 是可运行 parser category，但没有 full-grammar；它用于 portable SQL，不代表某个真实数据库 adaptor。
- Oracle full-grammar 当前仍按文档声明为 scoped/versioned coverage，不应在 UI 中宣传为完整官方 Oracle grammar。
- SQL Server / Oracle / MySQL / PostgreSQL 的版本差异由 `grammarProfile` 和 `databaseVersion` 表达；用户没有选择版本时优先使用 `parser.mode: auto`。
