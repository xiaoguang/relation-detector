# 代码实现说明与运维测试指南

本文档面向后续实施、维护、运维和测试人员，解释当前代码如何对应设计文档，以及如何运行示例、投喂数据、理解输出和设计测试用例。

## 1. 当前实现状态

当前代码已经落成 Java 17 + Maven 多模块工程。仓库根目录现在按产品边界分成
`relation-detector/` 与 `semantic-layer/` 两个同级目录；根 `pom.xml` 仍是统一 Maven
reactor：

```text
repo-root/
  pom.xml
  relation-detector/
    contracts/
    grammar/
      mysql-v5_7/ mysql-v8_0/
      postgres-v16/ postgres-v17/ postgres-v18/ postgres-routine/
      oracle-v12c/ oracle-v19c/ oracle-v21c/ oracle-v26ai/
      sqlserver-v2016/ sqlserver-v2017/ sqlserver-v2019/
      sqlserver-v2022/ sqlserver-v2025/
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

`relation-detector/` 内部仍采用短名称以提升本地开发体验；Maven `artifactId` 继续保留
`relation-detector-*` 长名称，例如 `relation-detector/core/` 对应
`relation-detector-core`，`relation-detector/adaptor-mysql/` 对应
`relation-detector-adaptor-mysql`。除非特别说明，本 relation-detector 文档中的模块内路径
默认相对 `relation-detector/`；从仓库根运行的 Maven / shell 命令必须写完整
`relation-detector/...` 模块路径。`semantic-layer/semantic-core` 和
`semantic-layer/semantic-cli` 是独立语义层，只消费 relation-detector JSON，不依赖
`core`、`cli` 或任何 `adaptor-*`。

`relation-detector/grammar/` 是 15 个独立 generated grammar artifact 的聚合模块。
versioned full grammar 和 PostgreSQL routine grammar 在这里生成/编译；adaptor 只依赖对应
artifact，因此修改普通 visitor 不会再触发大型 ANTLR 重生成。

已实现能力：

- 稳定 enum 和 adaptor API。
- Java SPI adaptor 发现。
- Common portable SQL 已作为正式 CLI parser category 接入；MySQL/PostgreSQL 内置成熟 adaptor；Oracle 与 SQL Server 已有 adaptor、token-event baseline 和 versioned full-grammer sample-data golden。
- DDL 外键解析，包括 inline references、ALTER TABLE FK、复合 FK、quoted schema-qualified 名称，以及 PK/unique/index 辅助 evidence。
- 纯 SQL 文本、MySQL 日志、PostgreSQL statement log 的 SQL 抽取。
- SQL/DML relationship 抽取，包括 JOIN、comma rowset、EXISTS、IN/tuple IN、CTE/derived 回溯、列级/表级共现、自连接弱共现，以及 MySQL/PostgreSQL 多表 DML；Oracle 当前覆盖 portable typed subset。
- Data Lineage v1，包括 `UPDATE SET`、`INSERT SELECT`、基础 `MERGE`、projection alias、derived aggregate、CASE/control flow、COALESCE、算术、函数和 CUMULATIVE transform。
- 关系证据合并和置信度计算。
- JSON/table 输出，以及 top-level `namingEvidence` 命名证据池。
- file-only 示例配置和输入数据。

当前 CLI 参数解析仍是轻量实现；YAML 配置读取已经由 `SimpleYamlConfigLoader` 内部使用 Jackson `YAMLMapper` 完成，JSON 输出已经由 `JsonResultWriter` 使用 Jackson `ObjectMapper` 完成。测试体系以 JUnit 5 为主，并用各 parser 自己的 correctness fixture、CLI E2E golden 和语义单测保护 SQL/DDL 行为。后续可按模块引入 picocli、AssertJ、Testcontainers 等工程化增强，但不能改变 relationship / Data Lineage / naming evidence JSON schema 或 confidence 语义。

## 2. 模块说明

### 2.1 contracts

核心文件：

- `contracts/Enums.java`
- `contracts/spi/DatabaseAdaptor.java`
- `contracts/spi/Collectors.java`
- `contracts/model/RelationshipCandidate.java`
- `contracts/model/DataLineageCandidate.java`
- `contracts/model/DataLineageEvidence.java`
- `contracts/model/Evidence.java`
- `contracts/model/NamingEvidenceCandidate.java`
- `contracts/model/TableId.java`
- `contracts/model/ColumnRef.java`
- `contracts/model/Endpoint.java`
- `contracts/metadata/MetadataSnapshot.java`
- `contracts/parse/StructuredSqlEvent.java`

职责：

- 定义第三方数据库 adaptor 必须遵守的接口。
- 定义 JSON 输出和内部模型使用的 enum。
- 定义 relationship、Data Lineage、naming evidence、warning、metadata snapshot、SQL statement 等跨模块类型。

设计对应：

- `docs/design/relation-detector/phase-02-core-model-scoring.md`
- `docs/design/relation-detector/phase-03-adaptor-api-spi.md`
- `docs/design/relation-detector/enum-reference.md`

维护注意：

- enum 的 JSON 字符串不能随意改名。
- `contracts` 不应依赖 `core`，否则第三方 adaptor 会被迫依赖核心实现。
- 新增数据库 adaptor 时，应只依赖该 API 和必要的工具库。

### 2.2 core

核心文件：

- `scan/ScanEngine.java`
- `scan/ScanConfig.java`
- `scan/ScanResult.java`
- `relation/RelationshipMerger.java`
- `relation/NamingEvidenceExtractor.java`
- `relation/NamingEvidenceMerger.java`
- `relation/NamingMatchEvidenceEnhancer.java`
- `lineage/StructuredDataLineageExtractor.java`
- `lineage/ProjectionTraceResolver.java`
- `lineage/DataLineageMerger.java`
- `scoring/ConfidenceCalculator.java`
- `diagnostics/DiagnosticWarnings.java`
- `parse/AntlrSqlParseSupport.java`
- `tokenevent/TokenEventStructuredDdlParser.java`
- `tokenevent/TokenEventStructuredSqlParser.java`
- `common/CommonDatabaseAdaptor.java`
- `relation/TokenEventRelationExtractor.java`
- `parser/DdlRelationParserRunner.java`
- `parser/SqlRelationParserRunner.java`
- `output/JsonResultWriter.java`
- `output/TableResultWriter.java`
- `log/PlainSqlLogExtractor.java`
- `log/SqlLogNoiseFilter.java`
- `metadata/MetadataEvidenceEnhancer.java`

职责：

- 编排扫描流程。
- 调用 adaptor 的 metadata、DDL、object、log、profile 钩子。
- 合并多个来源产生的关系候选。
- 抽取、合并并输出 top-level naming evidence 证据池。
- 计算最终 confidence。
- 输出 JSON/table。

结构注释入口：

- 每个生产 package 都有中英双语 `package-info.java`，用于说明该包在当前 parser / relationship / lineage / DDL 架构中的职责边界。
- `docs/design/relation-detector/phase-06-parser-enhancement.md` 的“代码结构注释索引”和“详细函数级调用结构”是这些 package 注释的设计展开。
- 新增 package 时必须同步新增 `package-info.java`；新增跨包调用路径时必须同步刷新 Phase 6 详细设计。
- 每个生产类需要类级中英双语 Javadoc，说明文件负责什么、不负责什么、位于哪条链路；关键 public 方法、核心编排方法和复杂 private helper 需要方法级中英双语注释。
- 不强制给 record accessor、简单 getter、显而易见的小工具方法写注释；避免把注释变成逐行翻译或噪声。

设计对应：

- `docs/design/relation-detector/phase-02-core-model-scoring.md`
- `docs/design/relation-detector/phase-06-parser-enhancement.md`
- `docs/design/relation-detector/phase-08-output-ux.md`

维护注意：

- `ConfidenceCalculator` 是置信度公式的唯一默认实现。
- `RelationshipMerger` 负责 `relationSubType` 主导证据优先级。
- SQL/DDL parser 由 `ParserBundleSelector` 统一选择，并一次性返回同一模式下的 SQL parser 与 DDL parser。运行模式是 `parser.mode: auto|full-grammer|token-event`：profile/version/JDBC metadata 足够时可使用版本化 full-grammer；无法选择 profile、版本不支持或 full-grammer hard failure 时 fallback 到 adaptor 暴露的 token-event parser，并输出 `PARSER_MODE_FALLBACK` warning。profile 已选中后的 syntax warning / partial events 属于 full-grammer 结果，不触发 fallback；fallback 只发生在 profile selection 或 hard failure 边界，不在 event 层混入 token-event 补齐。`parser.sql.mode`、`parser.ddl.mode`、`simple`、`antlr-shadow`、`simple-ddl` 和旧 simple fallback 已移除。
- `SqlRelationParserRunner` 与 `DdlRelationParserRunner` 不再各自重复 profile selection。SQL 链路通过同一个 structured parse result 同时服务 relationship extraction 与 Data Lineage extraction，避免同一条 SQL 在正常路径上重复解析。
- `AntlrSqlParseSupport` 是 ANTLR parse support 的通用骨架，负责动态 SQL warning、统一 diagnostics attributes 和 `StructuredParseResult`。
- `CommonRelationSql.g4` 是 core portable SQL subset typed grammar；公共 common token-event visitor 只覆盖跨 MySQL/PostgreSQL/Oracle/SQL Server 稳定的 `SELECT`、CTE、derived table、`JOIN ... ON/USING`、comma join、`EXISTS`、scalar/tuple `IN (SELECT ...)`、`INSERT ... SELECT`、基础 `UPDATE SET` / `DELETE WHERE` 和 DDL FK/index context。MySQL/PostgreSQL/Oracle/SQL Server 的 `MySqlRelationSql.g4` / `PostgresRelationSql.g4` / `OracleRelationSql.g4` / `SqlServerRelationSql.g4` 都归属各自 adaptor，并作为方言 token-event typed structural grammar：先覆盖 common portable subset，再保留方言 lexer/rowset/DDL 差异。adaptor 子包里的 `com.relationdetector.mysql.tokenevent.MySqlTokenEventStructuredSqlParser` / `com.relationdetector.postgres.tokenevent.PostgresTokenEventStructuredSqlParser` / `com.relationdetector.oracle.tokenevent.OracleTokenEventStructuredSqlParser` / `com.relationdetector.sqlserver.tokenevent.SqlServerTokenEventStructuredSqlParser` 和对应 DDL parser 直接运行 typed parse-tree visitor；当前生产事件不再通过 token-span scanner、旧补充路径或 DDL cursor/scanner 补齐。
- `CommonDatabaseAdaptor` 把 common portable typed grammar 暴露为正式 CLI parser category。配置 `database.type: common` 时，CLI 仍通过 Java SPI、`ScanEngine`、`StatementExecutionService`、naming evidence、lineage、derived path 和 JSON writer 的完整生产链路执行；它只支持 file DDL、object files 和 plain SQL logs，不做 live metadata、database object collection 或 data profiling。common 不是 MySQL/PostgreSQL/Oracle/SQL Server 的 fallback facade，也不会吸收方言专属语法。
- ANTLR 只负责把文本变成结构化 token/parse 事件，不负责直接判定业务关系。DDL-only 能力必须进入 common/方言 DDL entry rule、typed parse-tree visitor、`DdlRelationExtractionVisitor` 或 `DdlRelationParserRunner`；SQL-only 能力必须进入 SQL entry rule、typed parse-tree visitor、`TokenEventRelationExtractor` 或 `SqlRelationParserRunner`。不要把二者回流成一个全局万能 visitor。
- SQL 与 DDL correctness fixture golden 仍保护正式输出。root baseline manifest 显式走 token-event；versioned manifest 显式走 full-grammer。full-grammer 不再通过 token-event baseline 做跨 parser 兜底；如果版本化 full-grammer 漏识别，必须在自己的 `mysql/v5_7`、`mysql/v8_0`、`postgres/v16`、`postgres/v17`、`postgres/v18` golden 中直接失败并修 parser。SQL fixture 证明 SQL relation extractor 行为，DDL fixture 证明 DDL event visitor 与 DDL relation extraction 行为；禁止用其中一条链路的通过结果替代另一条链路验收。
- 新增方言差异时，优先进入对应数据库自己的 token-event grammar/visitor，例如 `mysql.tokenevent.MySqlTokenEventStructuredSqlParser`、`postgres.tokenevent.PostgresTokenEventStructuredSqlParser`、`oracle.tokenevent.OracleTokenEventStructuredSqlParser`、`sqlserver.tokenevent.SqlServerTokenEventStructuredSqlParser`、`mysql.tokenevent.MySqlTokenEventStructuredDdlParser`、`postgres.tokenevent.PostgresTokenEventStructuredDdlParser`、`oracle.tokenevent.OracleTokenEventStructuredDdlParser`、`sqlserver.tokenevent.SqlServerTokenEventStructuredDdlParser`。只有跨数据库稳定且语义相同的逻辑，才放入 core。
- 不要混淆三类 mode：`parser.mode` 是系统运行模式，只允许 `auto|full-grammer|token-event`；MySQL `SQL_MODE` 是 MySQL full-grammer runtime 的语法开关，由 `MySqlGrammarSqlMode` / `MySqlGrammarSqlModes` 表达；ANTLR lexer mode 是 `.g4` 内部词法状态，例如 PostgreSQL 字符串或 meta-command 状态，不对应 Java parser mode 类。
- typed token-event visitor 从 ANTLR parse tree 产出 `ROWSET_REFERENCE`、`PREDICATE_EQUALITY`、`JOIN_USING_COLUMNS`、`EXISTS_PREDICATE`、`IN_SUBQUERY_PREDICATE`、DDL FK/index、写入映射和 projection 等结构化事件。`TABLE_REFERENCE` / `COLUMN_EQUALITY` 仍作为兼容/bootstrap event 类型存在，但当前 relation extractor 的主输入是归一后的 rowset/predicate/DDL 事件。
- typed token-event visitor 区分 DML `USING table` 与 `JOIN USING (columns)`：前者可以产生 rowset，后者只能基于 `USING` 列名生成经过审核的弱共现证据，不能把列名当作 `ROWSET_REFERENCE`。
- `TokenEventStructuredSqlParser` / `TypedDialectTokenEventStructuredSqlParser` / `TokenEventStructuredDdlParser` 共同构成 token-event fallback 层：无 profile、unsupported version、full-grammer hard failure 或显式 `parser.mode=token-event` 时使用。common token-event 直接使用 `CommonRelationSql.g4` typed visitor，并且通过 `CommonDatabaseAdaptor` 可被 CLI 显式选择为 `database.type: common`；MySQL/PostgreSQL/Oracle/SQL Server token-event 使用各自 adaptor 的方言 typed visitor。公共 relation、rowset/scope、DML 深水区、Data Lineage 写入映射和 derived aggregate projection 已经迁入 token-event 事件和抽取测试，覆盖 `JOIN USING`、raw equality、correlated `EXISTS`、scalar/tuple `IN`、列级弱共现、CTE/temp/trigger scope、MySQL multi-table `DELETE`、PostgreSQL `UPDATE FROM`、SQL Server `UPDATE FROM` / `APPLY`、`UPDATE SET`、derived aggregate、`INSERT SELECT`、`MERGE`；新增 MySQL/PostgreSQL/Oracle/SQL Server token-event 专属规则必须进入方言 typed grammar/visitor，不得放回公共万能层。
- `SqlGrammarProfile` / `SqlGrammarProfileRegistry` / `FullGrammerDialectModule` 是版本化 full-grammer 接入点。当前注册 `mysql-5.7`、`mysql-8.0`、`postgresql-16`、`postgresql-17`、`postgresql-18`、`oracle-12c`、`oracle-19c`、`oracle-21c`、`oracle-26ai` module；人工配置 `parser.grammarProfile` 优先，其次可用 `parser.databaseVersion` 或 JDBC `DatabaseMetaData` 选择 profile。同一 major 的 minor 默认复用该 major profile；如果请求版本只比最高已支持版本高 1 个 major，可以临时选择最近低版本 profile 并返回 diagnostic；超过 1 个 major 或没有方言/版本信息时，回退 token-event parser。
- `FullGrammerParserBundleFactory` / `FullGrammerStructuredSqlParser` 是版本化 full-grammer 接入基础设施。14 套 versioned grammar 与 PostgreSQL routine grammar 位于 `relation-detector/grammar/*` 独立 Maven artifact，保持原 Java package；adaptor 只依赖 generated artifact，并保留 binding、profile module、version policy、typed context adapter 和 visitor。这使普通 visitor 修改不再触发大 grammar 重新生成，同时不合并不同版本 parser。MySQL 使用固定 grammars-v4 快照并按 5.7/8.0 官方边界收紧；PostgreSQL 以官方 `gram.y` / `scan.l` / keywords 为 source-of-truth；Oracle 12c/19c/21c/26ai 仍是 `INCOMPLETE_VERSIONED`；SQL Server 2016/2017/2019/2022/2025 以 Microsoft Learn T-SQL reference 为版本边界依据。独立 correctness fixture 锁定每个 profile，低版本不得静默复用高版本能力。core 只通过 `ServiceLoader<FullGrammerDialectModule>` 查找 module，不 import adaptor 或 generated parser。
- `FullGrammerGeneratedParserSmokeTest` 验证 MySQL/PostgreSQL full-grammer generated lexer/parser 可实例化并解析基础 SQL；Oracle adaptor 由 `OracleAdaptorParserTest` 和 `OracleParserArchitectureTest` 验证 ServiceLoader、Oracle token-event grammar、`INCOMPLETE_VERSIONED` full-grammer generated parser attributes、“不得持有 token-event delegate”和 Oracle SQL 资产卫生边界。full-grammer 行为测试只验证具体 SQL/DDL 的关系、血缘、warning 行为；不再把内部事件来源标签作为默认测试目标，也不再用 token-event baseline 兜底验证 full-grammer。后续 profile 深化 parse-tree visitor 后，missing 必须修 visitor，extra 进入审核，不自动写入 golden。
- `TokenEventRelationExtractor` 从 `ROWSET_REFERENCE` / `PREDICATE_EQUALITY` / `JOIN_USING_COLUMNS` / `EXISTS_PREDICATE` / `IN_SUBQUERY_PREDICATE` / `TUPLE_IN_SUBQUERY_PREDICATE` 等事件独立构造 FK-like/CO_OCCURRENCE 候选，并使用结构化 projection trace 信息把 CTE / 派生表端点回溯到物理列。公共 extractor 只保留跨方言关系语义；MySQL/PostgreSQL/Oracle/SQL Server 专属 rowset keyword、rowset modifier、CTE modifier 和多表 DML rowset 识别必须通过对应方言 `.g4` 与 typed visitor 实现。明确列等值时保留具体语法 evidence：JOIN / comma join 为 `SQL_LOG_JOIN`，correlated `EXISTS` 为 `SQL_LOG_EXISTS`，`IN (SELECT ...)` / tuple IN 为 `SQL_LOG_SUBQUERY_IN`。这些具体 predicate evidence 是 `SQL_LOG_COLUMN_CO_OCCURRENCE` 在生产路径上的替代，保留了原本“列级关联出现过”的含义，同时避免丢失 SQL 语法来源。同一物理表 self-join 在不同 SQL alias 下保留 role co-occurrence，并在 attributes 记录 alias；同 alias 行内比较不输出。`SQL_LOG_COLUMN_CO_OCCURRENCE` / `SQL_LOG_TABLE_CO_OCCURRENCE` 是 `RESERVED_COMPATIBILITY / NOT_PRODUCED`：enum、score 和 merger 兼容逻辑保留，但生产 typed path 不主动产出。`SQL_LOG_TABLE_CO_OCCURRENCE` 没有等价现役替代；无列谓词的多表同现当前不生成正式 relationship，只有历史/外部导入或显式 opt-in 审计场景可使用。PostgreSQL `ONLY`、`TABLESAMPLE`、`ROWS FROM`、`JOIN USING (...) AS alias` 这类语法必须配套其他方言负向测试，防止被当作物理表或共现证据；MySQL `STRAIGHT_JOIN`、ODBC `{ OJ ... }`、optimizer hints、`PARTITION (...)`、`JSON_TABLE(...)`、multi-table `UPDATE/DELETE` 这类语法也必须配套其他方言负向测试，防止被误抽为关系。
- `correlated EXISTS` 是公共 SQL 关系语义，新增或维护这类能力时，公共层只能处理跨方言相关谓词抽取，例如 `WHERE EXISTS (SELECT 1 FROM users u WHERE u.id = o.user_id)` 生成 `SQL_LOG_EXISTS` evidence。EXISTS 子查询内部如果出现 MySQL/PostgreSQL/Oracle/SQL Server 专属 rowset、function table、hint、`ONLY`、`JSON_TABLE`、`CONNECT BY`、`APPLY` 等语法，必须下沉到对应方言 visitor，并配套反向负向测试。
- SQL-only predicate 不能只靠“出现了 JOIN”直接定向成 FK-like。普通 JOIN、EXISTS、IN 和 tuple IN 都先证明列级谓词存在；如果 DDL/metadata/data-profile 在同一端点上提供方向，metadata/DDL index facts 证明一侧 unique、一侧 non-unique，或 `NamingMatchEvidenceEnhancer` 从 top-level `namingEvidence` 池里命中唯一 `_id/id` 方向提示，`RelationshipMerger` 才把关系定向为 FK-like。无法判断方向时保留 `CO_OCCURRENCE`。`NAMING_MATCH` 不能凭空创建 relation，也不能参与 SQL 结构判断；relationship 只能消费已经抽取出的 `namingEvidence`，不能自己重新计算命名规则。
- `TokenEventRelationExtractor` 消费结构事件并负责跨方言关系语义，不应重新承载数据库专属 rowset scanner。新增或修改方言 rowset/DDL 兼容逻辑必须进入对应 token-event typed grammar/visitor，并同时补 correctness fixture、更新 `docs/design/relation-detector/phase-06-parser-enhancement.md`。
- `SqlLogNoiseFilter` 在 `SqlRelationParserRunner` 之前过滤 native log 噪声。默认按数据库类型过滤系统 catalog 查询，并允许 YAML 通过 `sources.logs.filterSystemQueries`、`sources.logs.systemSchemas`、`sources.logs.metadataQueryMarkers` 覆盖。
- `TokenEventStructuredDdlParser`、`MySqlTokenEventStructuredDdlParser`、`PostgresTokenEventStructuredDdlParser`、`OracleTokenEventStructuredDdlParser`、`SqlServerTokenEventStructuredDdlParser` 通过 typed DDL grammar context 输出 `DDL_FOREIGN_KEY` / `DDL_INDEX` / `DDL_COLUMN` 事件；`DdlRelationExtractionVisitor` 独立把这些事件转换成 DDL FK-like 关系，并用 source index / target unique evidence 增强关系。DDL relationship 结构判断不依赖 DDL cursor/scanner、regex 或名字白名单。
- `ProjectionTraceResolver` 为 Data Lineage 写入映射提供保守列来源回溯，支持 CTE、派生表、多层嵌套 projection alias 和结构化表达式来源。它消费 `StructuredSqlEvent`，不重新解析 SQL 文本，也不保留 regex / token span fallback。裸列投影（例如 `SELECT user_id FROM orders`）只有在事件作用域和 fixture 覆盖证明安全时才回溯；复杂表达式的完整多源字段血缘由 `StructuredDataLineageExtractor` 通过 `ExpressionSourceSet`、`AssignmentMapping`、`ProjectionTrace` 输出。新增类似能力必须先说明方言和 SQL 形态边界，并配 correctness fixture。
- `NamingEvidenceExtractor` 位于 metadata / DDL / SQL predicate 解析之后，负责生成唯一的 top-level `namingEvidence` 证据池。它不再内置命名规则，而是调用 `NamingRuleEngine` 执行合并后的 `NamingRuleSet`：系统默认规则来自 `naming-rules/system-default.yml`，客户规则来自 YAML `namingMatch.ruleFiles` 和 inline `namingMatch.rules`。系统默认 direct 规则仍是 `TABLE_ID`、`ID_SUFFIX_TO_ID`、`SELF_ROLE_ID`；客户规则统一输出 `USER_CONFIGURED` 并带 `configuredRuleId`、`configuredRuleDescription`、`ruleSource` attributes。`TRANSITIVE_NAMING_PATH` 只能由 derived path 引擎生成，不能出现在用户配置中。命名 evidence 不能生成 relationship。DDL column inventory 与 DDL FK/index event 必须保留 SQL/DDL 显式写出的 schema：写 `schema.table` 就输出 `schema.table` endpoint，写 `table` 就输出裸 `table` endpoint，不自动补 `dbo` / `public` / current schema，也不丢弃显式 schema。
- `NamingMatchEvidenceEnhancer` 位于 naming evidence 抽取之后、relationship merge 之前。它只查询 top-level `namingEvidence` 池，并在命中已有列级 SQL predicate candidate 时给 relationship 挂轻量 `NAMING_MATCH` evidence：attributes 包含 `evidenceRef`、`namingRule`、`suggestedSourceEndpoint`、`suggestedTargetEndpoint`、`matchedColumn`、`matchedTable`、`directionHint=true`。relationship 内不重复 raw naming observations。
- `DerivedPathInferenceService` 是可选推导层，默认关闭。开启 `derivedPaths.enabled=true` 后，它从已定向 relationship、`VALUE` lineage 和 top-level namingEvidence 构建有向证据图，输出 `derivedRelationships`、`derivedDataLineages`，并可把 `TRANSITIVE_NAMING_PATH` 写回 top-level namingEvidence。relationship 推导内部按 referenced-by 方向反向遍历 FK-like 边，但输出仍保持 `child/dependent -> parent/referenced` 的 FK-like 正向；path 中用 `traversalMode=REVERSE_REFERENCED_BY`、`outputDirection=FK_LIKE_FORWARD` 和 `traversalPath` 保留审计轨迹。derived 输出必须带完整 path、`TRANSITIVE_PATH` / derived `NAMING_MATCH` evidence 和 raw observations；它不修改直接 relationship / lineage。derived graph 使用严格 endpoint key：`schema.table.column` 不会自动降级匹配 `table.column`。表内 identity bridge 仍保留，但只在同一个 canonical table key 内桥接，例如 `orders.id -> orders.customer_id` 或 `dbo.orders.id -> dbo.orders.customer_id`，不能跨 `orders` 与 `dbo.orders` 桥接。
- `StructuredDataLineageExtractor` 是正式 Data Lineage v1 输出链路，独立于 `TokenEventRelationExtractor`。它从 token-event / full-grammer 结构事件中抽取 `UPDATE SET`、`INSERT INTO ... SELECT` 和基础 `MERGE UPDATE/INSERT` 的 `table.column -> table.column` 字段血缘，跳过参数、JSON path、字面量和局部变量。`DataLineageCandidate.confidence` 只解释血缘可信度，不参与 relationship confidence。
- `DataLineageMerger` 只按 `sources + target + flowKind + transformType` 去重字段血缘，并与 relationship / naming evidence 一样保留 `rawEvidence` 与 grouped `evidence` 双层模型；不要把它接入 `RelationshipMerger`。
- `mysql.tokenevent.MySqlTokenEventStructuredDdlParser` / `postgres.tokenevent.PostgresTokenEventStructuredDdlParser` / `oracle.tokenevent.OracleTokenEventStructuredDdlParser` / `sqlserver.tokenevent.SqlServerTokenEventStructuredDdlParser` 是 adaptor DDL 入口，内部共享 token-event DDL event pipeline。数据库私有 DDL 写法应进入对应方言 `.g4` 和 typed visitor，不应回流为一个跨库万能 parser。
- `JsonResultWriter` 使用 Jackson `ObjectMapper` 输出稳定 JSON。顶层包含 `relationships`、`dataLineages`、`derivedRelationships`、`derivedDataLineages`、`namingEvidence`、`derivedNamingEvidence`、`warnings`；relationship / lineage / naming evidence / derived path 都使用 `rawEvidence` + grouped `evidence` 双层结构，`NAMING_MATCH` 在 relationship 里只保存 `evidenceRef` 和轻量摘要。`derivedNamingEvidence` 只是 `TRANSITIVE_NAMING_PATH` 的阅读/统计视图，只输出 `id/source/target/rule/directionHint`，完整 evidence 仍通过相同 id 到 top-level `namingEvidence` 查询。summary 对 relationship、dataLineage、namingEvidence 都只提供统一的 `direct*Count`、`derived*Count`、`total*Count` 和对应 observation count，不再输出重复的旧别名字段。Observation count 是可通过 `output.includeObservationCounts` 关闭的调试字段，只统计 raw observation 数量，不参与 confidence 或事实判断。
- `DiagnosticWarnings` 集中构造解析/提取失败 warning。`ScanEngine`、DDL parser、log extractor 不应各自拼装不同格式；失败时应保留 `exceptionClass`，并在能拿到输入文本时把原始 SQL/DDL 放入 `attributes.rawStatement`。full-grammer profile 选不中时由 parser selection 层 fallback 到 token-event；full-grammer 或 token-event 已选中后的硬失败只影响当前 statement/source，不回退旧 parser，也不混合另一条 parser 的事件。
- core 根包不再放生产 Java 类。公共能力按职责分到 `scan`、`parser`、`relation`、`lineage`、`ddl`、`tokenevent`、`fullgrammer`、`parse`、`output`、`diagnostics`、`metadata`、`log`、`scoring` 等子包。新增公共类时必须先判断职责归属，不能重新平铺到 `com.relationdetector.core`。

### 2.3 cli

核心文件：

- `Main.java`
- `SimpleYamlConfigLoader.java`
- `AdaptorRegistry.java`

职责：

- 解析命令行参数。
- 使用 Jackson YAML 读取配置。
- 通过 Java SPI 加载 adaptor。
- 调用 `ScanEngine`。
- 将结果写到 stdout 或文件。

设计对应：

- `docs/design/relation-detector/phase-01-project-skeleton.md`
- `docs/design/relation-detector/phase-03-adaptor-api-spi.md`
- `docs/design/relation-detector/phase-08-output-ux.md`

维护注意：

- `SimpleYamlConfigLoader` 保留历史类名，内部使用 Jackson `YAMLMapper` 读取配置，并保持旧配置语义：未知 key 忽略、环境变量解析、`paths/include` 展开、旧 parser 配置拒绝、默认值和校验逻辑。
- `AdaptorRegistry` 同时支持 classpath 内置 adaptor 和 `--plugin-dir` 外部 jar。

### 2.4 adaptor-mysql

核心文件：

- `MySqlDatabaseAdaptor.java`
- `META-INF/services/com.relationdetector.contracts.spi.DatabaseAdaptor`

职责：

- 通过 `information_schema.KEY_COLUMN_USAGE` 读取显式 FK。
- 读取 MySQL routine、view、trigger 定义。
- 从 MySQL general/slow log 中抽取 SQL。
- 提供 MySQL 数据画像钩子。
- 通过 SPI 注册为 `mysql` adaptor。

设计对应：

- `docs/design/relation-detector/phase-04-mysql-adaptor.md`

维护注意：

- 当前元数据采集先实现显式 FK，unique/index 采集可以按 Phase 4 继续补。
- 当前数据画像只做轻量匹配 evidence，后续应补包含率、重合率、负向证据等完整指标。
- MySQL adaptor 已声明 `mysql-connector-j` runtime 依赖；使用 Maven 构建出的运行 classpath 连接真实 MySQL 时会自动包含 Connector/J。

### 2.5 adaptor-postgres

核心文件：

- `PostgresDatabaseAdaptor.java`
- `META-INF/services/com.relationdetector.contracts.spi.DatabaseAdaptor`

职责：

- 通过 `pg_catalog.pg_constraint` 读取显式 FK。
- 读取 PostgreSQL function/procedure/view 定义。
- 从 PostgreSQL statement log 中抽取 SQL。
- 提供 PostgreSQL 数据画像钩子。
- 通过 SPI 注册为 `postgresql` adaptor。

设计对应：

- `docs/design/relation-detector/phase-05-postgres-adaptor.md`

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
VIEW/PROCEDURE JOIN  -> 当前复用 SQL_LOG_JOIN / SQL_LOG_EXISTS / SQL_LOG_SUBQUERY_IN；模型预留 VIEW_JOIN / PROCEDURE_JOIN
SQL log JOIN         -> SQL_LOG_JOIN
SQL column predicate -> SQL_LOG_JOIN / SQL_LOG_EXISTS / SQL_LOG_SUBQUERY_IN；替代生产路径上的泛化 SQL_LOG_COLUMN_CO_OCCURRENCE
SQL table co-occurrence -> RESERVED_COMPATIBILITY / NOT_PRODUCED；无等价现役替代，仅模型兼容 SQL_LOG_TABLE_CO_OCCURRENCE
data profile         -> MySQL/PostgreSQL/Oracle/SQL Server live profiler 在 dataProfile.enabled=true 时可产出 VALUE_CONTAINMENT_HIGH / VALUE_OVERLAP_HIGH / NEGATIVE_VALUE_MISMATCH；离线 INSERT 样本等待 typed literal insert sample event
derived path         -> derivedPaths.enabled=true 时，core 可从已定向关系、VALUE lineage、top-level namingEvidence 推导 TRANSITIVE_PATH / derived NAMING_MATCH；默认关闭，不改变直接事实层
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
relation-detector/test-fixtures/examples/schema.sql
```

SQL 日志文件：

```text
relation-detector/test-fixtures/examples/app-sql.sql
```

配置文件：

```text
relation-detector/test-fixtures/examples/file-only-config.yml
```

该配置关闭 JDBC 元数据，开启 DDL 和纯 SQL 文本日志，因此可以在没有数据库的环境里运行。

### 4.3 运行 JSON 输出

当前尚未打包 fat jar。CLI / correctness / 手工 SQL 分析必须先使用 Maven/Javac 产物，
不能直接信任 IDE 写入的 `target/classes`。VS Code Java Language Server / Eclipse
编译器在源码红线期间可能生成包含 `Unresolved compilation problems` 的占位 class，
这类 class 会让 `ServiceLoader` 报 `not a subtype` 或在运行时抛错。

推荐前置检查：

```bash
mvn clean -pl relation-detector/cli -am -DskipTests test-compile
relation-detector/scripts/check-no-jls-bad-classes.sh
```

如果检查失败，先执行 Maven clean build，并在 VS Code 中运行
`Java: Clean Java Language Server Workspace`。仓库的 `.vscode/settings.json` 已关闭
`java.autobuild.enabled`，避免 JLS 自动把坏 class 写入 Maven `target/classes`。

运行时建议使用仓库脚本。脚本会先构建 Maven main jar，执行坏 class 检查，再组装运行
classpath：

```bash
relation-detector/scripts/run-cli.sh scan \
  --config relation-detector/test-fixtures/examples/file-only-config.yml \
  --format json
```

不建议维护者手工拼接 `*/target/classes` 作为 CLI classpath；如果确实要手工运行，
必须先完成上述 Maven clean/test-compile 和
`relation-detector/scripts/check-no-jls-bad-classes.sh`。

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
relation-detector/scripts/run-cli.sh scan \
  --config relation-detector/test-fixtures/examples/file-only-config.yml \
  --format table
```

