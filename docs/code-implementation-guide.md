# 代码实现说明与运维测试指南

本文档面向后续实施、维护、运维和测试人员，解释当前代码如何对应设计文档，以及如何运行示例、投喂数据、理解输出和设计测试用例。

## 1. 当前实现状态

当前代码已经落成 Java 17 + Maven 多模块工程：

```text
relation-detector/
  pom.xml
  relation-adaptor-api/
  relation-core/
  relation-cli/
  adaptor-mysql/
  adaptor-postgres/
  test-fixtures/examples/
```

已实现能力：

- 稳定 enum 和 adaptor API。
- Java SPI adaptor 发现。
- MySQL/PostgreSQL 内置 adaptor。
- DDL 外键解析，包括 inline references、ALTER TABLE FK、复合 FK、quoted schema-qualified 名称，以及 PK/unique/index 辅助 evidence。
- 纯 SQL 文本、MySQL 日志、PostgreSQL statement log 的 SQL 抽取。
- 简易 JOIN、IN 子查询、表共现解析。
- 关系证据合并和置信度计算。
- JSON/table 输出。
- file-only 示例配置和输入数据。

第一版故意保持低外部依赖，目前没有引入 picocli、Jackson、JSqlParser、JUnit、Testcontainers。设计文档中这些仍是推荐演进方向；当前代码先保证结构清楚、可编译、可运行，后续可以按模块替换实现。

## 2. 模块说明

### 2.1 relation-adaptor-api

核心文件：

- `Enums.java`
- `DatabaseAdaptor.java`
- `Collectors.java`
- `RelationshipCandidate.java`
- `DataLineageCandidate.java`
- `DataLineageEvidence.java`
- `Evidence.java`
- `TableId.java`
- `ColumnRef.java`
- `Endpoint.java`

职责：

- 定义第三方数据库 adaptor 必须遵守的接口。
- 定义 JSON 输出和内部模型使用的 enum。
- 定义 evidence、warning、metadata snapshot、SQL statement 等跨模块类型。

设计对应：

- `docs/design/phase-02-core-model-scoring.md`
- `docs/design/phase-03-adaptor-api-spi.md`
- `docs/design/enum-reference.md`

维护注意：

- enum 的 JSON 字符串不能随意改名。
- `relation-adaptor-api` 不应依赖 `relation-core`，否则第三方 adaptor 会被迫依赖核心实现。
- 新增数据库 adaptor 时，应只依赖该 API 和必要的工具库。

### 2.2 relation-core

核心文件：

- `ScanEngine.java`
- `relation/RelationshipMerger.java`
- `lineage/TokenEventDataLineageExtractor.java`
- `lineage/DataLineageMerger.java`
- `ConfidenceCalculator.java`
- `DiagnosticWarnings.java`
- `AntlrSqlParseSupport.java`
- `tokenevent/TokenEventStructuredDdlParser.java`
- `tokenevent/TokenEventStructuredSqlParser.java`
- `relation/TokenEventRelationExtractor.java`
- `lineage/SqlLineageResolver.java`
- `parser/DdlRelationParserRunner.java`
- `parser/SqlRelationParserRunner.java`
- `JsonResultWriter.java`
- `TableResultWriter.java`

职责：

- 编排扫描流程。
- 调用 adaptor 的 metadata、DDL、object、log、profile 钩子。
- 合并多个来源产生的关系候选。
- 计算最终 confidence。
- 输出 JSON/table。

设计对应：

- `docs/design/phase-02-core-model-scoring.md`
- `docs/design/phase-06-parser-enhancement.md`
- `docs/design/phase-08-output-ux.md`

维护注意：

