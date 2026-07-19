# 代码与设计对应审视报告

本文按当前代码实现逐环节对照设计文档，列出代码入口、设计章节、测试覆盖和差异状态。状态含义：

- `MATCHED`：代码、设计、测试一致。
- `DOC_UPDATED`：本轮已按当前代码更新设计/测试说明。
- `FOCUSED_GREEN`：实现与设计一致，并已通过对应 focused contract；发布级结论仍由完整验收清单给出。
- `PARTIAL`：主链路已经落地，但仍有已定位的实现缺口，不能宣称完全匹配。
- `REVIEW_NEEDED`：实现与设计有差异，或存在后续设计取舍，需要人工审视。
- `BACKLOG`：明确不属于当前已实现能力的扩展项，不影响已经冻结的契约。

## Final Convergence Closure State

This generated-free table records the repository's current frozen closure matrix. Historical
implementation plans remain available through Git history and are not copied into the current
documentation tree. The matrix is independent of the technical statuses below.

| Closure ID | State |
| --- | --- |
| ID-01 | CLOSED |
| ID-02 | CLOSED |
| ID-03 | CLOSED |
| ID-04 | CLOSED |
| ID-05 | CLOSED |
| EV-01 | CLOSED |
| EV-02 | CLOSED |
| EV-03 | CLOSED |
| EV-04 | CLOSED |
| EV-05 | CLOSED |
| EV-06 | CLOSED |
| CT-01 | CLOSED |
| CT-02 | CLOSED |
| CT-03 | CLOSED |
| CT-04 | CLOSED |
| CT-05 | CLOSED |
| CT-06 | CLOSED |
| TG-01 | CLOSED |
| TG-02 | CLOSED |
| TG-03 | CLOSED |

All 20 frozen IDs are closed after focused and reverse-audit gates, isolated full correctness,
the 19-category/38-JSON sample-data CLI matrix, semantic observation parity, and release-manifest
integrity checks passed. Environment-dependent runtime smoke and explicitly out-of-scope parser or
profiling improvements remain backlog items; they do not reopen this closure matrix.

## 代码注释同步范围

生产代码的 package、命名类型和方法级结构说明已同步到代码侧；SPI v6、live collector、preflight 和
profiling outcome 的关键入口 Javadoc 已同步。架构测试使用 JDK compiler/doc-tree API 检查全部手写
生产 package 的中英双语职责契约，以及生产类型和大型编排方法的具体责任说明。后续修改必须遵守以下规则：

- 每个生产 package 的 `package-info.java` 必须以中英双语说明 responsibility、inputs、outputs、upstream/downstream 和 forbidden responsibility。架构测试验证这些语义类别、最小具体内容及禁用模板；描述是否准确仍需代码评审。
- 顶层 public/protected 手写类型及编排职责类的类级 Javadoc 必须使用 `CN:` / `EN:` 具体说明职责和禁止边界。
- 编排职责类中有效代码超过 40 行的非 override 方法必须说明输入效果、输出/副作用与失败边界；简单 getter、record accessor 和小工具方法不在自动门禁范围内。

若后续新增生产 package、核心类或跨链路调用，必须同步更新对应注释、Phase 6 详细设计和本代码与设计对应表；
`DialectGrammarArchitectureTest` 与 `SemanticDocumentationArchitectureTest` 会拒绝缺少双语 package 契约、缺少双语类型/大方法 Javadoc，或使用已禁止通用模板的手写生产代码。门禁不覆盖 generated、accessor 或显而易见的小 helper，也不会代替语义评审。

