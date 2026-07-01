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
| CLI config / argument parsing | `cli.Main.MainCommand`, `SimpleYamlConfigLoader` | `phase-06 Parser mode 和 profile 选择`, `phase-08 输出与配置` | `ParserConfigRemovalTest`, `CliEndToEndGoldenTest` | DOC_UPDATED | 本轮新增 CLI E2E golden，覆盖 YAML/CLI 到 JSON 输出；结构性命名检查不再作为默认测试。 |
| adaptor discovery | `cli.AdaptorRegistry` | `phase-03 Adaptor SPI`, `phase-06 full-grammer module 注入链` | `CliEndToEndGoldenTest`, adaptor selection tests | MATCHED | 通过 Java SPI 发现 `DatabaseAdaptor`；full-grammer module 由独立 `ServiceLoader<FullGrammerDialectModule>` 注入。 |
| ScanEngine source orchestration | `core.scan.ScanEngine.scan` | `phase-06 ScanEngine 总编排` | `ScanEngineDatabaseDdlSourceTest`, `ScanEngineDiagnosticsTest`, `CliEndToEndGoldenTest` | MATCHED | 文件 DDL、object files、logs、database DDL/object、metadata 分别进入对应 runner；失败转 warning 后继续。 |
| SQL/DDL parser selection | `core.parser.ParserBundleSelector`, `ParserBundle`, `ParserSelectionResult` | `phase-06 Parser mode 和 profile 选择` | `ParserBundleSelectorTest`, `SqlRelationParserRunnerTest`, `DdlRelationParserRunnerTest`, `ParserConfigRemovalTest`, `CliEndToEndGoldenTest` | MATCHED | `auto/full-grammer/token-event` 选择策略由一个 bundle 统一完成；SQL/DDL runner 不再分别重复 profile selection；unsupported profile 或 full-grammer hard failure fallback 到 token-event。 |
| token-event SQL parse | `core.tokenevent.CommonTokenEventStructuredSqlParser`, `TypedDialectTokenEventStructuredSqlParser`, dialect parse-tree visitors | `phase-06 SQL / DML token-event 链路` | `TokenEventRelationEventsTest`, dialect boundary tests, correctness fixtures | MATCHED | token-event 是无 profile / unsupported version / forced token-event 的正式 fallback；生产事件来自 typed structural grammar + visitor。 |
| full-grammer SQL parse | adaptor `fullgrammer/v8_0`、PostgreSQL `fullgrammer/common` + `v16|v17|v18` parsers、Oracle `fullgrammer/common` + `v12c|v19c|v21c|v26ai` `INCOMPLETE_VERSIONED` parsers | `phase-06 SQL / DML full-grammer 链路`, `phase-04`, `phase-05`, `phase-09` | `CorrectnessFixtureRunnerTest`, `FullGrammerSqlBehaviorTest`, versioned correctness fixtures, `OracleAdaptorParserTest`, `OracleParserArchitectureTest` | DOC_UPDATED | 运行时选中 profile 后 full-grammer 是 primary parser；默认测试不再断言 native/delegate/bridge 过程属性，也不再用 token-event baseline 做跨 parser 兜底；PostgreSQL strict version fixtures 由 `CorrectnessFixtureRunnerTest` 按各自 profile/golden 验收，并且 PL/pgSQL routine body 由 `postgres.fullgrammer.routine` 本地 grammar/visitor 解析。Oracle 当前是 `INCOMPLETE_VERSIONED` generated parser，不再桥接 token-event，更广泛的 Oracle 官方语法覆盖是 backlog。 |
| relationship extraction | `core.relation.TokenEventRelationExtractor`, `NamingMatchEvidenceEnhancer`, `RelationshipMerger` | `phase-02 SQL 谓词关系守卫`, `phase-06 Relationship 抽取` | `CorrectnessFixtureRunnerTest`, `NamingMatchEvidenceEnhancerTest`, `RelationshipMergerEvidenceAggregationTest`, confidence tests | DOC_UPDATED | full-grammer 和 token-event 共用 relationship 语义层；SQL predicate 保留 JOIN/EXISTS/IN evidence，`NAMING_MATCH` 只能附着在已有列级 predicate 上参与方向推导，不能凭空创建 relation。 |
| Data Lineage extraction | `core.lineage.TokenEventDataLineageExtractor`, `SqlLineageResolver` | `phase-06 Data Lineage v1` | `TokenEventDataLineageExtractorTest`, `DataLineageAuditGeneratorTest`, lineage golden | MATCHED | v1 只输出数据库内部字段血缘，不做 Parameter Binding；显式临时表过滤来自语法 scope。 |
| DDL parser selection | `core.parser.DdlRelationParserRunner` | `phase-06 DDL token-event / full-grammer DDL 链路` | `DdlRelationParserRunnerTest`, `CorrectnessFixtureRunnerTest`, `CliEndToEndGoldenTest` | MATCHED | DDL 使用同一 `parser.mode` 策略；DDL hard failure 记录 warning 并继续。 |
| DDL relationship extraction | `core.relation.DdlRelationExtractionVisitor` | `phase-06 DDL Relationship 抽取` | `PostgresDdlParserTest`, `MySqlDdlParserTest`, DDL correctness fixtures | MATCHED | DDL parser 只产 `DDL_FOREIGN_KEY` / `DDL_INDEX`，relationship 转换集中在 visitor。 |
| confidence / merger / output | `RelationshipMerger`, `ConfidenceCalculator`, `JsonResultWriter` | `phase-02`, `phase-08` | `ConfidenceScoringExamplesTest`, `RelationshipMergerEvidenceAggregationTest`, `JsonResultWriterEvidenceOutputTest`, `CliEndToEndGoldenTest` | MATCHED | Data Lineage confidence 不参与 relationship confidence；JSON schema 未改变。 |
| correctness fixture / generated reports | `CorrectnessFixtureRunnerTest`, `CorrectnessSummaryGeneratorTest`, `DataLineageAuditGeneratorTest` | `test-assets-map`, `code-implementation-guide` | 同名测试 | DOC_UPDATED | correctness fixture 是主 golden；generated summary/audit 是 Java 程序生成，不调用大模型。 |
| PostgreSQL versioned golden | `test-fixtures/correctness/postgres/v16|v17|v18`, PostgreSQL `FullGrammerDialectModule` | `phase-05 PostgreSQL versioned correctness golden`, `phase-06 PostgreSQL 版本化 correctness` | `CorrectnessFixtureRunnerTest` with version filters | DOC_UPDATED | root `postgres` 是历史兼容 baseline；`v16`、`v17`、`v18` 是严格 full-grammer version golden，不存在 `postgres/v1` 这个版本。 |
| MySQL versioned golden | `test-fixtures/correctness/mysql/v8_0`, MySQL `FullGrammerDialectModule` | `phase-04 Correctness 与 golden 状态`, `phase-06 MySQL correctness 命名约定` | `CorrectnessFixtureRunnerTest` | DOC_UPDATED | root `mysql` 是 token-event baseline；`mysql/v8_0` 是严格 MySQL 8.0 full-grammer version golden。 |
| Oracle versioned golden | `test-fixtures/correctness/oracle/v12c|v19c|v21c|v26ai`, Oracle `FullGrammerDialectModule` | `phase-09 Oracle adaptor`, `phase-06 Parser mode 和 profile 选择` | `CorrectnessFixtureRunnerTest` with Oracle sample-data fixtures, `OracleAdaptorParserTest`, `OracleParserArchitectureTest` | DOC_UPDATED | root `oracle` 是 token-event baseline；versioned directories 强制 Oracle full-grammer profile。当前 versioned outputs 是 `INCOMPLETE_VERSIONED` generated parser golden，不是 token-event bridge golden。 |
| 代码结构注释 | `**/package-info.java`, production class/method Javadocs | `phase-06 代码结构注释索引`, `code-implementation-guide` | compile + semantic tests | DOC_UPDATED | 注释用于说明职责边界，不作为结构守卫测试；后续新增包/关键类需同步文档。 |