预期输出形态：

```text
SOURCE                    TARGET                    TYPE            SUBTYPE                  CONF   EVIDENCE
orders.user_id            users.id                  FK_LIKE         DDL_DECLARED_FK          0.9550 DDL_FOREIGN_KEY,SQL_LOG_JOIN
order_items.order_id      orders.id                 FK_LIKE         INFERRED_JOIN_FK         0.6500 SQL_LOG_JOIN,NAMING_MATCH

Warnings: 0
```

### 4.5 写入输出文件

```bash
relation-detector/scripts/run-cli.sh scan \
  --config relation-detector/test-fixtures/examples/file-only-config.yml \
  --format json \
  --output target/example-result.json
```

检查：

```bash
cat target/example-result.json
```

### 4.5.1 目录输入配置

文件型输入既支持显式 `files`，也支持 `paths + include`。`paths` 可以指向目录或单个文件；目录会按 `include` glob 递归展开，并按路径排序后进入现有 `ddlFiles`、`objectFiles`、`logFiles`。

```yaml
database:
  type: mysql
  schema: erp_sample

parser:
  mode: full-grammer
  grammarProfile: mysql/5.7
  databaseVersion: 5.7

sources:
  metadata:
    enabled: false
  ddl:
    enabled: true
    fromDatabase: false
    paths:
      - relation-detector/sample-data/mysql/5.7/01-schema
    include:
      - "**/*.sql"
  objects:
    enabled: true
    fromDatabase: false
    paths:
      - relation-detector/sample-data/mysql/5.7/02-procedures
    include:
      - "**/*.sql"
  logs:
    enabled: true
    filterSystemQueries: false
    paths:
      - relation-detector/sample-data/mysql/5.7/03-data
      - relation-detector/sample-data/mysql/5.7/04-queries
    include:
      - "**/*.sql"
  dataProfile:
    enabled: false
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

### 5.1 语义单元测试

建议优先覆盖 core semantic layer，因为 relationship、Data Lineage、confidence、warning 和 JSON 输出稳定性由 core 决定。测试应保护 SQL/DDL 行为和输出语义，不再把目录命名、delegate/native 事件来源、迁移过程属性作为默认验收目标。

核心测试对象：

- `ConfidenceCalculator`
- `RelationshipMerger`
- `AntlrSqlParseSupport`
- `TokenEventStructuredSqlParser`
- `TokenEventStructuredDdlParser`
- `TokenEventRelationExtractor`
- `StructuredDataLineageExtractor`
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
  - SQL Server `[schema].[table]`、`UPDATE ... FROM`、`MERGE`、inline table-valued function、trigger object block 通过 SQL Server sample-data correctness 覆盖；`CROSS APPLY`、`OUTER APPLY` 可作为后续 T-SQL 深水区 fixture 补强。
- 复杂 SQL 的负向断言：
  - 不输出 CTE/derived table/function rowset 伪表。
  - 不把 `u.id IS NULL`、`a.closed_at IS NULL`、`status = 'PAID'` 等过滤条件当关系。
  - 不把 `LATERAL`、`unnest`、临时输入 rowset 当物理业务表。
- evidence/confidence 断言：
  - `EvidenceType` 正确，例如 SQL typed parser 的 `SQL_LOG_JOIN`、`SQL_LOG_EXISTS`、`SQL_LOG_SUBQUERY_IN`，以及 DDL/metadata 方向证据 `DDL_FOREIGN_KEY`、`METADATA_FOREIGN_KEY`。
  - `EvidenceSourceType` 正确，例如 `NATIVE_LOG`、`PLAIN_SQL`、`DATABASE_OBJECT`。
  - `attributes.joinKind` 正确，例如 `LEFT_JOIN`、`RIGHT_JOIN`、`FULL_JOIN`。
  - 加入 `TARGET_UNIQUE`、profiler 产生或手工构造的 `VALUE_CONTAINMENT_HIGH`、relationship 引用的 top-level `namingEvidence` 后 confidence 与公式一致。`NAMING_MATCH` 只能通过 `evidenceRef` 引用命名证据池；测试要同时覆盖“SQL predicate + 命名方向可定向”和“只有命名不能创建关系”。`VALUE_CONTAINMENT_HIGH` / `VALUE_OVERLAP_HIGH` / `NEGATIVE_VALUE_MISMATCH` 的生产端由 live data profiler 单测覆盖；correctness fixture 默认不依赖 live DB。
- correctness fixture：
- `CorrectnessFixtureRunnerTest` 扫描 `test-fixtures/correctness`，以 `expected-relations.json` 中的当前 parser gold fingerprints 为关系正确性基线；如果 fixture 存在 `expected-lineage.json` 和 `expected-naming-evidence.json`，还会比对 Data Lineage 和 naming evidence fingerprints。测试框架本身拆为 `FixtureInputLoader`、`FixtureExecutionEngine`、`GoldenAssertion`、`GoldenWriter`：读取、执行、断言和更新 golden 分离。`FixtureExecutionEngine` 复用 production `StatementExecutionService` 和 `EvidenceEnhancementService`，避免 correctness 与正式 scan 证据流分叉。默认 `mvn test` 只跑 `smoke` profile 的少量代表 fixture；日常开发按受影响方言显式传 `-DcorrectnessFixtureProfile=common|mysql|postgres|oracle|sqlserver`，合并前传 `-DcorrectnessFixtureProfile=full` 跑全量。root baseline fixture 显式走 token-event；`mysql/v5_7|v8_0`、`postgres/v16|v17|v18`、`oracle/v12c|v19c|v21c|v26ai`、`sqlserver/v2016|v2017|v2019|v2022|v2025` 版本目录显式走 full-grammer。full-grammer 不再通过 token-event 对照兜底；版本化 golden 直接暴露对应 parser 的 missing / extra。
  - correctness runner 在非 golden 更新模式下按 fixture 并行执行，默认并行度为 CPU 数和 8 的较小值，可用 `-DcorrectnessFixtureParallelism=N` 调整。`-DupdateCorrectnessGold=true` 时强制串行，保证 golden 文件写入稳定。
  - correctness、CLI E2E 和手工 SQL 分析前必须先跑 `mvn clean -pl relation-detector/cli -am -DskipTests test-compile` 与 `relation-detector/scripts/check-no-jls-bad-classes.sh`，确保测试源码可编译且 `target/classes` 没有 JLS/Eclipse 占位坏 class。`package -Dmaven.test.skip=true` 只能作为生成运行 jar/classpath 的临时步骤，不能替代主线验收。
  - `CliEndToEndGoldenTest` 从 YAML/CLI 参数进入 `Main.MainCommand`、adaptor registry、`ScanEngine`、parser runner、merger 和 JSON writer，并复用现有 fixture golden 比对 CLI JSON 中的 relationship / Data Lineage fingerprints。这是完整系统链路的黑盒正确性测试，不另建重复 golden。
  - routine/function fixture 使用 manifest `statementFormat: OBJECT_BLOCKS`，按 `-- relation-detector-fixture-source` / `-- relation-detector-fixture-end` block 读取一个完整对象定义，不能按普通 SQL 分号拆分过程体。
  - `CorrectnessSummaryGeneratorTest` 从同一批 fixture/golden 生成 `docs/generated/correctness-test-summary.md`，报告只展示 SQL/DDL preview、input 文件路径、expected relationship/data-lineage fingerprints、warning codes 和 forbidden tables。完整 SQL/DDL 保留在对应 fixture 的 `input.sql` 或 `input.ddl.sql` 中。该测试默认跳过；验收时显式传 `-DrunGeneratedReportTests=true`，刷新时传 `-DupdateCorrectnessSummary=true`。
  - `DataLineageAuditGeneratorTest` 从全部 correctness fixture 和 `StructuredDataLineageExtractor` 当前输出生成 `docs/parser-audit/data-lineage-full-audit.md`。该报告不是 golden 自动扩容工具，而是人工审核索引：每个 fixture 被归类为 `EXISTING_GOLD`、`SUGGESTED_GOLD`、`PENDING_REVIEW` 或 `NOT_APPLICABLE`，并列出 extractor 候选 fingerprints 和未进入 golden 的原因。该测试默认跳过；验收时显式传 `-DrunGeneratedReportTests=true`，刷新时传 `-DupdateDataLineageAudit=true`。
  - `DialectSqlAssetHygieneTest` 扫描 MySQL / PostgreSQL / Oracle / SQL Server 的 `sample-data` 和 correctness SQL，阻止明显跨方言残留，例如 MySQL 资产里出现 `LANGUAGE plpgsql` / `VARCHAR2`，PostgreSQL 资产里出现 `AUTO_INCREMENT` / `ENGINE=...`，Oracle 资产里出现 `LIMIT` / `::type` / `ON DUPLICATE KEY UPDATE`，SQL Server 资产里出现 `VARCHAR2` / `AUTO_INCREMENT` / `LANGUAGE plpgsql`。该检查只做资产卫生守门，不替代真实数据库装载或完整官方 grammar 验证。
- MySQL/PostgreSQL/Oracle/SQL Server parser selection 测试必须断言 `attributes.grammar`、`attributes.lexer`、`attributes.parser` 和 `attributes.eventBuilder` 或 profile attributes，证明 adaptor 选择了自己的方言 parser/event builder。
  - fixture 的 `expected-diagnostics.json` 只记录 fixture hash 和 warning code count；不再保存 Simple/ANTLR comparison delta。
- token-event / full-grammer 行为测试：
  - `SqlRelationParserRunnerTest` 覆盖 `parser.mode`、profile/version 选择、unsupported version fallback、SQL log noise filter 和 warning 行为。
  - `DdlRelationParserRunnerTest` 覆盖同一 parser mode 策略下的 DDL relationship 抽取和 failure warning。
  - full-grammer 测试只断言 SQL/DDL relationship、Data Lineage、warning 和具体方言行为，不断言内部 native/delegate/bridge 过程属性。
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
- 配置 `database.type: sqlserver` 但无 adaptor 时失败；如果 `adaptor-oracle` 不在 classpath，`database.type: oracle` 也应失败。
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

- 日常开发按 `focused -> scope -> matrix-smoke -> acceptance` 四级门禁执行。
  `relation-detector/scripts/test-scope.sh <core|mysql|postgres|oracle|sqlserver|assets>`
  会在一次 reactor 中合并运行受影响模块测试和 dialect correctness；
  `mvn -T 2 -Pmatrix-smoke verify` 覆盖全部 19 个 parser category 的代表 fixture。
- 每个逻辑批次结束执行 `mvn -T 2 -Pacceptance verify`。该 profile 运行全部
  1198 个 correctness fixture，使用 fixture parallelism 12、CLI test fork count 2，并显式验收
  generated reports。结构重构期间不得使用 `updateCorrectnessGold` 掩盖差异。
- 最终入口是 `bash relation-detector/scripts/verify-all.sh`。它只执行一次 Maven
  acceptance reactor，然后复用已打包 CLI 的 `batch --manifest` 一次 JVM 并行生成
  19 类 parser 的 38 份 direct/derived JSON，最后执行 summary、reference、absolute-path
  和 canonical output 校验。
- 发布前另外执行一次无缓存参考构建：
  `mvn -T 2 -Pacceptance -Dmaven.build.cache.enabled=false clean verify`。Maven Build Cache
  只复用 generated/compiled artifact，Surefire/Failsafe 仍每次运行，不缓存测试结果。
- `relation-detector/scripts/benchmark-build.sh` 记录 clean/warm/focused/full/CLI 时间；
  report 只读本次 session 的 Surefire XML，包含 module timing、ANTLR timing、测试 Top 20、
  fixture Top 20、CLI case timing 和忽略生成时间的 canonical JSON hash。

- 构建卫生测试：运行 `mvn clean -pl relation-detector/cli -am -DskipTests test-compile` 后执行 `relation-detector/scripts/check-no-jls-bad-classes.sh`。该脚本会扫描 `target/classes` 中的 JLS/Eclipse 占位错误字符串，并检查 MySQL/PostgreSQL/Oracle/SQL Server adaptor class 是否真实实现 `DatabaseAdaptor` SPI。
- JSON snapshot 测试字段兼容性。
- JSON evidence 输出测试：`rawEvidence` 是未压缩数组，`evidence` 是摘要数组，`attributes.count` 为数字，`attributes.sampleDetails` 为数组。
- correctness 明细报告同步测试：修改 `relation-detector/test-fixtures/correctness` 后运行 `mvn -pl relation-detector/cli -Dtest=CorrectnessSummaryGeneratorTest -DupdateCorrectnessSummary=true -Dsurefire.failIfNoSpecifiedTests=false test` 刷新轻量索引报告 `docs/generated/correctness-test-summary.md`；需要只验收不刷新时运行 `mvn -pl relation-detector/cli -Dtest=CorrectnessSummaryGeneratorTest -DrunGeneratedReportTests=true -Dsurefire.failIfNoSpecifiedTests=false test`。
- Data Lineage 全量审核报告同步测试：修改 SQL fixture、`expected-lineage.json` 或 `StructuredDataLineageExtractor` 后运行 `mvn -pl relation-detector/cli -Dtest=DataLineageAuditGeneratorTest -DupdateDataLineageAudit=true -Dsurefire.failIfNoSpecifiedTests=false test` 刷新 `docs/parser-audit/data-lineage-full-audit.md`；需要只验收不刷新时运行 `mvn -pl relation-detector/cli -Dtest=DataLineageAuditGeneratorTest -DrunGeneratedReportTests=true -Dsurefire.failIfNoSpecifiedTests=false test`。该报告只生成审核清单，不自动写入新的 lineage golden。
- enum 序列化值稳定性测试。
- warning code 稳定性测试。
- 置信度数值允许小范围精度变化，但 subtype 和 evidence 不应无故改变。

### 5.6 运维验收测试

上线前建议按以下清单验证：

- `mvn test` 成功。该命令运行 correctness smoke，不代表全量 golden 验收；
  合并前必须执行 `mvn -T 2 -Pacceptance verify`，最终执行
  `bash relation-detector/scripts/verify-all.sh`。
- file-only 示例成功。
- MySQL 只读账号能读取 metadata。
- PostgreSQL 只读账号能读取 metadata。
- 无权限读取过程/函数时，工具给 warning 而不是崩溃。
- dataProfile 默认关闭。
- 输出 JSON 不包含 password 或真实采样值。
- 大日志文件解析失败时能定位到 source 和 line。

## 6. 后续演进建议

- 引入 picocli 替换手写 CLI 参数解析。
- YAML/JSON 已经使用 Jackson；后续如需演进，应优先补配置 schema 校验、错误定位和 CLI help，而不是再替换输出实现。
- 按 `SqlGrammarProfile` 继续引入各方言大版本 full-grammer module。新增 profile 必须补 profile selection 和独立 versioned fixture；无方言、无版本信息、unsupported version 或 full-grammer hard failure 时仍使用 token-event fallback。Oracle 后续优先补更广泛的 Oracle 官方语法覆盖，扩大当前 `INCOMPLETE_VERSIONED` generated parser 覆盖面；SQL Server 后续继续按 Microsoft Learn T-SQL reference 硬化更多 version family。
- 在现有 JUnit 5 基础上引入 AssertJ、Testcontainers 做更强断言和真实数据库集成测试。
- 增加 Maven assembly/shade 打包，生成单个可执行发行包。
- 扩展 MySQL/PostgreSQL/Oracle/SQL Server unique/index 元数据采集。
- 补强 SQL Server JDBC metadata/object/profile collector、更多 Microsoft 官方逐版本 T-SQL family 边界 fixture，以及 Oracle runtime smoke 与官方版本边界测试。