- `ConfidenceCalculator` 是置信度公式的唯一默认实现。
- `RelationshipMerger` 负责 `relationSubType` 主导证据优先级。
- SQL parser 由 `SqlRelationParserRunner` 统一调度。运行模式是 `parser.mode: auto|full-grammer|token-event`：profile/version/JDBC metadata 足够时可使用版本化 full-grammer；无法选择 profile 或版本不支持时 fallback 到 adaptor 暴露的 token-event SQL parser。profile 已选中后的 parse warning / partial events 属于 full-grammer 结果；未捕获解析异常由 `ScanEngine` 记录 warning，并继续扫描其它语句，不在 event 层混入 token-event 补齐。`parser.sql.mode`、`simple`、`antlr-shadow` 和 simple fallback 已移除。
- DDL parser 由 `DdlRelationParserRunner` 统一调度，并使用同一 `parser.mode` 选择策略。`parser.ddl.mode`、`simple-ddl`、`antlr-ddl-shadow` 和 simple DDL fallback 已移除；DDL 硬失败记录 warning，并继续处理其它 DDL source。
- `AntlrSqlParseSupport` 是 ANTLR parse support 的通用骨架，负责动态 SQL warning、统一 diagnostics attributes 和 `StructuredParseResult`。
- `RelationSql.g4` 是 core tolerant grammar；MySQL/PostgreSQL 已拆出 `MySqlRelationSql.g4` / `PostgresRelationSql.g4`，由 adaptor 子包里的 `com.relationdetector.mysql.tokenevent.MySqlTokenEventStructuredSqlParser` / `com.relationdetector.postgres.tokenevent.PostgresTokenEventStructuredSqlParser` 分别调用自己的 generated lexer/parser。
- ANTLR 只负责把文本变成结构化 token/parse 事件，不负责直接判定业务关系。DDL-only 能力必须进入 DDL entry rule、`DdlStructuredEventVisitor`、`DdlRelationExtractionVisitor` 或 `DdlRelationParserRunner`；SQL-only 能力必须进入 SQL entry rule、`TokenEventSqlEventBuilder`、`TokenEventRelationExtractor` 或 `SqlRelationParserRunner`。不要把二者回流成一个全局万能 visitor。
- SQL 与 DDL correctness fixture golden 仍保护正式输出。默认无 profile/version 的测试路径走 token-event；full-grammer shadow/selection 测试证明版本化 profile 不少于 token-event gold。SQL fixture 证明 SQL relation extractor 行为，DDL fixture 证明 DDL event visitor 与 DDL relation extraction 行为；禁止用其中一条链路的通过结果替代另一条链路验收。
- 新增方言差异时，优先进入对应数据库自己的 token-event parser 或 event builder，例如 `mysql.tokenevent.MySqlTokenEventStructuredSqlParser`、`postgres.tokenevent.PostgresTokenEventStructuredSqlParser`、`MySqlTokenEventSqlEventBuilder`、`PostgresTokenEventSqlEventBuilder`、`MySqlDdlStructuredEventVisitor`、`PostgresDdlStructuredEventVisitor`、`mysql.tokenevent.MySqlTokenEventStructuredDdlParser`、`postgres.tokenevent.PostgresTokenEventStructuredDdlParser`。只有跨数据库稳定且语义相同的逻辑，才放入 core。
- 不要混淆三类 mode：`parser.mode` 是系统运行模式，只允许 `auto|full-grammer|token-event`；MySQL `SQL_MODE` 是 MySQL full-grammer runtime 的语法开关，由 `MySqlGrammarSqlMode` / `MySqlGrammarSqlModes` 表达；ANTLR lexer mode 是 `.g4` 内部词法状态，例如 PostgreSQL 字符串或 meta-command 状态，不对应 Java parser mode 类。
- `TokenEventSqlEventBuilder` 从 ANTLR token stream 产出 `ROWSET_REFERENCE`、`PREDICATE_EQUALITY`、`JOIN_USING_COLUMNS`、`EXISTS_PREDICATE`、`IN_SUBQUERY_PREDICATE`、写入映射和 projection 等结构化事件；MySQL/PostgreSQL 已拆出 `MySqlTokenEventSqlEventBuilder` / `PostgresTokenEventSqlEventBuilder`，用于隔离 quoted identifier 和方言 rowset 规则。`TABLE_REFERENCE` / `COLUMN_EQUALITY` 仍作为 legacy/bootstrap event 存在，但当前 relation extractor 的主输入是归一后的 rowset/predicate 事件。
- `TokenEventSqlEventBuilder` 区分 DML `USING table` 与 `JOIN USING (columns)`：前者可以产生 rowset，后者只能基于 `USING` 列名生成经过审核的弱共现证据，不能把列名当作 `ROWSET_REFERENCE`。
- `TokenEventStructuredSqlParser` / `TokenEventSqlEventBuilder` 是 MySQL/PostgreSQL SQL 的 token-event 结构事件层，也是无 profile、unsupported version 或显式 `parser.mode=token-event` 时的正式 fallback。公共 relation、rowset/scope、DML 深水区、Data Lineage 写入映射和 derived aggregate projection 已经迁入 token-event 事件和抽取测试，覆盖 `JOIN USING`、raw equality、correlated `EXISTS`、scalar/tuple `IN`、列级弱共现、CTE/temp/trigger scope、MySQL multi-table `DELETE`、PostgreSQL `UPDATE FROM`、`UPDATE SET`、derived aggregate、`INSERT SELECT`、`MERGE`；新增 MySQL/PostgreSQL token-event 专属规则必须进入 `MySqlTokenEventSqlEventBuilder` / `PostgresTokenEventSqlEventBuilder` 或对应 token-event parser，不得放回公共万能层。
- `SqlGrammarProfile` / `SqlGrammarProfileRegistry` / `FullGrammerDialectModule` 是版本化 full-grammer 接入点。当前注册 `mysql-8.0` 与 `postgresql-16` module；人工配置 `parser.grammarProfile` 优先，其次可用 `parser.databaseVersion` 或 JDBC `DatabaseMetaData` 选择 profile。同一 major 的 minor 默认复用该 major profile；如果请求版本只比最高已支持版本高 1 个 major，可以临时选择最近低版本 profile 并返回 diagnostic；超过 1 个 major 或没有方言/版本信息时，回退 token-event parser。
- `FullGrammerTokenEventStructuredSqlParser` / `FullGrammerTokenEventParserFactory` / `FullGrammerTokenEventShadowComparator` 是版本化 full-grammer 接入和 parity 基础设施。`mysql-8.0` 与 `postgresql-16` 已接入 vendored grammars-v4 full-grammer，具体实现分别位于 `adaptor-mysql` 的 `com.relationdetector.mysql.fullgrammer.v8_0` 和 `adaptor-postgres` 的 `com.relationdetector.postgres.fullgrammer.v16`。版本由 package 表达，类名保持无版本数字；core factory 只通过 `ServiceLoader<FullGrammerDialectModule>` 查找 module，不按 `profile.id()` switch 分发，也不直接 import adaptor 类。新增大版本时注册新的 adaptor module 和 fixture。full-grammer parser 调用真实 entry rule 后进入 parse-tree visitor；relationship、lineage、confidence、JSON schema 仍由现有 semantic extractor/merger 决定。
- `FullGrammerCorrectnessShadowTest` 扫描全部 SQL correctness fixture，验证 full-grammer profile 不少于 token-event fallback 输出。`FullGrammerGeneratedParserSmokeTest` 验证 MySQL/PostgreSQL full-grammer generated lexer/parser 可实例化并解析基础 SQL。`FullGrammerNativeRelationEventsTest` 还要求 `fullGrammerBridgedEventTypes=[]`，防止重新引入 token-event scanner bridge；后续 profile 深化 typed parse-tree visitor 后，missing 必须修 visitor，extra 进入审核，不自动写入 golden。
- `TokenEventRelationExtractor` 从 `ROWSET_REFERENCE` / `PREDICATE_EQUALITY` / `JOIN_USING_COLUMNS` / `EXISTS_PREDICATE` / `IN_SUBQUERY_PREDICATE` / `TUPLE_IN_SUBQUERY_PREDICATE` 等事件独立构造 FK-like/CO_OCCURRENCE 候选，并复用 `SqlLineageResolver` 做保守 CTE/派生表列回溯。公共 extractor 只保留跨方言关系语义；MySQL/PostgreSQL 专属 rowset keyword、rowset modifier、CTE modifier 和多表 DML rowset 识别必须通过 `MySqlTokenEventSqlEventBuilder` / `PostgresTokenEventSqlEventBuilder` 实现。明确列等值但方向不可靠时输出 `SQL_LOG_COLUMN_CO_OCCURRENCE`；同一物理表 self-join 只有在不同 SQL alias 且不同物理列时才输出列级弱共现，同 alias 行内比较不输出。没有列端点时才输出表级 `SQL_LOG_TABLE_CO_OCCURRENCE`。PostgreSQL `ONLY`、`TABLESAMPLE`、`ROWS FROM`、`JOIN USING (...) AS alias` 这类语法必须配套 MySQL 负向测试，防止被 MySQL 当作物理表或共现证据；MySQL `STRAIGHT_JOIN`、ODBC `{ OJ ... }`、optimizer hints、`PARTITION (...)`、`JSON_TABLE(...)`、multi-table `UPDATE/DELETE` 这类语法也必须配套 Postgres 负向测试，防止被 PostgreSQL 误抽为关系。
- `correlated EXISTS` 是公共 SQL 关系语义，新增或维护这类能力时，公共层只能处理跨方言相关谓词抽取，例如 `WHERE EXISTS (SELECT 1 FROM users u WHERE u.id = o.user_id)` 生成 `SQL_LOG_EXISTS`。EXISTS 子查询内部如果出现 MySQL/PostgreSQL 专属 rowset、function table、hint、`ONLY`、`JSON_TABLE` 等语法，必须下沉到对应方言 visitor，并配套反向负向测试。
- 同一 correlated EXISTS predicate 不能同时计为 `SQL_LOG_EXISTS` 和普通 `SQL_LOG_JOIN`。`SQL_LOG_EXISTS` 当前分值为 `0.58`，普通 SQL log join 为 `0.55`；当同一 endpoint pair 已由 EXISTS 识别时，移除重复 JOIN candidate 是反重复计分保护，避免单个 SQL 谓词虚高置信度。若未来要把“同一 SQL 中 EXISTS 与外层 JOIN 独立出现”计为两次观察，必须先增加 predicate span/source-location provenance，不能简单取消去重。
- `TokenEventRelationExtractor` 消费结构事件并负责跨方言关系语义，不应重新承载数据库专属 rowset scanner。新增或修改方言 rowset/DDL 兼容逻辑必须进入对应 token-event event builder 或 DDL event visitor，并同时补 correctness fixture、更新 `docs/design/phase-06-parser-enhancement.md`；未来如果改成完整 parse-tree visitor，也必须同步删除对应过渡说明。
- `SqlLogNoiseFilter` 在 `SqlRelationParserRunner` 之前过滤 native log 噪声。默认按数据库类型过滤系统 catalog 查询，并允许 YAML 通过 `sources.logs.filterSystemQueries`、`sources.logs.systemSchemas`、`sources.logs.metadataQueryMarkers` 覆盖。
- `TokenEventStructuredDdlParser` 现在按 dialect 选择 `DdlStructuredEventVisitor` 子类并输出 `DDL_FOREIGN_KEY` / `DDL_INDEX` 事件；`DdlRelationExtractionVisitor` 独立把这些事件转换成 DDL FK-like 关系，并用 source index / target unique evidence 增强关系。DDL 通用文本切分、括号匹配、identifier 读取和 index part 判断属于 `DdlTokenCursor` / `DdlIndexPartParser` / `DdlStatementView`；DDL 方言 regex 属于 `MySqlDdlStructuredEventVisitor` / `PostgresDdlStructuredEventVisitor` 或 adaptor DDL parser，不属于 `DdlRelationExtractionVisitor`。
- `SqlLineageResolver` 为 relationship extractor 和 Data Lineage extractor 提供保守列来源映射，支持 CTE、派生表、多层嵌套查询、PostgreSQL data-modifying CTE `DELETE ... RETURNING`，以及简单 LATERAL/correlated derived table 中的外层列投影回溯。它消费结构事件，不重新充当 SQL parser。裸列投影（例如 `SELECT user_id FROM orders`）只有在事件作用域和 fixture 覆盖证明安全时才回溯；复杂表达式的完整多源字段血缘由 `TokenEventDataLineageExtractor` 输出。新增类似能力必须先说明方言和 SQL 形态边界，并配 correctness fixture。
- `TokenEventDataLineageExtractor` 是正式 Data Lineage v1 输出链路，独立于 `TokenEventRelationExtractor`。它从 token-event 结构事件中抽取 `UPDATE SET`、`INSERT INTO ... SELECT` 和基础 `MERGE UPDATE/INSERT` 的 `table.column -> table.column` 字段血缘，跳过参数、JSON path、字面量和局部变量。`DataLineageCandidate.confidence` 只解释血缘可信度，不参与 relationship confidence。
- `DataLineageMerger` 只按 `sources + target + flowKind + transformType` 去重字段血缘；不要把它接入 `RelationshipMerger`。
- `mysql.tokenevent.MySqlTokenEventStructuredDdlParser` / `postgres.tokenevent.PostgresTokenEventStructuredDdlParser` 是 adaptor DDL 入口，内部共享 token-event DDL event pipeline。数据库私有 DDL 写法应进入对应 `DdlStructuredEventVisitor` 子类或 adaptor DDL parser，不应回流为一个跨库万能 parser。
- `JsonResultWriter` 当前手写 JSON，后续可替换为 Jackson，但字段结构应保持兼容。
- `DiagnosticWarnings` 集中构造解析/提取失败 warning。`ScanEngine`、DDL parser、log extractor 不应各自拼装不同格式；失败时应保留 `exceptionClass`，并在能拿到输入文本时把原始 SQL/DDL 放入 `attributes.rawStatement`。full-grammer profile 选不中时由 parser selection 层 fallback 到 token-event；full-grammer 或 token-event 已选中后的硬失败只影响当前 statement/source，不回退旧 parser，也不混合另一条 parser 的事件。

