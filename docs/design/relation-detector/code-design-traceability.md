# 代码与设计对应审视报告

本文按当前代码实现逐环节对照设计文档，列出代码入口、设计章节、测试覆盖和差异状态。状态含义：

- `MATCHED`：代码、设计、测试一致。
- `DOC_UPDATED`：本轮已按当前代码更新设计/测试说明。
- `REVIEW_NEEDED`：实现与设计有差异，或存在后续设计取舍，需要人工审视。

## 代码注释同步范围

本轮已把生产代码的结构说明同步到代码侧：

- 每个生产 package 的 `package-info.java` 用中英双语说明职责、输入输出、上游/下游和禁止承载的逻辑。
- 生产类的类级 Javadoc 用中英双语说明该文件负责什么、不负责什么、位于哪条链路。
- 关键 public 方法、核心编排方法、复杂 private helper 补充中英双语方法注释；简单 getter、record accessor 和显而易见的小工具方法不强制注释。

这些代码注释是本文的代码侧锚点。若后续新增生产 package、核心类或跨链路调用，必须同步更新对应注释、Phase 6 详细设计和本代码与设计对应表。

| 环节 | 代码入口 | 设计章节 | 测试覆盖 | 状态 | 差异 / 审视点 |
| --- | --- | --- | --- | --- | --- |
| CLI config / argument parsing | `cli.Main.MainCommand`, `SimpleYamlConfigLoader` | `phase-06 Parser mode 和 profile 选择`, `phase-08 输出与配置` | `ParserConfigRemovalTest`, `CliEndToEndGoldenTest` | DOC_UPDATED | `SimpleYamlConfigLoader` 保留历史类名，内部使用 Jackson `YAMLMapper`；CLI E2E 覆盖 YAML/CLI 到 JSON 输出。 |
| adaptor discovery | `cli.AdaptorRegistry` | `phase-03 Adaptor SPI`, `phase-06 full-grammer module 注入链` | `CliEndToEndGoldenTest`, adaptor selection tests | MATCHED | 通过 Java SPI 发现 `DatabaseAdaptor`；full-grammer module 由独立 `ServiceLoader<FullGrammerDialectModule>` 注入。 |
| ScanEngine source orchestration | `core.scan.ScanEngine.scan`, `SourceCollectorPipeline`, `StatementParsePipeline`, `StatementExecutionService` | `phase-06 ScanEngine 总编排` | `ScanEngineDatabaseDdlSourceTest`, `ScanEngineDiagnosticsTest`, `CliEndToEndGoldenTest`, `CorrectnessFixtureRunnerTest` | DOC_UPDATED | `ScanEngine` 只保留 scan 入口和 connection lifecycle；source collection、statement execution、evidence enhancement、result assembly 已拆成 pipeline/service。SQL correctness 与生产 scan 复用 `StatementExecutionService`。 |
| SQL/DDL parser selection | `core.parser.ParserBundleSelector`, `ParserBundle`, `ParserSelectionResult` | `phase-06 Parser mode 和 profile 选择` | `ParserBundleSelectorTest`, `SqlRelationParserRunnerTest`, `DdlRelationParserRunnerTest`, `ParserConfigRemovalTest`, `CliEndToEndGoldenTest` | MATCHED | `auto/full-grammer/token-event` 选择策略由一个 bundle 统一完成；SQL/DDL runner 不再分别重复 profile selection；unsupported profile 或 full-grammer hard failure fallback 到 token-event。 |
| token-event SQL parse | `core.tokenevent.CommonTokenEventStructuredSqlParser`, `TypedDialectTokenEventStructuredSqlParser`, dialect parse-tree visitors | `phase-06 SQL / DML token-event 链路` | `CommonTokenEventStructuredSqlParserTest`, `TokenEventStructuredSqlParserTest`, dialect boundary tests, correctness fixtures | MATCHED | token-event 是无 profile / unsupported version / forced token-event 的正式 fallback；生产事件来自 typed structural grammar + visitor。 |
| full-grammer SQL parse | MySQL `fullgrammer/common` + `v5_7|v8_0` parsers、PostgreSQL `fullgrammer/common` + `v16|v17|v18` parsers、Oracle `fullgrammer/common` + `v12c|v19c|v21c|v26ai` `INCOMPLETE_VERSIONED` parsers、SQL Server `fullgrammer/common` + `v2016|v2017|v2019|v2022|v2025` parsers | `phase-06 SQL / DML full-grammer 链路`, `phase-04`, `phase-05`, `phase-09`, `phase-10` | `CorrectnessFixtureRunnerTest`, `FullGrammerSqlBehaviorTest`, versioned correctness fixtures, adaptor parser architecture tests | DOC_UPDATED | 运行时选中 profile 后 full-grammer 是 primary parser；默认测试不再断言 native/delegate/bridge 过程属性，也不再用 token-event baseline 做跨 parser 兜底；PostgreSQL routine body 由 `postgres.routine` 方言级 grammar/visitor 解析。Oracle 当前是 `INCOMPLETE_VERSIONED` generated parser，不再桥接 token-event，更广泛的 Oracle 官方语法覆盖是 backlog。 |
| relationship extraction / naming evidence | `core.relation.TokenEventRelationExtractor`, `NamingEvidenceExtractor`, `NamingEvidenceMerger`, `NamingMatchEvidenceEnhancer`, `RelationshipMerger` | `phase-02 SQL 谓词关系守卫`, `phase-06 Relationship 抽取` | `CorrectnessFixtureRunnerTest`, `CorrectnessNamingEvidenceGoldenTest`, `NamingEvidenceExtractorTest`, `NamingMatchEvidenceEnhancerTest`, `RelationshipMergerEvidenceAggregationTest`, confidence tests | DOC_UPDATED | full-grammer 和 token-event 共用 relationship 语义层；top-level `namingEvidence` 是唯一命名证据池，relationship 中的 `NAMING_MATCH` 只能通过 `evidenceRef` 引用该池，不能本地重算。 |
| Data Lineage extraction | `core.lineage.StructuredDataLineageExtractor`, `ProjectionTraceResolver`, `core.lineage.model.*`, `DataLineageMerger` | `phase-06 Data Lineage v1`, `sql-lineage-resolver.md` | `ProjectionTraceResolverTest`, `DataLineageAuditGeneratorTest`, lineage golden | MATCHED | v1 只输出数据库内部字段血缘，不做 Parameter Binding；显式临时表过滤来自语法 scope；当前不保留 SQL 文本 regex helper、token span fallback 或旧 `SqlLineageResolver`。 |
| derived path inference | `core.derived.DerivedPathInferenceService`, `contracts.model.DerivedPathCandidate`, `JsonResultWriter` | `phase-06 Derived Path Evidence`, `phase-08 output` | `DerivedPathInferenceServiceTest`, `JsonResultWriterEvidenceOutputTest`, `ParserConfigRemovalTest` | MATCHED | `derivedPaths.enabled=false` 默认关闭；开启后从已合并 relationship、VALUE lineage 和 top-level namingEvidence 推导 derived 输出。relationship 内部按 referenced-by 反向遍历，但输出仍保持 FK-like 正向，不修改直接 relationship / lineage。 |
| DDL parser selection | `core.parser.DdlRelationParserRunner` | `phase-06 DDL token-event / full-grammer DDL 链路` | `DdlRelationParserRunnerTest`, `CorrectnessFixtureRunnerTest`, `CliEndToEndGoldenTest` | MATCHED | DDL 使用同一 `parser.mode` 策略；DDL hard failure 记录 warning 并继续。 |
| DDL relationship extraction | `core.relation.DdlRelationExtractionVisitor` | `phase-06 DDL Relationship 抽取` | `PostgresDdlParserTest`, `MySqlDdlParserTest`, DDL correctness fixtures | MATCHED | DDL parser 只产 `DDL_FOREIGN_KEY` / `DDL_INDEX`，relationship 转换集中在 visitor。 |
| confidence / merger / output | `RelationshipMerger`, `DataLineageMerger`, `NamingEvidenceMerger`, `ConfidenceCalculator`, `JsonResultWriter` | `phase-02`, `phase-08` | `ConfidenceScoringExamplesTest`, `RelationshipMergerEvidenceAggregationTest`, `JsonResultWriterEvidenceOutputTest`, `CliEndToEndGoldenTest` | MATCHED | Relationship、Data Lineage 和 naming evidence 都有 raw/grouped evidence 双层模型；JSON 输出由 Jackson 生成。 |
| correctness fixture / generated reports | `CorrectnessFixtureRunnerTest`, `CorrectnessFixtureExecutor`, `FixtureInputLoader`, `FixtureExecutionEngine`, `GoldenAssertion`, `GoldenWriter`, `CorrectnessSummaryGeneratorTest`, `DataLineageAuditGeneratorTest` | `test-assets-map`, `code-implementation-guide`, `phase-06 correctness / golden 验收链` | 同名测试 | DOC_UPDATED | correctness fixture 是主 golden；执行框架拆为 input loader、execution engine、golden assertion、golden writer。SQL/DDL actual 输出通过 `StatementExecutionService`，避免测试链路和生产解析链路分叉。generated summary/audit 是 Java 程序生成，不调用大模型。 |
| PostgreSQL versioned golden | `test-fixtures/correctness/postgres/v16|v17|v18`, PostgreSQL `FullGrammerDialectModule` | `phase-05 PostgreSQL versioned correctness golden`, `phase-06 PostgreSQL 版本化 correctness` | `CorrectnessFixtureRunnerTest` with version filters | DOC_UPDATED | root `postgres` 是历史兼容 baseline；`v16`、`v17`、`v18` 是严格 full-grammer version golden，不存在 `postgres/v1` 这个版本。 |
| MySQL versioned golden | `test-fixtures/correctness/mysql/v5_7|v8_0`, MySQL `FullGrammerDialectModule` | `phase-04 Correctness 与 golden 状态`, `phase-06 MySQL correctness 命名约定` | `CorrectnessFixtureRunnerTest` | DOC_UPDATED | root `mysql` 是 token-event baseline；`mysql/v5_7` 与 `mysql/v8_0` 分别是严格 MySQL 5.7 / 8.0 full-grammer version golden。 |
| Oracle versioned golden | `test-fixtures/correctness/oracle/v12c|v19c|v21c|v26ai`, Oracle `FullGrammerDialectModule` | `phase-09 Oracle adaptor`, `phase-06 Parser mode 和 profile 选择` | `CorrectnessFixtureRunnerTest` with Oracle sample-data fixtures, `OracleAdaptorParserTest`, `OracleParserArchitectureTest` | DOC_UPDATED | root `oracle` 是 token-event baseline；versioned directories 强制 Oracle full-grammer profile。当前 versioned outputs 是 `INCOMPLETE_VERSIONED` generated parser golden，不是 token-event bridge golden。 |
| SQL Server versioned golden | `test-fixtures/correctness/sqlserver/v2016|v2017|v2019|v2022|v2025`, SQL Server `FullGrammerDialectModule` | `phase-10 SQL Server adaptor`, `phase-06 Parser mode 和 profile 选择` | `CorrectnessFixtureRunnerTest`, `SqlServerParserArchitectureTest`, `SqlServerTokenEventParserTest` | DOC_UPDATED | root `sqlserver` 是 token-event baseline；versioned directories 强制 SQL Server full-grammer profile。当前 sample-data 使用保守 T-SQL 子集；2017 `STRING_AGG`、2022 `DATETRUNC` / `GENERATE_SERIES`、2025 `VECTOR(...)` 已有 grammar-level version boundary，更多 T-SQL family 仍是 backlog。 |
| 代码结构注释 | `**/package-info.java`, production class/method Javadocs | `phase-06 代码结构注释索引`, `code-implementation-guide` | compile + semantic tests | DOC_UPDATED | 注释用于说明职责边界，不作为结构守卫测试；后续新增包/关键类需同步文档。 |

