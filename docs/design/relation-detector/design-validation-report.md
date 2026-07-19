# 设计一致性检查报告

## 检查目标

本报告用于检查当前设计文档是否与代码实现保持一致，重点覆盖：

- core / adaptor 职责边界。
- `token-event` 与 `full-grammar` parser 模式。
- SQL / DML relationship 抽取。
- Data Lineage v1。
- DDL relationship 抽取。
- correctness fixture 与生成报告。

## 检查结论

当前主设计已经从早期 “Simple / ANTLR primary / shadow” 迁移为：

```text
parser.mode=auto|full-grammar|token-event

token-event:
  ANTLR lexer/parser support
  -> token-event structured events
  -> relationship / lineage / DDL semantic extractor

full-grammar:
  adaptor-owned versioned grammar profile
  -> typed parse-tree visitor
  -> same structured events
  -> same semantic extractor
```

代码中的主要行为与设计一致：

- MySQL/PostgreSQL 是当前工程覆盖最广的 parser 与 sample-data 支持目标。MySQL live object 使用
  `SHOW CREATE`，四方言 live profiler 返回四项独立 exact metrics。PostgreSQL live metadata 已覆盖
  table/column、PK/UNIQUE/FK、index inventory；database-DDL 与 metadata 共用 ordinal-safe composite
  constraint reader，object collector 采集 non-internal trigger definition，三条链路均保留 connection catalog。
- Oracle 是当前初始支持目标：已有 adaptor、Oracle token-event fallback、root correctness golden 和 `INCOMPLETE_VERSIONED` versioned full-grammar，但更广泛的 Oracle 官方语法覆盖仍是 backlog，当前状态为 `INCOMPLETE_VERSIONED`。
- SQL Server 已接入 adaptor、root token-event baseline 和 `sqlserver/2016|2017|2019|2022|2025` versioned full-grammar sample-data golden；sample-data 已收敛为自然 ERP 业务 SQL；高密度关系探针迁入 semantic-equivalent benchmark。首批 Microsoft 官方逐版本 T-SQL 边界已经进入 `.g4`、version-only fixture 和 architecture test。Metadata/object/database-DDL collector 与 exact live profiler 已有可调用实现；database-DDL 会继承 connection catalog，并按 `constraint_column_id` 配对组合 FK，但重建文本是关系分析骨架，不是完整可执行 table declaration。更多 T-SQL family 及真实数据库权限/版本组合 runtime smoke 仍是 backlog。
- core 不直接 import MySQL/PostgreSQL/Oracle/SQL Server full-grammar 实现；版本化 module 由 adaptor 注册。
- Relationship 与 Data Lineage 是两个独立输出模型。
- Simple SQL/DDL parser 和旧 SQL/DDL parser mode 配置不再是当前能力。
- correctness fixture 以当前 parser golden 为回归基线；root token-event 与 versioned full-grammar 分别直接比对自己的 golden，不再用 token-event fallback 保护 full-grammar。Golden 通过只证明“输出没有偏离已审核基线”，不单独证明 SQL 资产符合真实数据库版本，也不证明每条 relation/lineage 的业务语义正确。

### 验证结论的三个层级

| 层级 | 能证明什么 | 不能证明什么 |
| --- | --- | --- |
| 架构测试 | parser ownership、token-event/full-grammar 独立、regex 边界、SPI 依赖方向。 | 某条 SQL 的 relation/lineage 一定正确。 |
| correctness golden | 当前 parser 输出与已保存 fingerprint 一致；生产与 correctness 共用 execution service。 | golden 本身没有 false positive/false negative；目标数据库 runtime 一定接受 SQL。 |
| SQL/版本/语义审计 | 具体 SQL、官方版本文档和 parser output 可以互相解释。 | 未审计 statement family 的完整覆盖。 |

本报告中的“通过”默认只表示对应层级通过；不得把 zero diagnostics 或 count parity 升格为 SQL/语义正确性证明。

## 当前收敛状态来源

Closure 状态的唯一所有者是 [Code / Design Traceability](code-design-traceability.md)。本报告只说明验证方法和证据边界，不复制 closure ID 或状态表，避免两份手工状态随实现分叉。

## 本轮代码结构注释审视

生产代码结构注释的目标分成三层。package 契约、手写 public/protected 顶层类型和编排类都强制中文 / English 双语、具体且非模板的设计说明：