### 2.3 relation-cli

核心文件：

- `Main.java`
- `SimpleYamlConfigLoader.java`
- `AdaptorRegistry.java`

职责：

- 解析命令行参数。
- 读取 YAML 配置。
- 通过 Java SPI 加载 adaptor。
- 调用 `ScanEngine`。
- 将结果写到 stdout 或文件。

设计对应：

- `docs/design/phase-01-project-skeleton.md`
- `docs/design/phase-03-adaptor-api-spi.md`
- `docs/design/phase-08-output-ux.md`

维护注意：

- `SimpleYamlConfigLoader` 只支持示例配置所需的 YAML 子集。
- 生产化时建议替换为 Jackson YAML。
- `AdaptorRegistry` 同时支持 classpath 内置 adaptor 和 `--plugin-dir` 外部 jar。

### 2.4 adaptor-mysql

核心文件：

- `MySqlDatabaseAdaptor.java`
- `META-INF/services/com.relationdetector.api.DatabaseAdaptor`

职责：

- 通过 `information_schema.KEY_COLUMN_USAGE` 读取显式 FK。
- 读取 MySQL routine、view、trigger 定义。
- 从 MySQL general/slow log 中抽取 SQL。
- 提供 MySQL 数据画像钩子。
- 通过 SPI 注册为 `mysql` adaptor。