## 已知实现事实

- 单条 SQL 解析当前由 `StatementExecutionService.executeSql(...)` 调用 `SqlRelationParserRunner.parseStructuredAndRelations(...)`，一次结构化解析后同时服务 relationship、Data Lineage 和 naming evidence。parser mode、fallback warning 和 diagnostics 因此在同一条 SQL 内保持一致。
- `CorrectnessFixtureRunnerTest` 不再自己拼 SQL/DDL -> relationship/lineage/namingEvidence 流程；它通过 `FixtureExecutionEngine` 复用 `StatementExecutionService` 和 `EvidenceEnhancementService`。DDL fixture 仍保持 parser-outcome 验收语义，不额外引入 scan-level metadata/naming enhancement。
- 目录、命名、迁移过程检查已从默认测试入口移除。后续如需审查旧命名残留，按 `docs/relation-detector/test-assets-map.md` 中的可选 `rg` 命令手工执行。
- `SqlGrammarProfileRegistry` 中的版本字符串解析会使用字符串/正则处理配置值；这不属于 SQL/DDL 结构解析。SQL/DDL relationship 和 lineage 不能依赖特殊表名/列名过滤。
- MySQL 5.7 / 8.0 full-grammer 当前已有独立 `test-fixtures/correctness/mysql/v5_7`、`test-fixtures/correctness/mysql/v8_0` versioned golden。root MySQL correctness 仍是 token-event baseline；三者都必须保持独立，不能互相覆盖。

## 剩余技术债

| 项目 | 状态 | 说明 |
| --- | --- | --- |
| root token-event typed visitor coverage | BACKLOG | root token-event 已使用 typed structural grammar/visitor，但在复杂 routine、业务查询和部分 DDL evidence 上仍弱于对应 full-grammer。差异不需要人工审批，后续应继续扩展 typed grammar/visitor，不能恢复 scanner 或名字过滤。 |
| Oracle 官方语法覆盖扩展 | BACKLOG | Oracle 已有 adaptor、profile 和 sample-data versioned full-grammer golden，但 full-grammer 仍是 `INCOMPLETE_VERSIONED`。后续需要按 Oracle SQL/PLSQL Reference 扩大 `.g4` / typed visitor 覆盖面，并补版本边界 fixture。 |