- package 层：每个生产 package 的 `package-info.java` 用中英双语说明职责、输入输出、上游/下游和禁止承载的逻辑。
- class 层：所有手写 public/protected 顶层类型和编排职责类使用 `CN:` / `EN:` 说明负责什么、不负责什么、位于哪条链路。
- method 层：有效代码超过 40 行的非 override 编排方法说明输入效果、输出/副作用和失败边界；简单 getter、record accessor 和显而易见的小工具方法不强制注释。

代表性检查范围包括（实际架构测试按 source roots 扫描，不以此列表作为穷举 allowlist）：

```text
contracts
contracts.model / metadata / parse / spi / scoring
core.scan / parser / tokenevent / fullgrammar / relation / lineage / ddl
core.parse / log / metadata / output / diagnostics / scoring
cli
mysql / mysql.tokenevent / mysql.fullgrammar.v5_7 / mysql.fullgrammar.v8_0
postgres / postgres.tokenevent / postgres.fullgrammar.common / postgres.fullgrammar.v16 / v17 / v18
oracle / oracle.tokenevent / oracle.fullgrammar.common / oracle.fullgrammar.v12c / v19c / v21c / v26ai
sqlserver / sqlserver.tokenevent / sqlserver.fullgrammar.common / sqlserver.fullgrammar.v2016 / v2017 / v2019 / v2022 / v2025
```

逐包审视结论：

- `contracts` 只承载跨模块契约、模型、SPI、parse result 和默认 score 常量，不依赖 core。
- `core.scan` 负责扫描编排；`core.parser` 负责 parser mode/profile 选择；二者没有承载数据库版本实现。
- `core.tokenevent` 与 `core.fullgrammar` 是事件来源基础设施；relationship / lineage 语义被隔离在 `core.relation` 与 `core.lineage`。
- `core.ddl` 是 token-event DDL event 支撑；DDL relationship 转换仍在 `core.relation.DdlRelationExtractionVisitor`。
- `adaptor-mysql` / `adaptor-postgres` / `adaptor-oracle` 根包只做 adaptor 装配；token-event parser 位于各自 `tokenevent` 子包，版本化 full-grammar 位于 `fullgrammar/v8_0`、PostgreSQL `fullgrammar/v16|v17|v18` 或 Oracle `fullgrammar/v12c|v19c|v21c|v26ai`，PostgreSQL/Oracle 公共抽象位于 `fullgrammar/common`。
- 没有发现 core 直接 import MySQL/PostgreSQL/Oracle/SQL Server full-grammar implementation 的职责倒置。
- 没有发现 adaptor 侧重复实现 relationship / lineage semantic extractor。
- 没有发现 contracts 反向依赖 core 的设计破坏。
- 手写生产 package、public/protected 顶层类型、已登记 suffix 编排类与大型编排方法已有中英双语契约。
  `DialectGrammarArchitectureTest` 和 `SemanticDocumentationArchitectureTest` 使用 JDK compiler/doc-tree API 验证 package 的 `CN:` / `EN:` 标记、
  职责、输入、输出、上下游与禁止边界，并对公开类型、当前登记的编排 suffix 和大方法验证最小具体内容及泛化模板禁止。generated Java、
  record accessor、getter 和显而易见的小方法按规则排除。当前 suffix 已覆盖 `Assembly`、`Factory`、
  `Assembler`、`Resolver` 与 `Index`；`ResultAssembler`、`RelationshipAliasResolver`、
  `RelationshipCandidateFactory` 和 semantic factories/registries 具备完整双语设计说明。描述是否准确
  反映实际调用链继续由代码评审确认。

本报告和 `phase-06-parser-enhancement.md` 已按上述代码注释刷新。若后续新增生产 package、核心类或跨链路调用，必须同步新增/校准代码注释，并在 Phase 6 的结构表与调用链中登记。

本轮新增 [代码与设计对应审视报告](code-design-traceability.md)，按 CLI、ScanEngine、SQL/DDL parser、relationship、Data Lineage、confidence、输出和 correctness 报告逐环节列出代码入口、设计章节、测试覆盖和差异状态。

## 需要特别说明的实现事实

### 1. fallback 只发生在 parser selection 层

当 `parser.mode=auto|full-grammar` 时，如果无法根据 database type / profile / version 选中 full-grammar profile，runner 会使用 adaptor 暴露的 token-event parser，并记录 fallback 诊断。