| 环节 | 代码入口 | 设计章节 | 测试覆盖 | 状态 | 差异 / 审视点 |
| --- | --- | --- | --- | --- | --- |
| CLI config / argument parsing | `cli.Main.MainCommand`, `SimpleYamlConfigLoader`, `SingleScanRunner`, `BatchCommand` | `phase-06 Parser mode 和 profile 选择`, `phase-08 输出与配置` | `ParserConfigRemovalTest`, `CliErrorCodeContractTest`, `BatchCommandIntegrationTest`, `BatchSchedulerTest` | MATCHED | argument parsing、config file read、YAML syntax/shape、adaptor/input/connection/runtime 和 output write failure 已有固定 `ErrorCode` 与脱敏 stderr。执行期 `LiveSourceConfigurationException` 由 single-scan 映射为 `CONFIG_FORMAT_ERROR`；batch case 保留同一 typed code，整体仍返回 `BATCH_PARTIAL_FAILURE`。 |
| adaptor discovery | `cli.AdaptorRegistry`, `core.scan.AdaptorContractValidator` | `phase-03 Adaptor SPI`, `phase-06 full-grammar module 注入链` | `AdaptorApiVersionTest`, `CommonAdaptorRegistryTest`, `CliEndToEndGoldenTest` | FOCUSED_GREEN | 通过 Java SPI 发现 `DatabaseAdaptor`；registry 与 direct `ScanEngine` 复用同一完整 validator 检查 SPI v6、database type 和显式 adaptor id，CLI 边界将 validator failure 包装为既有 `AdaptorException`。v5 外部插件在 registry 阶段被明确拒绝并提示重新编译；v6 `AdaptorParsers.scriptFramer()` 提供必需的 `DialectScriptFramer`，full-grammar module 由独立 `ServiceLoader<FullGrammarDialectModule>` 注入。 |
| ScanEngine source orchestration | `core.scan.ScanEngine.scan`, `ScanInputPathResolver`, `SourceCollectorPipeline`, `StatementParsePipeline`, `StatementExecutionService` | `phase-06 ScanEngine 总编排`, `phase-08 execution 配置` | `FinalScanContractTest`, `ScanInputPathResolverTest`, `ScanEngineDatabaseDdlSourceTest`, `ScanEngineDiagnosticsTest`, `ScanExecutionParallelismTest`, `CliEndToEndGoldenTest`, `CorrectnessFixtureRunnerTest` | FOCUSED_GREEN | `ScanConfig.resolve(baseDirectory)` 是 `files + paths + include` 的唯一展开入口；CLI 使用配置文件父目录，direct API 无参入口使用当前工作目录。运行态只消费稳定排序、规范绝对路径且去重的 `*Files`，missing、non-regular 或 unreadable 输入会在 scan 前明确失败。 |
| SQL/DDL parser selection | `core.parser.ParserBundleSelector`, `ParserBundle`, `ParserSelectionResult` | `phase-06 Parser mode 和 profile 选择` | `ParserBundleSelectorTest`, `SqlRelationParserRunnerTest`, `DdlRelationParserRunnerTest`, `ParserConfigRemovalTest`, `CliEndToEndGoldenTest` | MATCHED | `auto/full-grammar/token-event` 选择策略由一个 bundle 统一完成；SQL/DDL runner 不再分别重复 profile selection；unsupported profile 或 full-grammar hard failure fallback 到 token-event。 |
| token-event SQL parse | `core.tokenevent.CommonTokenEventStructuredSqlParser`, `TypedDialectTokenEventStructuredSqlParser`, dialect parse-tree visitors | `phase-06 SQL / DML token-event 链路` | `CommonTokenEventStructuredSqlParserTest`, `CommonTokenEventStructuredSqlParserAdditionalTest`, dialect boundary tests, correctness fixtures | MATCHED | legacy `TokenEventStructuredSqlParser` wrapper 已删除；common parser直接拥有生命周期，方言 parser 继承 typed lifecycle，生产事件来自 typed structural grammar + visitor。 |
| full-grammar SQL parse | MySQL `fullgrammar/common` + `v5_7|v8_0` parsers、PostgreSQL `fullgrammar/common` + `v16|v17|v18` parsers、Oracle `fullgrammar/common` + `v12c|v19c|v21c|v26ai` `INCOMPLETE_VERSIONED` parsers、SQL Server `fullgrammar/common` + `v2016|v2017|v2019|v2022|v2025` parsers | `phase-06 SQL / DML full-grammar 链路`, `phase-04`, `phase-05`, `phase-09`, `phase-10` | `CorrectnessFixtureRunnerTest`, `FullGrammarSqlBehaviorTest`, versioned correctness fixtures, adaptor parser architecture tests | DOC_UPDATED | 运行时选中 profile 后 full-grammar 是 primary parser；它只消费 typed generated context，不使用 rule name、reflection 或 raw SQL regex 推断结构；默认测试不再断言 native/delegate/bridge 过程属性，也不再用 token-event baseline 做跨 parser 兜底。PostgreSQL routine body由`postgres.plpgsql.v16|v17|v18`同版本shell解析，token-event使用独立`postgres.plpgsql.tokenevent` shell。Oracle当前是`INCOMPLETE_VERSIONED` generated parser，不再桥接token-event，更广泛的Oracle官方语法覆盖是backlog。 |
| dialect script framing | `core.script.StructuredScriptFramer`, five `*ScriptSlicePlanner`, `ScriptFileExtractor`, dialect `*ScriptFramer` | `phase-03 DialectScriptFramer`, `phase-04`, `phase-05`, `phase-09`, `phase-10` | dialect script framer tests, architecture test | MATCHED | MySQL `DELIMITER`、PostgreSQL dollar quote、Oracle single-line slash、SQL Server single-line `GO` 和 common compound object framing 分别由独立 planner 处理；`StructuredScriptFramer` 只编排 marker、slice 选择及 statement/provenance 装配。 |
| relationship extraction / naming evidence | `core.relation.StructuredRelationshipExtractor`, `RelationshipAliasSupport`, `RelationshipMerger`; `core.naming.NamingEvidenceExtractor`, `NamingRuleEngine`, `NamingRuleSet`, `NamingEvidenceMerger`, `NamingMatchEvidenceEnhancer` | `phase-02 SQL 谓词关系守卫`, `phase-06 Relationship 抽取` | `CorrectnessFixtureRunnerTest`, `CorrectnessNamingEvidenceGoldenTest`, `NamingEvidenceExtractorTest`, `NamingRuleEngineCatalogIdentityTest`, `RelationshipMergerEvidenceAggregationTest`, `FinalEvidenceContractTest` | MATCHED | `NamingRuleEngine` 是唯一命名启发式边界。relationship direct fact、naming rule 分组、pool 和 merger 已复用 dialect-aware canonical endpoint key；完整、排序后的 `conditions` 数组进入 candidate/observation/fingerprint identity，conditional summary 聚合全部 guard，并保留被 unguarded observation 覆盖的 guarded raw evidence。公开 `normalizedKey()` 继续承担 evidence reference/display 兼容职责。grouped evidence 只保留 deep-consensus attributes，冲突 provenance 留在 raw evidence。 |
| endpoint / metadata identity | `TableId`, `Endpoint`, `CanonicalEndpointKey`, `CanonicalEndpointKeyProvider`, `AliasSymbolTable`, `StatementParsePipeline`, metadata fact records | `phase-02`, `phase-06 Hard Boundaries` | catalog identity、naming、projection、metadata enhancement tests | MATCHED | `TableId` 构造一致性、MySQL `database.table` catalog 轴、source definition namespace、direct fact identity、profile namespace 和 derived path bridge 均复用 dialect-aware canonical endpoint identity；跨 catalog 同名表负向测试覆盖 relationship、lineage 和 naming path。 |
| semantic physical endpoint | `semantic.model.PhysicalEndpointRef`, `reader.PhysicalEndpointJsonReader` | semantic reader / evidence boundary | `PhysicalEndpointRefTest`, `PhysicalEndpointJsonReaderTest`, semantic normalization tests | MATCHED | table 和 column 使用不同构造入口；JSON 解析留在 reader，中性模型不依赖 Jackson。catalog/schema 多段名称只在 column factory 的最后一个分隔点拆分，缺失列段明确失败。 |
| semantic CLI orchestration | `semantic.cli.Main`, command handlers, `SemanticKgBuildService` | semantic CLI build / extract / e2e flow | `SemanticCliIntegrationTest` | MATCHED | `Main` 只解析、分发和渲染脱敏错误；build 与 e2e 共用唯一的 bundle→evidence graph→KG→artifact service，handler 不复制图装配流程。 |
| MySQL metadata inventory | `MySqlMetadataCollector`, four `MySql*MetadataReader`, `MySqlMetadataReaderSupport` | adaptor live metadata boundary | `MySqlMetadataCollectorFactsTest` | MATCHED | collector 只编排 table/column/constraint/index reader；各 catalog family 独立失败并保留已成功 inventory，组合 constraint/index 继续按 ordinal 重建。 |
| Data Lineage extraction | `core.lineage.StructuredDataLineageExtractor`, `ProjectionTraceResolver`, `core.lineage.model.*`, `DataLineageMerger` | `phase-06 Data Lineage v1`, `sql-lineage-resolver.md` | `ProjectionTraceResolverTest`, `DataLineageMergerTest`, lineage golden | DOC_UPDATED | 正式链路只消费 structured events。statement source namespace进入 alias/known-physical 解析；`DataLineageMerger` 按 dialect-aware canonical source/target identity 去重，而输出 source 顺序仍按公开 key 稳定排序以保持 JSON 兼容。 |
| derived path inference | `core.derived.DerivedPathInferenceService`, `contracts.model.DerivedPathCandidate`, `JsonResultWriter` | `phase-06 Derived Path Evidence`, `phase-08 output` | `DerivedPathInferenceServiceTest`, `JsonResultWriterEvidenceOutputTest`, `ParserConfigRemovalTest` | MATCHED | `derivedPaths.enabled=false` 默认关闭；路径方向、canonical path fact identity、edge variant observation 合并、non-adjacent re-entry 防护和 table identity bridge 均使用完整 dialect-aware catalog/schema/table identity。公开 path/evidence 文本仍保留兼容 endpoint 表示。 |
| DDL parser selection | `core.parser.DdlRelationParserRunner` | `phase-06 DDL token-event / full-grammar DDL 链路` | `DdlRelationParserRunnerTest`, `CorrectnessFixtureRunnerTest`, `CliEndToEndGoldenTest` | MATCHED | DDL 使用同一 `parser.mode` 策略；DDL hard failure 记录 warning 并继续。 |
| DDL relationship extraction | `core.relation.DdlRelationExtractionVisitor`, `core.naming.NamingEvidenceExtractor` | `phase-06 DDL Relationship 抽取` | `PostgresDdlParserTest`, `MySqlDdlParserTest`, DDL correctness fixtures | DOC_UPDATED | DDL parser 产 `DDL_FOREIGN_KEY` / `DDL_INDEX` / `DDL_COLUMN`；relationship 转换集中在 visitor，column inventory 进入 naming pool。endpoint pair 只执行一次命名匹配，但双方所有 DDL/metadata/structural observations 都合并为 raw provenance，精确重复用 `occurrenceCount` 折叠。 |
| adaptor live capabilities | `AdaptorContractValidator`, `DatabaseAdaptor.capabilities`, grouped `collectors()/parsers()/profiling()`, `ScanCapabilityValidator`, `DatabaseAdaptor.resolveLiveScope` | `phase-03 Adaptor API 和 SPI`, adaptor phase docs | `ScanCapabilityValidatorTest`, capability、live collector 与 profile-only catalog tests | MATCHED | SPI/type/id、live capability、collector 及对应 consumer 均在 JDBC 前验证；live DDL 同时要求 structured DDL parser，live objects 同时要求 structured SQL parser，profile 同时要求 profiler。四数据库真实权限/版本 runtime smoke 是环境性验证 backlog，不是 capability/preflight 实现缺口。 |
| metadata/profile direction gates | `MetadataEvidenceEnhancer`, `DataProfileCandidateGenerator`, `IndexEvidencePolicy` | `phase-02`, `phase-07` | metadata/profile candidate tests | MATCHED | 组合 PK/UNIQUE 不证明单列唯一；普通组合索引仅物理首列可作 lookup / `SOURCE_INDEX` 支持，不单独决定方向。Metadata/DDL observation 交给 merger 按完整 identity 折叠，不再按 type 提前丢弃。 |
| profiler diagnostics | `JdbcDataProfilerTemplate`, `JdbcExceptionClassifier`, `LiveDiagnosticSanitizer`, `DataProfilePipeline` | `phase-07 安全与权限` | core/Oracle/SQL Server profiler tests | MATCHED | warning 使用固定安全消息，只保留 endpoint、SQLState、vendor code、exception class 和 profiler source；profiler 通过调用方传入 Oracle 1031 或 SQL Server 229/916。真实 driver/version 仍需环境性 smoke。 |
| live collector diagnostics | `ScanEngine`, `DiagnosticWarnings`, `LiveDiagnosticSanitizer`, `JdbcExceptionClassifier`, dialect metadata/object/database-DDL collectors | `phase-08 Warning 设计` | scan/collector diagnostics tests | MATCHED | profiler 与普通 live collectors 共用 fixed-message sanitizer。共享 classifier 只识别可移植 JDBC 类型和 SQLState；Oracle/SQL Server adaptor 显式提供自己的 vendor code。Pipeline 对 null/blank definition、null element 和 null list 均输出 `DEFINITION_UNAVAILABLE` 并阻止下游解析。 |
| profiler metrics | `JdbcDataProfilerTemplate`, `DataProfileMetrics`, `DataProfileEvidenceBuilder`, `NegativeProfileEvidencePolicy`, `ProfileEvidenceContractValidator`, `DataProfilePipeline`, dialect `IdentifierQuoter` modes | `phase-07 指标` | evidence-builder、pipeline contract、dialect fake-JDBC、profile-only catalog tests | MATCHED | 内置 live query 独立测量 source non-null rows、source distinct、matched distinct 和 target distinct。core consumer原子校验 status、三类 profile evidence allowlist、`DATA_PROFILE` source type和负向策略；conditional/polymorphic 判断覆盖 candidate summary、structural evidence 与 raw evidence attributes。 |
| runtime config validation | `ScanConfigurationValidator`, `NamingRuleSetResolver`, `ScanConfig.resolve`, `ResolvedScanConfig`, `ScanEngine.scan`, `ScanCapabilityValidator` | `phase-08 配置校验` | `ScanConfigurationValidatorTest`, `ResolvedScanConfigTest`, `RuntimeConfigurationCliTest`, `BatchCommandIntegrationTest`, capability tests | MATCHED | source、live/JDBC、parser、derived、execution/profile limit 和 confidence 在 capability/JDBC 前校验。core 统一解析 naming rule files、合并 system/file/inline typed rules并拒绝重复 ID；CLI 仅解析相对路径，parser compatibility view 只复制最终 typed rules，避免二次加载。 |
| offline profile options | `ScanYamlConfigDto.DataProfile`, `SimpleYamlConfigLoader` | `phase-07 live-only profiling` | `OfflineProfileConfigurationRemovalTest`, SPI architecture tests | MATCHED | runtime、SPI v6、示例和证据已删除 offline INSERT sample 选项与分支。YAML DTO 仅保留四个旧字段作拒绝哨兵，发现后返回 `CONFIG_FORMAT_ERROR`，不映射到 runtime 类型。 |
| confidence / merger / output | `RelationshipMerger`, `DataLineageMerger`, `NamingEvidenceMerger`, `ConfidenceCalculator`, `JsonResultWriter` | `phase-02`, `phase-08` | `ConfidenceScoringExamplesTest`, `RelationshipMergerEvidenceAggregationTest`, `DataLineageMergerTest`, `JsonResultWriterEvidenceOutputTest` | MATCHED | raw/grouped 模型与 catalog 输出已落地。relationship、lineage 和 naming grouped evidence 使用 deep-consensus attributes；所有 direct/derived summary 通过统一 occurrence helper 累加折叠 evidence 的 `occurrenceCount`，而 repeated-observation confidence 仍按独立 observation 计数。 |
| correctness fixture / generated reports | `CorrectnessFixtureRunnerTest`, `CorrectnessFixtureExecutor`, `FixtureInputLoader`, `FixtureExecutionEngine`, `GoldenAssertion`, `GoldenWriter`, `CorrectnessSummaryGeneratorTest`, `DataLineageAuditGeneratorTest` | `test-assets-map`, `code-implementation-guide`, `phase-06 correctness / golden 验收链` | 同名测试 | DOC_UPDATED | correctness fixture 是主 golden；执行框架拆为 input loader、execution engine、golden assertion、golden writer。SQL/DDL actual 输出通过 `StatementExecutionService`，避免测试链路和生产解析链路分叉。generated summary/audit 是 Java 程序生成，不调用大模型。 |
| PostgreSQL versioned golden | `test-fixtures/correctness/postgres/v16|v17|v18`, PostgreSQL `FullGrammarDialectModule` | `phase-05 PostgreSQL versioned correctness golden`, `phase-06 PostgreSQL 版本化 correctness` | `CorrectnessFixtureRunnerTest` with version filters | DOC_UPDATED | root `postgres` 是历史兼容 baseline；`v16`、`v17`、`v18` 是严格 full-grammar version golden，不存在 `postgres/v1` 这个版本。 |
| MySQL versioned golden | `test-fixtures/correctness/mysql/v5_7|v8_0`, MySQL `FullGrammarDialectModule` | `phase-04 Correctness 与 golden 状态`, `phase-06 MySQL correctness 命名约定` | `CorrectnessFixtureRunnerTest` | DOC_UPDATED | root `mysql` 是 token-event baseline；`mysql/v5_7` 与 `mysql/v8_0` 分别是严格 MySQL 5.7 / 8.0 full-grammar version golden。 |
| Oracle versioned golden | `test-fixtures/correctness/oracle/v12c|v19c|v21c|v26ai`, Oracle `FullGrammarDialectModule` | `phase-09 Oracle adaptor`, `phase-06 Parser mode 和 profile 选择` | `CorrectnessFixtureRunnerTest` with Oracle sample-data fixtures, `OracleAdaptorParserTest`, `OracleParserArchitectureTest` | DOC_UPDATED | root `oracle` 是 token-event baseline；versioned directories 强制 Oracle full-grammar profile。当前 versioned outputs 是 `INCOMPLETE_VERSIONED` generated parser golden，不是 token-event bridge golden。 |
| SQL Server versioned golden | `test-fixtures/correctness/sqlserver/v2016|v2017|v2019|v2022|v2025`, SQL Server `FullGrammarDialectModule` | `phase-10 SQL Server adaptor`, `phase-06 Parser mode 和 profile 选择` | `CorrectnessFixtureRunnerTest`, `SqlServerParserArchitectureTest`, `SqlServerTokenEventParserTest` | DOC_UPDATED | root `sqlserver` 是 token-event baseline；versioned directories 强制 SQL Server full-grammar profile。当前 sample-data 使用保守 T-SQL 子集；2017 `STRING_AGG`、2022 `DATETRUNC` / `GENERATE_SERIES`、2025 `VECTOR(...)` 已有 grammar-level version boundary，更多 T-SQL family 仍是 backlog。 |
| 代码结构注释 | `**/package-info.java`, production class/method Javadocs | `phase-06 代码结构注释索引`, `code-implementation-guide` | `DialectGrammarArchitectureTest`, `SemanticDocumentationArchitectureTest` | MATCHED | relation-detector 与 semantic-layer 均要求双语 package、public/protected 顶层类型、编排类及大型编排方法；泛化模板为零。门禁仍只能证明结构和已知模板，不能替代调用链语义评审。 |
| 大型语义类职责门禁 | parser semantic analyzers/support/extractors/resolvers/mergers/framers | `phase-06 Visitor 与语义 helper 职责边界` | `DialectGrammarArchitectureTest` semantic/framer gates | MATCHED | 400/450、framer 200 和 planner 250 的有效代码行门禁已覆盖目标职责且无永久 allowlist。top-level record 排除通过 JDK compiler AST 检查真实顶层声明；普通类注释或字符串中的伪 `record` 不能绕过门禁。 |
| repository documentation / reachability | `docs/design`, `docs/guides`, `docs/parser-audit`, `audit-java-reachability.sh` | design index, guide and current-audit contracts | `RepositoryDocumentationContractTest`, reachability report review | MATCHED | 当前 HEAD 只维护设计、运行指南和登记过的现行 parser 审计；历史计划与快照仅保留在 Git 历史，完整生成报告属于 verification artifact。reachability 脚本覆盖 relation-detector、semantic-layer 与 grammar 手写 Java，仅报告候选，不自动删除 SPI、ServiceLoader、generated 或模型容器。 |