## 已知实现事实

- `ScanEngine.safeParseStatement(...)` 当前调用 `SqlRelationParserRunner.parseStructuredAndRelations(...)`，一次结构化解析后同时服务 relationship 与 Data Lineage。parser mode、fallback warning 和 diagnostics 因此在同一条 SQL 内保持一致。
- `CorrectnessFixtureRunnerTest` 为避免 warning 计数污染，lineage-only structural parse 使用 null context。这是测试实现细节，不影响正式 CLI / ScanEngine 链路。
- 目录、命名、迁移过程检查已从默认测试入口移除。后续如需审查旧命名残留，按 `docs/relation-detector/test-assets-map.md` 中的可选 `rg` 命令手工执行。
- `SqlGrammarProfileRegistry` 中的版本字符串解析会使用字符串/正则处理配置值；这不属于 SQL/DDL 结构解析。SQL/DDL relationship 和 lineage 不能依赖特殊表名/列名过滤。
- MySQL 8.0 full-grammer 当前已有独立 `test-fixtures/correctness/mysql/v8_0` versioned golden。root MySQL correctness 仍是 token-event baseline；两者都必须保持独立，不能互相覆盖。

## 剩余技术债

| 项目 | 状态 | 说明 |
| --- | --- | --- |
| root token-event typed visitor coverage | BACKLOG | root token-event 已使用 typed structural grammar/visitor，但在复杂 routine、业务查询和部分 DDL evidence 上仍弱于对应 full-grammer。差异不需要人工审批，后续应继续扩展 typed grammar/visitor，不能恢复 scanner 或名字过滤。 |
| Oracle 官方语法覆盖扩展 | BACKLOG | Oracle 已有 adaptor、profile 和 sample-data versioned full-grammer golden，但 full-grammer 仍是 `INCOMPLETE_VERSIONED`。后续需要按 Oracle SQL/PLSQL Reference 扩大 `.g4` / typed visitor 覆盖面，并补版本边界 fixture。 |