如果 full-grammar profile 已经选中，full-grammar parser 自己返回 structured events、partial result 和 warning；它不会在 event 层委托 token-event 补齐事件。只有 profile 缺失、版本不支持或 full-grammar hard failure 才 fallback 到 token-event，并输出 parser-mode fallback warning。

### 2. SQL relationship 与 Data Lineage 共享 structured result

`ScanEngine.scan(...)` 当前通过 `SourceCollectorPipeline` 和 `StatementParsePipeline` 进入 `StatementExecutionService`。单条 SQL 由 `StatementExecutionService.executeSql(...)` 调用 `SqlRelationParserRunner.parseStructuredAndRelations(...)`，一次结构化解析后生成 relationship candidates，并把同一个 `StructuredParseResult` 交给 Data Lineage extractor。SQL naming rule 不在 statement 层执行；它随后由 scan-level `EvidenceEnhancementService` 对合并后的 relationship candidates 执行一次。

这是当前实现事实，不改变 relationship / Data Lineage JSON schema，也不改变 semantic extractor 的职责边界。

### 3. full-grammar 与 token-event 共用语义层

full-grammar 只替换事件来源，不替换语义判断。以下逻辑仍在 Java semantic layer：

- FK-like 方向归一。
- 列级 / 表级 `CO_OCCURRENCE` 判断。
- self-join 结构性列级弱共现。
- SQL 谓词 relationship 守卫：literal filter、literal `IN`、`LIKE`、表达式 tuple、aggregate/HAVING/filter 字段不生成关系；`IN` / tuple `IN` 必须是已验证的列子查询结构。
- Data Lineage transform 映射和 confidence。
- DDL index / FK 事件到 relationship 的转换。

### 4. 不允许特殊名字过滤

当前设计要求 SQL/DDL/Lineage 过滤只能基于语法结构、事件类型、作用域、endpoint 类型或数据库关键字。不能因为表名或列名包含 `tmp`、`temp`、`manager_id` 等特殊字符串而改变关系/血缘结论。

临时表只能来自明确语法结构，例如 `CREATE TEMPORARY TABLE` / `CREATE TEMP TABLE`。

## 一致性检查项

### Core 与 adaptor

结果：通过。

- core 负责 parser selection、module registry、relationship merger、lineage merger、confidence、输出模型。
- adaptor 负责数据库元数据、日志/对象采集、token-event parser、versioned full-grammar module。
- MySQL `SQL_MODE` helper 只属于 MySQL full-grammar runtime，不是系统 `parser.mode`。

### Relationship 模型

结果：结构契约通过；跨 parser SQL 语义仍需逐条审计。

- `RelationType` 仍只保留 `FK_LIKE` 和 `CO_OCCURRENCE`。
- 列级弱共现使用 `RelationSubType.COLUMN_CO_OCCURRENCE`；evidence 保留具体 SQL 谓词来源，例如 `SQL_LOG_JOIN`、`SQL_LOG_EXISTS` 或 `SQL_LOG_SUBQUERY_IN`。
- `SQL_LOG_COLUMN_CO_OCCURRENCE` / `SQL_LOG_TABLE_CO_OCCURRENCE` 仍作为 enum、score 和 merger 兼容项保留，但当前生产 parser / extractor 不主动产出。前者由具体 SQL predicate evidence 替代；后者没有等价现役替代，纯表级同现默认不生成正式 relationship。
- 同表不同 alias 的 self-join 允许输出列级弱共现；同 alias 行内比较不输出关系。

### Data Lineage 模型

结果：结构契约与 source-set fact identity 已通过。

- `ScanResult` 已有独立 `dataLineages`。
- Data Lineage confidence 不参与 relationship confidence。
- v1 只输出数据库内部 `table.column -> table.column`，不做 Parameter Binding。
- `CUMULATIVE` 已作为累计/运行聚合 transform 与普通 `AGGREGATE` 区分。
- 设计把 `sources` 视为 set-valued identity；`DataLineageMerger` 在构造 fact key 前执行
  canonical dedupe/sort，同一 source 集合不会因发射顺序不同形成重复 fact。

### Parser 模式

结果：通过，需注意文档用词。