## 已知实现事实

- 单条 SQL 解析当前由 `StatementExecutionService.executeSql(...)` 调用 `SqlRelationParserRunner.parseStructuredAndRelations(...)`，一次结构化解析后同时服务 relationship 与 Data Lineage。SQL naming rule 随后只在 scan-level `EvidenceEnhancementService` 对合并后的 relationship candidates 执行一次。parser mode、fallback warning 和 diagnostics 在 relationship/lineage 链路中保持一致，naming 复用其结构化 relationship evidence。
- `CorrectnessFixtureRunnerTest` 不再自己拼 SQL/DDL -> relationship/lineage/namingEvidence 流程；它通过 `FixtureExecutionEngine` 复用 `StatementExecutionService` 和 `EvidenceEnhancementService`。DDL fixture 仍保持 parser-outcome 验收语义，不额外引入 scan-level metadata/naming enhancement。
- 目录、命名、迁移过程检查已从默认测试入口移除。后续如需审查旧命名残留，按 `docs/guides/relation-detector/test-assets-map.md` 中的可选 `rg` 命令手工执行。
- `SqlGrammarProfileRegistry` 中的版本字符串解析会使用字符串/正则处理配置值；这不属于 SQL/DDL 结构解析。SQL/DDL relationship 和 lineage 不能依赖特殊表名/列名过滤。
- MySQL 5.7 / 8.0 full-grammar 当前已有独立 `test-fixtures/correctness/mysql/v5_7`、`test-fixtures/correctness/mysql/v8_0` versioned golden。root MySQL correctness 仍是 token-event baseline；三者都必须保持独立，不能互相覆盖。

