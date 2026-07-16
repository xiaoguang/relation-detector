# 代码与设计对应审视报告

本文按当前代码实现逐环节对照设计文档，列出代码入口、设计章节、测试覆盖和差异状态。状态含义：

- `MATCHED`：代码、设计、测试一致。
- `DOC_UPDATED`：本轮已按当前代码更新设计/测试说明。
- `PARTIAL`：主链路已经落地，但仍有已定位的实现缺口，不能宣称完全匹配。
- `REVIEW_NEEDED`：实现与设计有差异，或存在后续设计取舍，需要人工审视。

## Final Convergence Closure State

This generated-free table records the frozen closure matrix from
`docs/superpowers/specs/2026-07-16-relation-detector-final-convergence-design.md`.
It is independent of the technical statuses below.

| Closure ID | State |
| --- | --- |
| ID-01 | FOCUSED_GREEN |
| ID-02 | FOCUSED_GREEN |
| ID-03 | FOCUSED_GREEN |
| ID-04 | FOCUSED_GREEN |
| ID-05 | FOCUSED_GREEN |
| EV-01 | FOCUSED_GREEN |
| EV-02 | FOCUSED_GREEN |
| EV-03 | FOCUSED_GREEN |
| EV-04 | FOCUSED_GREEN |
| EV-05 | FOCUSED_GREEN |
| EV-06 | FOCUSED_GREEN |
| CT-01 | RED_PROVEN |
| CT-02 | OPEN |
| CT-03 | OPEN |
| CT-04 | OPEN |
| CT-05 | OPEN |
| CT-06 | OPEN |
| TG-01 | OPEN |
| TG-02 | OPEN |
| TG-03 | OPEN |

## 代码注释同步范围

生产代码的 package、命名类型和方法级结构说明已同步到代码侧；SPI v5、live collector、preflight 和
profiling outcome 的关键入口 Javadoc 已同步。架构测试使用 JDK compiler/doc-tree API 检查全部手写
生产 package 的中英双语职责契约，以及生产类型和大型编排方法的具体责任说明。后续修改必须遵守以下规则：

- 每个生产 package 的 `package-info.java` 必须以中英双语说明 responsibility、inputs、outputs、upstream/downstream 和 forbidden responsibility。架构测试验证这些语义类别、最小具体内容及禁用模板；描述是否准确仍需代码评审。
- 顶层 public/protected 手写类型的类级 Javadoc 必须具体说明职责和边界；类级门禁当前不强制双语标记。
- 编排职责类中超过门禁阈值的大方法必须有具体边界说明；简单 getter、record accessor 和小工具方法不在自动门禁范围内。

若后续新增生产 package、核心类或跨链路调用，必须同步更新对应注释、Phase 6 详细设计和本代码与设计对应表；
`DialectGrammarArchitectureTest` 会拒绝缺少双语 package 契约、缺少具体类/大方法 Javadoc，或使用已禁止通用模板的手写生产代码。门禁不会自动证明每个类和方法都是双语，也不会代替语义评审。