- 用户可见模式名是 `full-grammar` 与 `token-event`。
- Java package 使用 `fullgrammar` / `tokenevent`，因为 Java package 不能包含横线。
- `full-grammar` 具体版本实现在 adaptor，例如 `mysql.fullgrammar.v5_7|v8_0`、`postgres.fullgrammar.v16|v17|v18`、`oracle.fullgrammar.v12c|v19c|v21c|v26ai`。
- 无方言或无合理版本信息时使用 token-event。
- PostgreSQL full-grammar 当前有严格版本 profile：`postgresql/16`、`postgresql/17`、`postgresql/18`。三者分别有独立 versioned correctness golden。root `postgres` fixture 目录是历史兼容 baseline，不代表 `v1` 数据库版本。
- MySQL full-grammar 当前有 `mysql/5.7`、`mysql/8.0` profile，并已有独立 `test-fixtures/correctness/mysql/v5_7`、`test-fixtures/correctness/mysql/v8_0` versioned correctness golden。root `mysql` fixture 目录是 token-event baseline，不代表严格 MySQL 版本证明。
- Oracle full-grammar 当前有 `oracle/12c`、`oracle/19c`、`oracle/21c`、`oracle/26ai` profile，并已有独立 `test-fixtures/correctness/oracle/v12c|v19c|v21c|v26ai` sample-data correctness golden。当前 Oracle full-grammar 使用本版本 generated parser/visitor，但状态是 `INCOMPLETE_VERSIONED`，尚不代表 更广泛的 Oracle 官方语法 已完成。
- SQL Server full-grammar 当前有 `sqlserver/2016`、`sqlserver/2017`、`sqlserver/2019`、`sqlserver/2022`、`sqlserver/2025` profile，并已有独立 `test-fixtures/correctness/sqlserver/v2016|v2017|v2019|v2022|v2025` sample-data correctness golden。当前 SQL Server sample-data 使用跨版本保守 T-SQL 子集；首批官方逐版本语法边界已通过 2017 `STRING_AGG`、2022 `DATETRUNC` / `GENERATE_SERIES`、2025 `VECTOR(...)` fixture 和低版本拒绝测试锁定。

### 当前 golden 与验证结果

当前 correctness 数量只维护在 verification session 的
`reports/correctness-test-summary.md`；sample-data parser/category、direct/derived 和 observation 数量只维护在
[`parser-comparison-summary.md`](../../parser-audit/parser-comparison-summary.md)。本 validation 文档不再
复制易漂移的计数表。

验证入口：

- 日常 smoke：`mvn test`。
- 全量 correctness golden 与 generated report：`mvn -T 2 -Pacceptance verify`。
- 最终 parser CLI 矩阵与 canonical output 验收：`bash relation-detector/scripts/verify-all.sh`。
- 无缓存参考验收：`mvn -T 2 -Pacceptance -Dmaven.build.cache.enabled=false clean verify`。
- 报告验收：显式运行 `CorrectnessSummaryGeneratorTest` 和 `DataLineageAuditGeneratorTest`，并传 `-DrunGeneratedReportTests=true`。
- 跨 parser 差异需联合阅读 [`parser-comparison-summary.md`](../../parser-audit/parser-comparison-summary.md)、各版本边界审计与 [`sample-data-output-audit-backlog.md`](../../parser-audit/sample-data-output-audit-backlog.md)；它们分别维护当前统计、确认的版本差异和未关闭问题。

### DDL

结果：统一 event/merger 链路通过；Oracle 版本资产与部分 token-event/full-grammar typed coverage 仍未完成官方 runtime 验证。

- 当前 DDL production parser 是 token-event DDL structured parser 或被 parser selection 选中的 full-grammar DDL parser。
- 两者都输出 `DDL_FOREIGN_KEY` / `DDL_INDEX` / `DDL_COLUMN` 事件；column event 只补充 inventory。
- `DdlRelationExtractionVisitor` 只消费 DDL events，不参与 SQL relation / lineage。

### 测试资产

结果：回归框架通过；测试资产真实性与 golden 语义不能只靠 runner 自动证明。

- `CorrectnessFixtureRunnerTest` 保护当前 parser golden。
- `CorrectnessSummaryGeneratorTest` 生成轻量索引报告。
- `DataLineageAuditGeneratorTest` 维护 lineage 审核入口。
- full-grammar 不再通过 token-event 跨 parser 兜底；版本化 SQL/DDL golden 直接暴露 full-grammar 的 missing / extra。
- `CliEndToEndGoldenTest` 保护从 CLI YAML/参数到 JSON 输出的完整系统链路，并复用现有 fixture golden。

## 反向审计收口状态