设计对应：

- `docs/design/phase-04-mysql-adaptor.md`

维护注意：

- 当前元数据采集先实现显式 FK，unique/index 采集可以按 Phase 4 继续补。
- 当前数据画像只做轻量匹配 evidence，后续应补包含率、重合率、负向证据等完整指标。
- MySQL adaptor 已声明 `mysql-connector-j` runtime 依赖；使用 Maven 构建出的运行 classpath 连接真实 MySQL 时会自动包含 Connector/J。

### 2.5 adaptor-postgres

核心文件：

- `PostgresDatabaseAdaptor.java`
- `META-INF/services/com.relationdetector.api.DatabaseAdaptor`

职责：

- 通过 `pg_catalog.pg_constraint` 读取显式 FK。
- 读取 PostgreSQL function/procedure/view 定义。
- 从 PostgreSQL statement log 中抽取 SQL。
- 提供 PostgreSQL 数据画像钩子。
- 通过 SPI 注册为 `postgresql` adaptor。

设计对应：

- `docs/design/phase-05-postgres-adaptor.md`

维护注意：

- 当前触发器关联 trigger function 的逻辑还可以继续增强。
- PostgreSQL 的 quoted identifier 规则已经在 `IdentifierRules` 中预留。
- PostgreSQL JDBC driver 没有内置到项目中，连接真实 PostgreSQL 时需要运行环境提供驱动。