| 环节 | 代码入口 | 设计章节 | 测试覆盖 | 状态 | 差异 / 审视点 |
| --- | --- | --- | --- | --- | --- |
| CLI config / argument parsing | `cli.Main.MainCommand`, `SimpleYamlConfigLoader` | `phase-06 Parser mode 和 profile 选择`, `phase-08 输出与配置` | `ParserConfigRemovalTest`, `CliEndToEndGoldenTest` | PARTIAL | `SimpleYamlConfigLoader` 保留历史类名，内部使用 Jackson `YAMLMapper`。但 `CliArguments.parse()` 位于 `MainCommand` 的 exception mapping 之外，未知参数/非法值可以绕过 `ARGUMENT_ERROR`；`ErrorCode` 中的文件、连接和输出专用 code 也尚未被 single-scan 路径稳定映射。 |
| adaptor discovery | `cli.AdaptorRegistry` | `phase-03 Adaptor SPI`, `phase-06 full-grammar module 注入链` | `CliEndToEndGoldenTest`, adaptor selection tests | MATCHED | 通过 Java SPI 发现 `DatabaseAdaptor`；SPI v5 的 `AdaptorParsers.scriptFramer()` 提供必需的 `DialectScriptFramer`，full-grammar module 由独立 `ServiceLoader<FullGrammarDialectModule>` 注入。 |
| ScanEngine source orchestration | `core.scan.ScanEngine.scan`, `SourceCollectorPipeline`, `StatementParsePipeline`, `StatementExecutionService` | `phase-06 ScanEngine 总编排`, `phase-08 execution 配置` | `ScanEngineDatabaseDdlSourceTest`, `ScanEngineDiagnosticsTest`, `ScanExecutionParallelismTest`, `CliEndToEndGoldenTest`, `CorrectnessFixtureRunnerTest` | PARTIAL | `ScanEngine` 已拆出 source collection、statement execution、evidence enhancement 和 result assembly。CLI/YAML 会在构造 scan 前展开 `paths + include`，但直接 Java 调用 `ScanConfig.ddlPaths/objectPaths/logPaths` 时 core 只消费 `*Files`，路径输入会被静默忽略；core 必须统一展开或在 scan 前拒绝未展开路径。 |
| SQL/DDL parser selection | `core.parser.ParserBundleSelector`, `ParserBundle`, `ParserSelectionResult` | `phase-06 Parser mode 和 profile 选择` | `ParserBundleSelectorTest`, `SqlRelationParserRunnerTest`, `DdlRelationParserRunnerTest`, `ParserConfigRemovalTest`, `CliEndToEndGoldenTest` | MATCHED | `auto/full-grammar/token-event` 选择策略由一个 bundle 统一完成；SQL/DDL runner 不再分别重复 profile selection；unsupported profile 或 full-grammar hard failure fallback 到 token-event。 |
| token-event SQL parse | `core.tokenevent.CommonTokenEventStructuredSqlParser`, `TypedDialectTokenEventStructuredSqlParser`, dialect parse-tree visitors | `phase-06 SQL / DML token-event 链路` | `CommonTokenEventStructuredSqlParserTest`, `TokenEventStructuredSqlParserTest`, dialect boundary tests, correctness fixtures | MATCHED | token-event 是无 profile / unsupported version / forced token-event 的正式 fallback；生产事件来自 typed structural grammar + visitor。 |
| full-grammar SQL parse | MySQL `fullgrammar/common` + `v5_7|v8_0` parsers、PostgreSQL `fullgrammar/common` + `v16|v17|v18` parsers、Oracle `fullgrammar/common` + `v12c|v19c|v21c|v26ai` `INCOMPLETE_VERSIONED` parsers、SQL Server `fullgrammar/common` + `v2016|v2017|v2019|v2022|v2025` parsers | `phase-06 SQL / DML full-grammar 链路`, `phase-04`, `phase-05`, `phase-09`, `phase-10` | `CorrectnessFixtureRunnerTest`, `FullGrammarSqlBehaviorTest`, versioned correctness fixtures, adaptor parser architecture tests | DOC_UPDATED | 运行时选中 profile 后 full-grammar 是 primary parser；它只消费 typed generated context，不使用 rule name、reflection 或 raw SQL regex 推断结构；默认测试不再断言 native/delegate/bridge 过程属性，也不再用 token-event baseline 做跨 parser 兜底。PostgreSQL routine body由`postgres.plpgsql.v16|v17|v18`同版本shell解析，token-event使用独立`postgres.plpgsql.tokenevent` shell。Oracle当前是`INCOMPLETE_VERSIONED` generated parser，不再桥接token-event，更广泛的Oracle官方语法覆盖是backlog。 |
| dialect script framing | `core.script.StructuredScriptFramer`, five `*ScriptSlicePlanner`, `ScriptFileExtractor`, dialect `*ScriptFramer` | `phase-03 DialectScriptFramer`, `phase-04`, `phase-05`, `phase-09`, `phase-10` | dialect script framer tests, architecture test | MATCHED | MySQL `DELIMITER`、PostgreSQL dollar quote、Oracle single-line slash、SQL Server single-line `GO` 和 common compound object framing 分别由独立 planner 处理；`StructuredScriptFramer` 只编排 marker、slice 选择及 statement/provenance 装配。 |
| relationship extraction / naming evidence | `core.relation.StructuredRelationshipExtractor`, `RelationshipAliasSupport`, `RelationshipMerger`; `core.naming.NamingEvidenceExtractor`, `NamingRuleEngine`, `NamingRuleSet`, `NamingEvidenceMerger`, `NamingMatchEvidenceEnhancer` | `phase-02 SQL 谓词关系守卫`, `phase-06 Relationship 抽取` | `CorrectnessFixtureRunnerTest`, `CorrectnessNamingEvidenceGoldenTest`, `NamingEvidenceExtractorTest`, `NamingRuleEngineCatalogIdentityTest`, `RelationshipMergerEvidenceAggregationTest`, `FinalEvidenceContractTest` | MATCHED | `NamingRuleEngine` 是唯一命名启发式边界。relationship direct fact、naming rule 分组、pool 和 merger 已复用 dialect-aware canonical endpoint key；完整、排序后的 `conditions` 数组进入 candidate/observation/fingerprint identity，conditional summary 聚合全部 guard，并保留被 unguarded observation 覆盖的 guarded raw evidence。公开 `normalizedKey()` 继续承担 evidence reference/display 兼容职责。grouped evidence 只保留 deep-consensus attributes，冲突 provenance 留在 raw evidence。 |
| endpoint / metadata identity | `TableId`, `Endpoint`, `CanonicalEndpointKey`, `CanonicalEndpointKeyProvider`, `AliasSymbolTable`, `StatementParsePipeline`, metadata fact records | `phase-02`, `phase-06 Hard Boundaries` | catalog identity、naming、projection、metadata enhancement tests | MATCHED | `TableId` 构造一致性、MySQL `database.table` catalog 轴、source definition namespace、direct fact identity、profile namespace 和 derived path bridge 均复用 dialect-aware canonical endpoint identity；跨 catalog 同名表负向测试覆盖 relationship、lineage 和 naming path。 |
| Data Lineage extraction | `core.lineage.StructuredDataLineageExtractor`, `ProjectionTraceResolver`, `core.lineage.model.*`, `DataLineageMerger` | `phase-06 Data Lineage v1`, `sql-lineage-resolver.md` | `ProjectionTraceResolverTest`, `DataLineageMergerTest`, lineage golden | DOC_UPDATED | 正式链路只消费 structured events。statement source namespace进入 alias/known-physical 解析；`DataLineageMerger` 按 dialect-aware canonical source/target identity 去重，而输出 source 顺序仍按公开 key 稳定排序以保持 JSON 兼容。 |
| derived path inference | `core.derived.DerivedPathInferenceService`, `contracts.model.DerivedPathCandidate`, `JsonResultWriter` | `phase-06 Derived Path Evidence`, `phase-08 output` | `DerivedPathInferenceServiceTest`, `JsonResultWriterEvidenceOutputTest`, `ParserConfigRemovalTest` | MATCHED | `derivedPaths.enabled=false` 默认关闭；路径方向、canonical path fact identity、edge variant observation 合并、non-adjacent re-entry 防护和 table identity bridge 均使用完整 dialect-aware catalog/schema/table identity。公开 path/evidence 文本仍保留兼容 endpoint 表示。 |
| DDL parser selection | `core.parser.DdlRelationParserRunner` | `phase-06 DDL token-event / full-grammar DDL 链路` | `DdlRelationParserRunnerTest`, `CorrectnessFixtureRunnerTest`, `CliEndToEndGoldenTest` | MATCHED | DDL 使用同一 `parser.mode` 策略；DDL hard failure 记录 warning 并继续。 |
| DDL relationship extraction | `core.relation.DdlRelationExtractionVisitor`, `core.naming.NamingEvidenceExtractor` | `phase-06 DDL Relationship 抽取` | `PostgresDdlParserTest`, `MySqlDdlParserTest`, DDL correctness fixtures | DOC_UPDATED | DDL parser 产 `DDL_FOREIGN_KEY` / `DDL_INDEX` / `DDL_COLUMN`；relationship 转换集中在 visitor，column inventory 进入 naming pool。endpoint pair 只执行一次命名匹配，但双方所有 DDL/metadata/structural observations 都合并为 raw provenance，精确重复用 `occurrenceCount` 折叠。 |
| adaptor live capabilities | `DatabaseAdaptor.capabilities`, grouped `collectors()/parsers()/profiling()`, `ScanCapabilityValidator`, `DatabaseAdaptor.resolveLiveScope` | `phase-03 Adaptor API 和 SPI`, adaptor phase docs | capability、live collector 与 profile-only catalog tests | PARTIAL | SPI v5 preflight 已验证 capability 与 collector；所有 JDBC live source 在采集和 profiling 前先解析可执行 scope。MySQL 未显式配置 database 时使用 connection catalog，PostgreSQL/SQL Server 拒绝 connection catalog mismatch，Oracle拒绝非空 catalog。剩余缺口是 CT-02：live object/database-DDL 请求还需同时验证对应 structured parser。 |
| metadata/profile direction gates | `MetadataEvidenceEnhancer`, `DataProfileCandidateGenerator`, `IndexEvidencePolicy` | `phase-02`, `phase-07` | metadata/profile candidate tests | MATCHED | 组合 PK/UNIQUE 不证明单列唯一；普通组合索引仅物理首列可作 lookup / `SOURCE_INDEX` 支持，不单独决定方向。Metadata/DDL observation 交给 merger 按完整 identity 折叠，不再按 type 提前丢弃。 |
| profiler diagnostics | `JdbcDataProfilerTemplate`, `JdbcExceptionClassifier`, `LiveDiagnosticSanitizer`, `DataProfilePipeline` | `phase-07 安全与权限` | core/Oracle/SQL Server profiler tests | MATCHED | warning 使用固定安全消息，只保留 endpoint、SQLState、vendor code、exception class 和 profiler source；profiler 通过调用方传入 Oracle 1031 或 SQL Server 229/916。真实 driver/version 仍需环境性 smoke。 |
| live collector diagnostics | `ScanEngine`, `DiagnosticWarnings`, `LiveDiagnosticSanitizer`, `JdbcExceptionClassifier`, dialect metadata/object/database-DDL collectors | `phase-08 Warning 设计` | scan/collector diagnostics tests | MATCHED | profiler 与普通 live collectors 共用 fixed-message sanitizer。共享 classifier 只识别可移植 JDBC 类型和 SQLState；Oracle/SQL Server adaptor 显式提供自己的 vendor code。Pipeline 对 null/blank definition、null element 和 null list 均输出 `DEFINITION_UNAVAILABLE` 并阻止下游解析。 |
| profiler metrics | `JdbcDataProfilerTemplate`, `DataProfileMetrics`, `DataProfileEvidenceBuilder`, `DataProfileNamespacePolicy`, dialect `IdentifierQuoter` modes | `phase-07 指标` | evidence-builder、dialect fake-JDBC、profile-only catalog tests | PARTIAL | live query 已独立测量 source non-null rows、source distinct、matched distinct 和 target distinct；profile table reference 分别按 MySQL catalog/table、PostgreSQL schema/table、Oracle owner/table、SQL Server catalog/schema/table 渲染。PostgreSQL 异库候选和 Oracle 带 catalog 候选在 renderer 前被拒绝，避免丢弃 catalog 后误查同名表。剩余缺口仅是 negative evidence 尚未建模 tenant/soft-delete/time-window 等过滤上下文 guard。 |
| confidence / merger / output | `RelationshipMerger`, `DataLineageMerger`, `NamingEvidenceMerger`, `ConfidenceCalculator`, `JsonResultWriter` | `phase-02`, `phase-08` | `ConfidenceScoringExamplesTest`, `RelationshipMergerEvidenceAggregationTest`, `DataLineageMergerTest`, `JsonResultWriterEvidenceOutputTest` | MATCHED | raw/grouped 模型与 catalog 输出已落地。relationship、lineage 和 naming grouped evidence 使用 deep-consensus attributes；所有 direct/derived summary 通过统一 occurrence helper 累加折叠 evidence 的 `occurrenceCount`，而 repeated-observation confidence 仍按独立 observation 计数。 |
| correctness fixture / generated reports | `CorrectnessFixtureRunnerTest`, `CorrectnessFixtureExecutor`, `FixtureInputLoader`, `FixtureExecutionEngine`, `GoldenAssertion`, `GoldenWriter`, `CorrectnessSummaryGeneratorTest`, `DataLineageAuditGeneratorTest` | `test-assets-map`, `code-implementation-guide`, `phase-06 correctness / golden 验收链` | 同名测试 | DOC_UPDATED | correctness fixture 是主 golden；执行框架拆为 input loader、execution engine、golden assertion、golden writer。SQL/DDL actual 输出通过 `StatementExecutionService`，避免测试链路和生产解析链路分叉。generated summary/audit 是 Java 程序生成，不调用大模型。 |
| PostgreSQL versioned golden | `test-fixtures/correctness/postgres/v16|v17|v18`, PostgreSQL `FullGrammarDialectModule` | `phase-05 PostgreSQL versioned correctness golden`, `phase-06 PostgreSQL 版本化 correctness` | `CorrectnessFixtureRunnerTest` with version filters | DOC_UPDATED | root `postgres` 是历史兼容 baseline；`v16`、`v17`、`v18` 是严格 full-grammar version golden，不存在 `postgres/v1` 这个版本。 |
| MySQL versioned golden | `test-fixtures/correctness/mysql/v5_7|v8_0`, MySQL `FullGrammarDialectModule` | `phase-04 Correctness 与 golden 状态`, `phase-06 MySQL correctness 命名约定` | `CorrectnessFixtureRunnerTest` | DOC_UPDATED | root `mysql` 是 token-event baseline；`mysql/v5_7` 与 `mysql/v8_0` 分别是严格 MySQL 5.7 / 8.0 full-grammar version golden。 |
| Oracle versioned golden | `test-fixtures/correctness/oracle/v12c|v19c|v21c|v26ai`, Oracle `FullGrammarDialectModule` | `phase-09 Oracle adaptor`, `phase-06 Parser mode 和 profile 选择` | `CorrectnessFixtureRunnerTest` with Oracle sample-data fixtures, `OracleAdaptorParserTest`, `OracleParserArchitectureTest` | DOC_UPDATED | root `oracle` 是 token-event baseline；versioned directories 强制 Oracle full-grammar profile。当前 versioned outputs 是 `INCOMPLETE_VERSIONED` generated parser golden，不是 token-event bridge golden。 |
| SQL Server versioned golden | `test-fixtures/correctness/sqlserver/v2016|v2017|v2019|v2022|v2025`, SQL Server `FullGrammarDialectModule` | `phase-10 SQL Server adaptor`, `phase-06 Parser mode 和 profile 选择` | `CorrectnessFixtureRunnerTest`, `SqlServerParserArchitectureTest`, `SqlServerTokenEventParserTest` | DOC_UPDATED | root `sqlserver` 是 token-event baseline；versioned directories 强制 SQL Server full-grammar profile。当前 sample-data 使用保守 T-SQL 子集；2017 `STRING_AGG`、2022 `DATETRUNC` / `GENERATE_SERIES`、2025 `VECTOR(...)` 已有 grammar-level version boundary，更多 T-SQL family 仍是 backlog。 |
| 代码结构注释 | `**/package-info.java`, production class/method Javadocs | `phase-06 代码结构注释索引`, `code-implementation-guide` | `DialectGrammarArchitectureTest`, compile + semantic tests | MATCHED | 泛化模板已清零。package 门禁要求双语职责、输入、输出、上下游与禁止边界；公开类型和大型编排方法要求具体说明。门禁验证结构与禁用模板，语义准确性仍由代码评审负责。 |
| 大型语义类职责门禁 | parser semantic analyzers/support/extractors/resolvers/mergers/framers | `phase-06 Visitor 与语义 helper 职责边界` | `DialectGrammarArchitectureTest` semantic/framer gates | PARTIAL | 400/450、framer 200 和 planner 250 的有效代码行门禁已覆盖目标职责且无永久 allowlist。但 top-level record 排除仍通过源码文本包含判断，注释或字符串可误触发豁免；门禁应改为 compiler AST 的顶层声明判断。 |