2026-07 的结构/SQL 审计已经修复以下历史不匹配：derived lineage 按 canonical path 合并、不同 edge variant 保留为 raw observations；naming inventory 合并同 endpoint 的全部 metadata/DDL observation；Oracle natural assets 使用 `GENERATED ALWAYS AS (...) VIRTUAL` 且无参 routine 不再写空 `()`；common natural 只保留一份 canonical `payments`；已审计 CASE/scalar-subquery、trigger provenance、非平凡 self-update 和 Oracle transform gap 均由 typed context 测试保护。当前 direct/derived sample-data JSON 的数量与完整性结论只以生成的 parser comparison 和 verification manifest 为准。

本轮已完成 preflight 主链、index policy、lineage source-set identity、live warning 脱敏、四项 exact
profile metrics、scan summary namespace 和职责拆分。以下条目记录当前实现边界和验证层级；
catalog-aware fact identity 已闭环。runtime 配置由 core 统一校验，negative profiling 通过
“只验证非条件声明 FK”的适用范围解决过滤上下文不可证问题，offline profile
配置已从 runtime 和 SPI 删除：

1. Oracle/SQL Server `METADATA` 与 `DATABASE_OBJECTS` capability 已有非空 live collector，支持组合 constraint/index 和 partial-success warning；这证明代码契约可执行，但真实权限/版本组合仍需 runtime smoke。
2. `AdaptorContractValidator` 与 `ScanCapabilityValidator` 在 JDBC 前分别验证 SPI/type/id 及实际请求的 capability、collector 和 consumer；live DDL 要求 structured DDL parser，live objects 要求 structured SQL parser，纯文件 scan 不新增 live capability 要求。契约失败统一抛 `AdaptorContractException`，single/batch 保持 `ADAPTOR_ERROR` 类别。
3. `IndexEvidencePolicy` 不允许组合 PK/UNIQUE 成员证明单列唯一；普通组合索引仅首列可支持 lookup / `SOURCE_INDEX`，不单独决定方向。
4. `DataLineageMerger` 对 source set canonical dedupe/sort，fact identity 不再依赖发射顺序。
5. `ProfileOutcome` 区分 success/no-evidence/skip/permission/timeout/query-failure。`ProfileOutcomeContractValidator` 将外部 outcome 作为不可信输入原子校验，core 不转发 plugin warning 内容，而按已验证 status 重建脱敏 warning。四个方言 live SQL 独立测量 source non-null rows、source/target distinct 和 matched distinct，containment、overlap 与 negative gate 均基于真实统计。
6. Metadata facts、live collector、statement source namespace、profile query 和 derived graph 均保留
   dialect-aware catalog/schema/table identity。MySQL profile 使用 catalog/table，PostgreSQL 使用
   schema/table 并先验证 connection catalog，Oracle 拒绝非空 catalog且使用 owner/table，SQL Server
   使用 catalog/schema/table；PostgreSQL 异库候选和 Oracle 带 catalog 候选不会进入 profiler，MySQL
   缺省 database 时从 connection catalog 建立 live scope；跨 catalog 同名表不能形成 derived
   relationship、lineage 或 naming path。
7. SPI v6、Oracle/SQL Server live 能力和 `contracts.Enums` 设计真源链接的生产 Javadoc 已同步。
8. Metadata/DDL observation 不再在 merger 前仅按 type 丢弃；merger 按完整 observation identity 折叠精确重复并记录 `occurrenceCount`。
9. MySQL live object collector 只用 `information_schema` 枚举身份，parser 输入由对应 `SHOW CREATE` 返回的完整 declaration 提供。
10. Connection、metadata、object、database-DDL 和 profiler 的 SQLException failure 共用
    `JdbcExceptionClassifier` 与 `LiveDiagnosticSanitizer`，且不输出 JDBC URL、rendered SQL 或 driver message。
    共享 classifier 只识别 JDBC 类型和 SQLState；Oracle 1031、SQL Server 229/916 由对应 adaptor 显式提供。
    Pipeline 对第三方 collector 返回的 null/blank definition、null element 或 null list 统一输出 `DEFINITION_UNAVAILABLE` 并跳过解析。
11. `ScanInputPathResolver` 是 `files + paths + include` 的唯一展开 owner；CLI 以配置文件父目录调用
    `ScanConfig.resolve(baseDirectory)`，direct API 无参调用以当前工作目录为 base。运行态仅消费稳定排序、
    规范绝对路径且去重的 `*Files`，missing、non-regular 和 unreadable 输入均在 scan 前明确失败。