## 3. 关键执行流程

一次扫描的主链路：

```text
Main
  -> SimpleYamlConfigLoader
  -> AdaptorRegistry
  -> ScanEngine
  -> adaptor metadata/object/ddl/log/profile hooks
  -> RelationshipMerger
  -> ConfidenceCalculator
  -> JsonResultWriter 或 TableResultWriter
```

关系从多个来源进入：

```text
DDL FK               -> DDL_FOREIGN_KEY
DB metadata FK       -> METADATA_FOREIGN_KEY
VIEW/PROCEDURE JOIN  -> VIEW_JOIN / PROCEDURE_JOIN
SQL log JOIN         -> SQL_LOG_JOIN
SQL log co-occurrence-> SQL_LOG_TABLE_CO_OCCURRENCE
data profile         -> VALUE_OVERLAP_HIGH / VALUE_CONTAINMENT_HIGH
```

最终每条关系都会包含：

- source table/column。
- target table/column。
- relationType。
- relationSubType。
- confidence。
- rawEvidence list：归并前的完整证据审计轨迹，每一次日志、对象、DDL 或画像命中都保留。
- evidence list：归并后的摘要证据，用于 confidence 计算；重复证据会带 `count`、`sampleDetails`，并通过 `REPEATED_OBSERVATION` 表示最多 0.10 的递减增益。
- warning list。

扫描级 warning 会进入顶层 `warnings`：

```json
{
  "type": "PARSE_WARNING",
  "severity": "WARN",
  "code": "SQL_PARSE_FAILED",
  "message": "synthetic sql failure",
  "source": "procedures.sql",
  "line": 1,
  "attributes": {
    "statementSourceType": "PROCEDURE",
    "endLine": 3,
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

诊断规则：

- DDL parser 抛异常或 DDL 文件读取失败：`DDL_PARSE_FAILED`。
- SQL parser 处理 procedure/function/view/trigger/log/plain SQL 时抛异常：`SQL_PARSE_FAILED`。
- 普通 SQL/object 文件读取或切分失败：`SQL_FILE_EXTRACT_FAILED`。
- 数据库原生日志读取或抽取失败：`LOG_EXTRACT_FAILED`。
- MySQL/PostgreSQL 对象定义读取失败：对应 `MYSQL_*_COLLECT_FAILED` 或 `POSTGRES_*_COLLECT_FAILED`。
- database object 进入 SQL parser 后产生的 warning 会把 `objectSchema/objectName/objectType` 放在 attributes 顶层；procedure/function 还会额外带 `routineSchema/routineName/routineType`，方便直接定位 routine。
- parser 返回空关系不是失败；例如没有 JOIN/IN/EXISTS 的过滤 SQL 可以没有 warning。

## 4. 运维示例：从构建到输出

本章给出足够细的 file-only 示例，不需要真实数据库。它适合本地验证、运维演示和黑盒测试。

### 4.1 构建项目

```bash
mvn test
```

预期：

```text
BUILD SUCCESS
```

### 4.2 示例输入文件

DDL 文件：

```text
test-fixtures/examples/schema.sql
```

SQL 日志文件：

```text
test-fixtures/examples/app-sql.sql
```

配置文件：

```text
test-fixtures/examples/file-only-config.yml
```

该配置关闭 JDBC 元数据，开启 DDL 和纯 SQL 文本日志，因此可以在没有数据库的环境里运行。

### 4.3 运行 JSON 输出

当前第一版没有打包 fat jar，直接用模块 classpath 运行：

```bash
java -cp "relation-cli/target/classes:relation-core/target/classes:relation-adaptor-api/target/classes:adaptor-mysql/target/classes:adaptor-postgres/target/classes" \
  com.relationdetector.cli.Main \
  scan \
  --config test-fixtures/examples/file-only-config.yml \
  --format json