## 剩余技术债

| 项目 | 状态 | 说明 |
| --- | --- | --- |
| root token-event typed visitor coverage | BACKLOG | root token-event 已使用 typed structural grammar/visitor，但在复杂 routine、业务查询和部分 DDL evidence 上仍弱于对应 full-grammar。差异不需要人工审批，后续应继续扩展 typed grammar/visitor，不能恢复 scanner 或名字过滤。 |
| Oracle 官方语法覆盖扩展 | BACKLOG | Oracle 已有 adaptor、profile 和 sample-data versioned full-grammar golden，但 full-grammar 仍是 `INCOMPLETE_VERSIONED`。后续需要按 Oracle SQL/PLSQL Reference 扩大 `.g4` / typed visitor 覆盖面，并补版本边界 fixture。 |
| production Javadoc / package documentation | MATCHED | compiler/doc-tree 门禁验证双语 package 契约、具体边界说明和禁用模板；core profile、MySQL/PostgreSQL version package 与 SQL Server DDL structural-skeleton contract 已按当前实现修正。门禁只能验证结构与已知模板，描述是否准确仍需调用链代码评审。 |
| table identity 与 fact canonicalization | MATCHED | `TableId`、dialect canonical key、source namespace、live profile namespace 和 derived bridge 已使用完整身份；跨 catalog/schema、quoted case 和 profile-only mismatch 由 focused tests 保护。 |
| 直接 API 文件路径 | FOCUSED_GREEN | `ScanInputPathResolver` 是 `files + paths + include` 的唯一展开 owner；CLI 以配置文件父目录解析，core direct API 以显式或当前工作目录解析。missing、non-regular 和 unreadable 输入在 scan 前 fail-fast。 |
| relationship 条件与 provenance merge | MATCHED | 完整 guard identity、conditional/unconditional summary 与 polymorphic 计算已通过 focused tests；grouped evidence 顶层只保留所有 observation 一致的 consensus attributes。 |
| 大型语义类职责门禁 | MATCHED | 行数和 planner/helper 边界已实现；top-level record 豁免使用 JDK compiler AST 判断，并以普通类注释/字符串中的伪 `record` 和真实 record DTO 做对抗测试。 |
| profiler warning 脱敏与权限分类 | MATCHED | 脱敏和 profiler 契约已实现；共享规则只识别 JDBC 类型/SQLState，方言 vendor code 由对应 adaptor 限定。 |
| live profiler 指标完整性 | MATCHED | 四项计数、方言合法 table qualification 与 profile-only catalog 校验已闭环；`DataProfilePipeline` 拒绝外部 SPI 的越界 evidence，并从 candidate、structural evidence 与 raw evidence 共同计算 pre-merge conditional/polymorphic guard。 |
| runtime 配置校验 | MATCHED | live/JDBC、source、parser、derived、execution/profile limit、confidence 与 naming rule files 均由 core 最终配置入口统一校验；direct API、CLI 和 batch 使用同一规则集。 |
| offline profile 配置 | MATCHED | runtime 和 SPI v6 已删除无 producer 的 offline INSERT sample 配置与 metrics 分支；YAML 旧字段仅作显式拒绝哨兵。 |
| MySQL live object parser completeness | MATCHED | `information_schema` 只用于枚举对象身份；完整 parser 输入来自 `SHOW CREATE PROCEDURE/FUNCTION/VIEW/TRIGGER/EVENT`，单对象失败产生安全 warning 并跳过。 |
| live database-DDL structural contract | MATCHED | Oracle DDL 使用 `DBMS_METADATA.GET_DDL`；SQL Server/PostgreSQL 明确输出关系解析用结构骨架，组合 FK 分别按 `constraint_column_id` / ordinality 配对并保留 catalog。完整可回放 DDL（type modifier、default、identity/generated/computed/collation）不属于当前骨架契约。 |
| live collector warning sanitization | MATCHED | 所有 live JDBC failures 经共享 classifier/sanitizer 输出固定消息，不包含 URL、SQL 或 driver message。 |
| live warning object context | MATCHED | 四个 adaptor及 pipeline 均安全处理 null/blank definition；第三方 collector 返回 null 元素或 null list 时生成通用 `DEFINITION_UNAVAILABLE`，不会静默过滤或误归为 collect-failed。 |
| observation summary count | MATCHED | relationship、lineage、naming 和 derived path 的 direct/derived summary 均通过统一 helper 累加 `occurrenceCount`；折叠 multiplicity 不参与 repeated-observation confidence。 |
| CLI error-code contract | MATCHED | `CliErrorCodeContractTest` 覆盖 single-scan 执行期 live namespace 配置错误映射；batch tests证明 case保留 `CONFIG_FORMAT_ERROR`，整体保持 partial-failure exit 13，且错误文本固定脱敏。 |