12. `ScanConfigurationValidator` 是 YAML/CLI override、`ScanConfig.resolve()`、手工
    `ResolvedScanConfig` 和 `ScanEngine.scan()` 的主要行为边界；live source 缺 JDBC、无可执行
    source、非法 parser mode、derived limit 或 confidence 在 adaptor capability 检查和 JDBC 前失败。
    naming rule file 由 core `NamingRuleSetResolver` 统一加载。CLI 只负责相对路径解析；direct API、CLI
    与 batch 在 JDBC 前合并 system/file/inline typed rules 并拒绝 duplicate rule id，parser compatibility
    view 只复制最终 typed rules，避免二次加载。
13. 内置 `JdbcDataProfilerTemplate` / `DataProfileEvidenceBuilder` 只对 live database、非条件
    `DDL_FOREIGN_KEY` / `METADATA_FOREIGN_KEY` 产生 `NEGATIVE_VALUE_MISMATCH`。`DataProfilePipeline`
    通过 `ProfileOutcomeContractValidator` 重验 status、evidence allowlist、source type、warning 状态契约和
    负向策略；pre-merge guard同时读取 candidate、structural evidence与raw evidence attributes。全部 outcome
    通过后才统一应用，plugin warning message/source/attributes 不进入 scan result。
14. offline INSERT profiling 没有可执行 producer，其 runtime/SPI 字段已在 v6 删除；
    YAML transport 仅保留拒绝哨兵，旧字段明确返回 config format error，不会被静默忽略。

上述 live definition、warning sanitization 与 collector fail-fast 主链已有 focused tests；当前完整
验收数量应从生成报告与 verification manifest读取，不在本文复制。direct Java `ScanConfig.*Paths`、
public SPI/type/id 与 parser-half 边界已有 focused API tests；file-based fact parity 仍不能覆盖 profile-only、
跨 catalog 或真实 live 权限行为，也不能代替四个数据库的真实权限、版本、driver 与 catalog 组合 runtime smoke。
PostgreSQL/SQL Server 重建 DDL 明确属于 relationship parser 使用的 structural skeleton，不承诺完整
可回放 declaration；若未来增加回放契约，需另行补齐 type modifier、default、identity/generated/
computed/collation 并建立数据库执行测试。

代码结构方面，`DialectGrammarArchitectureTest` 对 parser semantic package 中的
Visitor/Collector 实施 400 有效代码行门禁，并对 Analyzer/Support/Extractor/Resolver/Merger/Framer/Facade
实施 450 有效代码行门禁；Javadoc、普通注释和空行不计入职责规模。generated Java、top-level record DTO 和 `package-info` 不参与行数约束，
门禁没有永久 allowlist。expression、relationship 和 lineage 入口已经抽出 typed helper；
`StructuredScriptFramer` 只负责编排，并由 200 行门禁保护；MySQL、PostgreSQL、Oracle、common 和
SQL Server 的 slice 算法位于五个独立 planner，各受 250 行门禁保护。行数和职责拆分已匹配，
top-level record 豁免通过 JDK compiler AST 检查实际顶层声明；普通类中的注释或字符串即使包含
`record TypeName(` 也不能绕过门禁。该职责门禁状态为 `MATCHED`。

## 后续技术债

- Catalog identity 的 direct fact、live profile 和 derived path 边界已由 focused negative tests 闭环；
  当前 sample-data 仍不用于替代跨 catalog/quoted case 的专门测试。
- relationship 已将完整、顺序无关的 guard 数组纳入 candidate/observation/fingerprint identity，
  并按全部 structural observations 计算 conditional 与 polymorphic summary。grouped evidence 仅保留
  deep-consensus attributes；relationship、lineage、naming 和 derived observation summary 统一累加
  `occurrenceCount`，而 repeated-observation confidence 仍按独立 observation 计数。
- negative profiling 的目标边界是不从普通 SQL/naming 候选推断 tenant、软删除、时间窗口、
  归档或行过滤上下文，只验证 typed 声明 FK。内置 builder和core SPI consumer均遵守该规则，并从
  pre-merge structural guards判断conditional/polymorphic。若未来要对
  普通推断关系产生反证，必须先引入可审计的过滤上下文模型。
- offline literal-INSERT profiling 仍未实现，也不再是公开配置或 SPI 承诺。如未来重新引入，
  必须同时提供 typed producer、sample completeness 契约、资源边界和独立 SPI 升级。