```

预期输出应包含：

```json
{
  "relationships": [
    {
      "source": { "table": "orders", "column": "user_id" },
      "target": { "table": "users", "column": "id" },
      "relationType": "FK_LIKE",
      "relationSubType": "DDL_DECLARED_FK",
      "confidence": 0.9550
    },
    {
      "source": { "table": "users", "column": null },
      "target": { "table": "audit_logs", "column": null },
      "relationType": "CO_OCCURRENCE",
      "relationSubType": "TABLE_CO_OCCURRENCE",
      "confidence": 0.2500
    }
  ]
}
```

说明：

- `orders.user_id -> users.id` 同时来自 DDL FK 和 SQL JOIN，所以 confidence 高于单独 DDL FK。
- `users -> audit_logs` 只有共现证据，因此是表级弱关系。

### 4.4 运行 table 输出

```bash
java -cp "relation-cli/target/classes:relation-core/target/classes:relation-adaptor-api/target/classes:adaptor-mysql/target/classes:adaptor-postgres/target/classes" \
  com.relationdetector.cli.Main \
  scan \
  --config test-fixtures/examples/file-only-config.yml \
  --format table
```

预期输出形态：

```text
SOURCE                    TARGET                    TYPE            SUBTYPE                  CONF   EVIDENCE
orders.user_id            users.id                  FK_LIKE         DDL_DECLARED_FK          0.9550 DDL_FOREIGN_KEY,SQL_LOG_JOIN
users                     audit_logs                CO_OCCURRENCE   TABLE_CO_OCCURRENCE      0.2500 SQL_LOG_TABLE_CO_OCCURRENCE

Warnings: 0
```

### 4.5 写入输出文件

```bash
java -cp "relation-cli/target/classes:relation-core/target/classes:relation-adaptor-api/target/classes:adaptor-mysql/target/classes:adaptor-postgres/target/classes" \
  com.relationdetector.cli.Main \
  scan \
  --config test-fixtures/examples/file-only-config.yml \
  --format json \
  --output target/example-result.json
```

检查：

```bash
cat target/example-result.json
```

### 4.6 连接真实数据库的配置示例

MySQL：

```yaml
database:
  type: mysql
  jdbcUrl: jdbc:mysql://localhost:3306/shop
  username: readonly
  password: ${DB_PASSWORD}
  schema: shop

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
```

PostgreSQL：

```yaml
database:
  type: postgresql
  jdbcUrl: jdbc:postgresql://localhost:5432/shop
  username: readonly
  password: ${DB_PASSWORD}
  schema: public

sources:
  metadata:
    enabled: true
  objects:
    enabled: true
    fromDatabase: true
  dataProfile:
    enabled: false