## 已知实现事实

- 单条 SQL 解析当前由 `StatementExecutionService.executeSql(...)` 调用 `SqlRelationParserRunner.parseStructuredAndRelations(...)`，一次结构化解析后同时服务 relationship 与 Data Lineage。SQL naming rule 随后只在 scan-level `EvidenceEnhancementService` 对合并后的 relationship candidates 执行一次。parser mode、fallback warning 和 diagnostics 在 relationship/lineage 链路中保持一致，naming 复用其结构化 relationship evidence。
- `CorrectnessFixtureRunnerTest` 不再自己拼 SQL/DDL -> relationship/lineage/namingEvidence 流程；它通过 `FixtureExecutionEngine` 复用 `StatementExecutionService` 和 `EvidenceEnhancementService`。DDL fixture 仍保持 parser-outcome 验收语义，不额外引入 scan-level metadata/naming enhancement。
- 目录、命名、迁移过程检查已从默认测试入口移除。后续如需审查旧命名残留，按 `docs/relation-detector/test-assets-map.md` 中的可选 `rg` 命令手工执行。
- `SqlGrammarProfileRegistry` 中的版本字符串解析会使用字符串/正则处理配置值；这不属于 SQL/DDL 结构解析。SQL/DDL relationship 和 lineage 不能依赖特殊表名/列名过滤。
- MySQL 5.7 / 8.0 full-grammar 当前已有独立 `test-fixtures/correctness/mysql/v5_7`、`test-fixtures/correctness/mysql/v8_0` versioned golden。root MySQL correctness 仍是 token-event baseline；三者都必须保持独立，不能互相覆盖。