- CLI argument、config file、config format、adaptor、input、connection、runtime 和 output write
  failure 的 mapping 已有测试；batch partial failure 保持 exit 13，并只写 typed error code 与固定
  脱敏文本。live namespace resolver 的 `LiveSourceConfigurationException` 已在 single-scan 映射为
  `CONFIG_FORMAT_ERROR`，batch case 保留同一 typed code，整体仍返回 `BATCH_PARTIAL_FAILURE`。adaptor
  SPI/type/id/capability/implementation failure 则统一使用 `AdaptorContractException` 和 `ADAPTOR_ERROR`。
- `DirectionConfidence` 和保留 error/evidence enum 继续作为 compatibility contract；所有 public production
  enum value 已由 AST discovery gate 逐值执行 Jackson serializer/deserializer round-trip，冻结的 CLI
  `ErrorCode` matrix 另有穷举集合断言和路径测试。
- root token-event 虽已使用 typed structural grammar/visitor，但复杂 routine、业务查询和部分 DDL evidence coverage 仍弱于对应 full-grammar；后续应继续扩展 typed grammar/visitor，不能恢复 scanner、regex 或名字过滤。
- full-grammar profile 当前覆盖 MySQL 5.7/8.0、PostgreSQL 16/17/18、Oracle 12c/19c/21c/26ai 与 SQL Server 2016/2017/2019/2022/2025；新增大版本需新增 adaptor module、严格 versioned fixture 和版本边界测试。
- Oracle/SQL Server permission vendor code 已从 adaptor 边界传入共享 classifier；单测验证不替代真实 driver/version smoke。
- PostgreSQL/SQL Server database-DDL 当前明确保持“关系解析骨架”；只有产品引入数据库回放需求时，
  才扩展为包含 type modifier、default、identity/generated/computed/collation 的完整 declaration。
- Live collector 的 JDBC proxy 测试不能替代真实 MySQL/PostgreSQL/Oracle/SQL Server 权限、版本和 catalog 组合 runtime smoke。
- PostgreSQL/SQL Server 当前选择拒绝显式跨 database catalog，而非实现 catalog-qualified 系统查询；
  resolver与`ScanEngine`会中止scan，single/batch CLI均将该执行期`LiveSourceConfigurationException`
  归类为`CONFIG_FORMAT_ERROR`。
- Oracle live owner 解析已统一为显式 schema、connection schema、metadata username 三层 fallback；
  都不可用时在首条 catalog SQL 前抛出脱敏 `LiveSourceConfigurationException`，不会用空 owner 查询。
- `JsonResultWriter` 的 `includeWarnings=false` 已定义为完整公开隐藏：根和 fact-level warning 数组均为空，
  `summary.warningCount=0`；内部 warning 与 CLI 退出判断不变，semantic strict reader 可消费该输出。
- 生产 Javadoc 已清除已知泛化模板，`Assembly` / `Factory` / `Assembler` / `Resolver` / `Index` 已进入
  门禁；重命名后的 `ResultAssembler`、`RelationshipAliasResolver` 和 `RelationshipCandidateFactory` 具备
  具体双语职责说明。
- formal semantic normalization 与离线 `SemanticKgBuilder` 均拒绝无证据/不可解析 evidence 及冲突
  node/edge ID；完全相同 ID/content 仅做幂等去重。`SemanticEventExtractor` 的结构分类只消费 typed
  `sourceObjectType` / `mappingKind`，缺失时使用中性默认值，不读取 detail/path/source 前缀。
- correctness fixture 唯一性已闭环：fixture-local input 在相同执行配置下按 content hash 去重，
  correctness tree 外的 tracked sample-data 以规范 repo-relative path 作为独立 source-asset identity。Common 重复 fixture 已合并，MySQL 5.7 三个独立资产路径继续分别验收。
- 双语 package Javadoc 和具体类/大方法 Javadoc 架构门禁能验证结构类别和禁用模板，但不能自动证明每句话与调用链一致；内容准确性仍需代码评审。
- 更广泛的 Oracle 官方语法覆盖仍需要补齐；当前 versioned sample-data golden 不能替代官方版本边界测试。
- SQL Server 已有独立 adaptor，不回退到 MySQL/PostgreSQL/Oracle parser；后续需要补更多 Microsoft 官方逐版本 T-SQL family 和 runtime smoke。