```

注意：

- MySQL adaptor 已内置 Connector/J runtime 依赖。PostgreSQL 真实数据库运行时仍需要把 PostgreSQL JDBC driver 放到 classpath，或在 `adaptor-postgres` 中补 runtime dependency。
- MySQL `sources.ddl.fromDatabase: true` 会对 scope 内表执行 `SHOW CREATE TABLE`，解析出的外键/索引 evidence 使用 `DATABASE_DDL`，不同于用户提供的 DDL 文件 `DDL_FILE`。
- 生产环境建议默认关闭 `dataProfile`。
- 如果 `${DB_PASSWORD}` 环境变量不存在，CLI 会失败并提示缺少环境变量。

## 5. 可测性设计

本章用于指导后续测试开发和系统测试。

### 5.1 白盒单元测试

建议优先覆盖 core，因为 core 决定输出稳定性。

核心测试对象：

- `ConfidenceCalculator`
- `RelationshipMerger`
- `AntlrSqlParseSupport`
- `TokenEventStructuredSqlParser`
- `TokenEventSqlEventBuilder`
- `TokenEventStructuredDdlParser`
- `TokenEventRelationExtractor`
- `TokenEventDataLineageExtractor`
- `DdlRelationExtractionVisitor`
- `SimpleYamlConfigLoader`
- `AdaptorRegistry`

建议用例：

- 单个 `METADATA_FOREIGN_KEY` 输出 0.98。
- `DDL_FOREIGN_KEY + SQL_LOG_JOIN` 合并后高于 0.90。
- `NEGATIVE_VALUE_MISMATCH` 会降低分数。
- 多 evidence 下 `DECLARED_FK` subtype 不被覆盖。
- JOIN `orders.user_id = users.id` 输出列级 `FK_LIKE`。
- `FROM users, audit_logs` 无连接条件时输出 `CO_OCCURRENCE`。
- `IN (SELECT ...)` 输出 `SUBQUERY_INFERRED_FK`。
- 方言复杂 SQL 矩阵：
  - MySQL backtick、multi-table `UPDATE`、multi-table `DELETE ... LEFT JOIN`、derived table column alias、recursive CTE。
  - PostgreSQL quoted identifier、多层 CTE、`WITH RECURSIVE`、`LATERAL`、`unnest(...) WITH ORDINALITY`、`MERGE`。
  - SQL Server `[schema].[table]`、`CROSS APPLY`、`OUTER APPLY` 先作为 disabled/future fixture。
- 复杂 SQL 的负向断言：
  - 不输出 CTE/derived table/function rowset 伪表。
  - 不把 `u.id IS NULL`、`a.closed_at IS NULL`、`status = 'PAID'` 等过滤条件当关系。
  - 不把 `LATERAL`、`unnest`、临时输入 rowset 当物理业务表。
- evidence/confidence 断言：
  - `EvidenceType` 正确，例如 `SQL_LOG_JOIN`、`VIEW_JOIN`、`PROCEDURE_JOIN`、`SQL_LOG_SUBQUERY_IN`、`SQL_LOG_EXISTS`。
  - `EvidenceSourceType` 正确，例如 `NATIVE_LOG`、`PLAIN_SQL`、`DATABASE_OBJECT`。
  - `attributes.joinKind` 正确，例如 `LEFT_JOIN`、`RIGHT_JOIN`、`FULL_JOIN`。
  - 加入 `TARGET_UNIQUE`、`NAMING_MATCH`、`VALUE_CONTAINMENT_HIGH` 后 confidence 与公式一致。
- correctness fixture：
- `CorrectnessFixtureRunnerTest` 扫描 `test-fixtures/correctness`，以 `expected-relations.json` 中的当前 parser gold fingerprints 为关系正确性基线；如果 fixture 存在 `expected-lineage.json`，还会比对 Data Lineage fingerprints。默认无 profile/version 的 fixture 走 token-event；full-grammer 由 shadow/parity 测试证明不低于 token-event。
  - routine/function fixture 使用 manifest `statementFormat: OBJECT_BLOCKS`，按 `-- relation-detector-fixture-source` / `-- relation-detector-fixture-end` block 读取一个完整对象定义，不能按普通 SQL 分号拆分过程体。
  - `CorrectnessSummaryGeneratorTest` 从同一批 fixture/golden 生成 `docs/generated/correctness-test-summary.md`，报告只展示 SQL/DDL preview、input 文件路径、expected relationship/data-lineage fingerprints、warning codes 和 forbidden tables。完整 SQL/DDL 保留在对应 fixture 的 `input.sql` 或 `input.ddl.sql` 中。普通测试只校验报告是否最新，不自动写文件。
  - `DataLineageAuditGeneratorTest` 从全部 correctness fixture 和 `TokenEventDataLineageExtractor` 当前输出生成 `docs/parser-audit/data-lineage-full-audit.md`。该报告不是 golden 自动扩容工具，而是人工审核索引：每个 fixture 被归类为 `EXISTING_GOLD`、`SUGGESTED_GOLD`、`PENDING_REVIEW` 或 `NOT_APPLICABLE`，并列出 extractor 候选 fingerprints 和未进入 golden 的原因。
  - MySQL/PostgreSQL parser selection 测试必须断言 `attributes.grammar`、`attributes.lexer`、`attributes.parser` 和 `attributes.eventBuilder`，证明 adaptor 选择了自己的方言 parser/event builder。
  - fixture 的 `expected-diagnostics.json` 只记录 fixture hash 和 warning code count；不再保存 Simple/ANTLR comparison delta。
- token-event 独立抽取测试：
  - `TokenEventRelationExtractorIndependenceTest` 用“raw SQL 无关系但 events 有关系”和“events 为空时 extractor 不回退解析 raw SQL”两种输入，证明 SQL 关系抽取独立消费 token-event event。
  - `SqlRelationParserRunnerTest` 覆盖 runner 总是调用 adaptor token-event parser，SQL log noise filter 仍生效，空结果不会被旧 parser 替换。
  - `DdlRelationParserRunnerTest` 覆盖 runner 总是调用 structured DDL parser，空 DDL event 结果不会被旧 parser 替换。
- 重复 SQL 日志 JOIN 输出两份证据：`rawEvidence` 保留每次观测，`evidence` 聚合为一条基础 evidence 加一条 `REPEATED_OBSERVATION`。
- YAML 中环境变量缺失时报错。
- unknown adaptor 报 `ADAPTOR_ERROR`。

### 5.2 黑盒功能测试

从 CLI 入口测试完整链路。

建议用例：

- file-only DDL + SQL 日志输入，输出 JSON。
- file-only DDL + SQL 日志输入，输出 table。
- `--min-confidence 0.90` 过滤掉低置信共现关系。
- 输入文件不存在时返回非零错误码。
- 配置只开启 `dataProfile` 时失败。
- 配置 `database.type: oracle` 但无 adaptor 时失败。
- `--output target/result.json` 能写文件。

### 5.3 集成测试

MySQL：

- 使用 Testcontainers MySQL 8。
- 建表：显式 FK、unique index、普通 index、generated column、prefix index。
- 验证 metadata snapshot 包含 table/column/index/constraint facts，并且 `SHOW CREATE TABLE` 产生 `DATABASE_DDL` evidence。
- 创建 view、procedure、trigger。
- 投喂 general log/slow log fixture。
- 验证输出包含显式 FK、JOIN 推断、共现关系。

PostgreSQL：

- 使用 Testcontainers PostgreSQL 12+。
- 建 schema、FK、unique index。
- 创建 view、function、trigger function。
- 投喂 statement log fixture。
- 验证 schema、quoted identifier 和 FK 方向。

### 5.4 性能测试

目标：

- 验证 SQL 日志大文件解析吞吐。
- 验证大量候选关系归并性能。
- 验证数据画像受限，不会无限扫描。

建议指标：

- 10 万条 SQL 日志解析耗时。
- 100 万条 SQL 日志解析耗时。
- 10 万 candidate merge 内存占用。
- dataProfile 在 `sampleRows`、`timeoutSeconds`、`maxCandidatePairs` 下是否按预期停止。

### 5.5 稳定性和回归测试

建议维护一组 fixture：

- `simple-fk-schema.sql`
- `join-log.sql`
- `subquery-log.sql`
- `ambiguous-join.sql`
- `co-occurrence.sql`
- `mysql-slow.log`
- `postgres-statement.log`

每个 fixture 配一个 expected JSON。

回归测试策略：

- JSON snapshot 测试字段兼容性。
- JSON evidence 输出测试：`rawEvidence` 是未压缩数组，`evidence` 是摘要数组，`attributes.count` 为数字，`attributes.sampleDetails` 为数组。
- correctness 明细报告同步测试：修改 `test-fixtures/correctness` 后运行 `mvn -pl relation-cli -Dtest=CorrectnessSummaryGeneratorTest -DupdateCorrectnessSummary=true -Dsurefire.failIfNoSpecifiedTests=false test` 刷新轻量索引报告 `docs/generated/correctness-test-summary.md`，再用普通 `mvn test` 校验报告没有漂移。
- Data Lineage 全量审核报告同步测试：修改 SQL fixture、`expected-lineage.json` 或 `TokenEventDataLineageExtractor` 后运行 `mvn -pl relation-cli -Dtest=DataLineageAuditGeneratorTest -DupdateDataLineageAudit=true -Dsurefire.failIfNoSpecifiedTests=false test` 刷新 `docs/parser-audit/data-lineage-full-audit.md`，再运行不带 update 参数的同一测试确认报告稳定。该报告只生成审核清单，不自动写入新的 lineage golden。
- enum 序列化值稳定性测试。
- warning code 稳定性测试。
- 置信度数值允许小范围精度变化，但 subtype 和 evidence 不应无故改变。

### 5.6 运维验收测试

上线前建议按以下清单验证：

- `mvn test` 成功。
- file-only 示例成功。
- MySQL 只读账号能读取 metadata。
- PostgreSQL 只读账号能读取 metadata。
- 无权限读取过程/函数时，工具给 warning 而不是崩溃。
- dataProfile 默认关闭。
- 输出 JSON 不包含 password 或真实采样值。
- 大日志文件解析失败时能定位到 source 和 line。

## 6. 后续演进建议

- 引入 picocli 替换手写 CLI 参数解析。
- 引入 Jackson YAML/JSON 替换轻量解析和手写 JSON。
- 按 `SqlGrammarProfile` 继续引入 MySQL/PostgreSQL 大版本 full-grammer module。新增 profile 必须通过 full-grammer shadow correctness 证明不低于 token-event，并补对应版本 fixture；无方言或无版本信息仍使用 token-event fallback。
- 继续扩展 JUnit 5 用例，并引入 AssertJ、Testcontainers 做更强断言和真实数据库集成测试。
- 增加 Maven assembly/shade 打包，生成单个可执行发行包。
- 扩展 MySQL/PostgreSQL unique/index 元数据采集。
- 按 adaptor API 增加 SQL Server 和 Oracle 模块。