## 剩余技术债

| 项目 | 状态 | 说明 |
| --- | --- | --- |
| root token-event typed visitor coverage | BACKLOG | root token-event 已使用 typed structural grammar/visitor，但在复杂 routine、业务查询和部分 DDL evidence 上仍弱于对应 full-grammar。差异不需要人工审批，后续应继续扩展 typed grammar/visitor，不能恢复 scanner 或名字过滤。 |
| Oracle 官方语法覆盖扩展 | BACKLOG | Oracle 已有 adaptor、profile 和 sample-data versioned full-grammar golden，但 full-grammar 仍是 `INCOMPLETE_VERSIONED`。后续需要按 Oracle SQL/PLSQL Reference 扩大 `.g4` / typed visitor 覆盖面，并补版本边界 fixture。 |
| production Javadoc / package documentation | MATCHED | compiler/doc-tree 门禁验证双语 package 契约、具体边界说明和禁用模板；最终描述准确性继续由 review 保证。 |
| table identity 与 fact canonicalization | MATCHED | `TableId`、dialect canonical key、source namespace、live profile namespace 和 derived bridge 已使用完整身份；跨 catalog/schema、quoted case 和 profile-only mismatch 由 focused tests 保护。 |
| 直接 API 文件路径 | REVIEW_NEEDED | `ScanConfig.*Paths + *Includes` 只有 CLI loader 会展开；core 直接调用必须统一展开或 fail-fast，不能成功返回空解析结果。 |
| relationship 条件与 provenance merge | MATCHED | 完整 guard identity、conditional/unconditional summary 与 polymorphic 计算已通过 focused tests；grouped evidence 顶层只保留所有 observation 一致的 consensus attributes。 |
| 大型语义类职责门禁 | PARTIAL | 行数和 planner/helper 边界已实现；top-level record 豁免仍需从文本包含判断改为 AST 顶层类型判断。 |
| profiler warning 脱敏与权限分类 | MATCHED | 脱敏和 profiler 契约已实现；共享规则只识别 JDBC 类型/SQLState，方言 vendor code 由对应 adaptor 限定。 |
| live profiler 指标完整性 | PARTIAL | 四项计数、方言合法 table qualification 与 profile-only catalog 校验已闭环；negative filter-context guard 仍为明确 backlog。 |
| MySQL live object parser completeness | MATCHED | `information_schema` 只用于枚举对象身份；完整 parser 输入来自 `SHOW CREATE PROCEDURE/FUNCTION/VIEW/TRIGGER/EVENT`，单对象失败产生安全 warning 并跳过。 |
| live database-DDL structural contract | MATCHED | Oracle DDL 使用 `DBMS_METADATA.GET_DDL`；SQL Server/PostgreSQL 明确输出关系解析用结构骨架，组合 FK 分别按 `constraint_column_id` / ordinality 配对并保留 catalog。完整可回放 DDL（type modifier、default、identity/generated/computed/collation）不属于当前骨架契约。 |
| live collector warning sanitization | MATCHED | 所有 live JDBC failures 经共享 classifier/sanitizer 输出固定消息，不包含 URL、SQL 或 driver message。 |
| live warning object context | MATCHED | 四个 adaptor及 pipeline 均安全处理 null/blank definition；第三方 collector 返回 null 元素或 null list 时生成通用 `DEFINITION_UNAVAILABLE`，不会静默过滤或误归为 collect-failed。 |
| observation summary count | MATCHED | relationship、lineage、naming 和 derived path 的 direct/derived summary 均通过统一 helper 累加 `occurrenceCount`；折叠 multiplicity 不参与 repeated-observation confidence。 |
| CLI error-code contract | REVIEW_NEEDED | 将 argument parsing 纳入统一 exception mapping，并为 `CONFIG_FILE_ERROR` / `INPUT_FILE_ERROR` / `DATABASE_CONNECTION_ERROR` / `OUTPUT_WRITE_ERROR` 建立可执行的 single-scan 映射与穷举测试；否则 enum 只是保留合同，不能称为实际 CLI 行为。 |
