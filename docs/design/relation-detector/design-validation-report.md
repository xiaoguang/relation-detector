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

代码中的主链行为与设计基本一致。以下是当前能力事实与明确保留的版本/环境边界，不应误写成四个实现缺口：

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
| correctness golden | 当前 parser 输出与已保存 fingerprint 一致；生产与 correctness 共用 execution service 和事实抽取器。common direct structured-parser 路径与 production runner 共用 `StructuredSqlParseExecutor`，因此覆盖同一个 structured-result isolation/validation 边界。 | common fixture 不覆盖 parser profile selection、runtime fallback 和 live source orchestration；golden 本身没有 false positive/false negative；目标数据库 runtime 一定接受 SQL。 |
| SQL/版本/语义审计 | 具体 SQL、官方版本文档和 parser output 可以互相解释。 | 未审计 statement family 的完整覆盖。 |

本报告中的“通过”默认只表示对应层级通过；不得把 zero diagnostics 或 count parity 升格为 SQL/语义正确性证明。

## 当前收敛状态来源

Closure 状态的唯一所有者是 [Code / Design Traceability](code-design-traceability.md)。本报告只说明验证方法和证据边界，不复制 closure ID 或状态表，避免两份手工状态随实现分叉。

2026-07-26 冻结的有限矩阵已经按 traceability 中的直接测试闭合：

1. profiling候选以live metadata证明物理存在和类型兼容，稳定排序/去重后再消费配额，声明FK可按配置
   绕过unindexed-target gate。
2. MySQL functional/invisible、PostgreSQL partial/INCLUDE、SQL Server filtered/INCLUDE/disabled index
   不再被提升为无条件单列或lookup证据。MySQL live policy同时校验`SUB_PART`、ordinal、visibility与
   expression；token/full DDL分别保留typed prefix member。prefix/组合/expression unique不再证明
   成员单列唯一，合法首个物理成员仍可支持lookup。
3. 内置adaptor保留SPI v6 identity hook但不声明weight-adjustment capability；core只调用明确声明能力
   的实际策略。
4. semantic derived path、逐项typed ref、review section和自动review identity均严格闭包。
5. reconciliation只允许冲突选择和既有对象展示名称修改，不能新增对象或relation。
6. `final-only`从完整staging构建独立发布候选，晚期失败仍保留已完成shard审计材料。
7. semantic CLI将参数/配置/API key缺失映射为exit 2，执行期失败映射为脱敏exit 1。
8. Java verification强制核对writer定义的10个summary字段，并以
   `providedSourceLocationsValid`和实际校验数量表达位置契约。

Fingerprint继续作为manifest中的审计产物，不另设tracked baseline；`verify-all`允许dirty，
clean-worktree发布证明由`verify-release`负责。这些是冻结的窄契约，不是未闭合缺口。

### 2026-07-30 File DDL inventory 与 Semantic 发布门禁

file-only scan不再因为未连接JDBC而必然输出`NOT_REQUESTED`。只有配置显式声明DDL文件覆盖完整扫描
scope时，core才把各parser独立产生并经契约校验的typed table、column、foreign-key和index事件组装为
metadata inventory，并输出`status=COMPLETE, basis=DDL_DECLARATIONS`。默认
`EVIDENCE_ONLY`仍不声明完整性；typed gap、冲突、空catalog或consumer引用不闭合继续失败。

DDL basis证明的是“配置scope内的声明集合已完整处理”，不是整个数据库实例的live snapshot，也不
虚构parser没有提供的数据类型。类型payload缺失时列类型保持`UNKNOWN`。发布链新增两层验证：Java
流式结果校验要求19类parser的38份direct/derived JSON均为evidence-backed COMPLETE且inventory一致；
随后对38份结果逐一构建完整deterministic KG，并为全部direct/derived结果生成可重建
`gpt-5.6-sol/xhigh` Codex-session request package。确定性矩阵得到38/38 KG与bundle reconstruction
PASS；每个owned shard均有一份精确引用sidecar。shard数量和最大估算输入属于运行产物，由ignored
verification summary/manifest记录；设计契约只要求估算值不超过统一hard门限。
模型响应、reconciliation和最终closure由独立enrichment tier验证，不能以request-only替代。该项状态为`MATCHED`。

### 2026-07-28 四项反向审计的当前状态

本轮按更新后的设计重新读取生产代码与测试，冻结并处理以下四项：

1. MySQL live与token/full DDL已将index member分类为完整列、前缀列或表达式；prefix、expression
   和composite unique不再产生成员单列`TARGET_UNIQUE`，合法首个物理成员仍可支持lookup。公共
   `MetadataIndexFact.members`保存全部成员的typed kind、连续ordinal及mixed交错顺序；旧accessor
   是兼容投影，顺序不明确的旧mixed shape由core拒绝。
2. semantic内存validator与磁盘reader共用metadata closure rules，完整验证member column、FK引用端、
   cardinality/ordinal、typed index member shape及identity唯一性；mixed index的表达式ordinal和
   完整交错顺序已进入同一闭包。
3. semantic磁盘链路已实现全局event归并、typed component与唯一owner计划，raw-byte只控制I/O
   window。event contribution的标量、计数和成员分开落盘并外排归并，base descriptor在成员列表
   与association key物化前受token门限，association完成后再执行组合门限。standalone envelope通过
   Jackson单字符串上限和有界writer执行共享累计码点预算，字段通过后才物化`JsonNode`。临时树使用
   不跟随符号链接的`walkFileTree`逐项清理。默认、发布和extended memory profile分别固定为
   1 MiB/96 MiB、128 MiB/96 MiB和1 GiB/512 MiB。
4. 400/450有效代码行门禁已扩展到relation-detector与semantic-layer全部目标职责后缀；原超限store、
   artifact writer、output writer和fingerprinter已按真实职责拆分，无allowlist或改名绕过。

以上状态以[Code / Design Traceability](code-design-traceability.md)为唯一矩阵。索引证据政策、
typed mixed-member wire、consumer closure、结构对抗内存门禁和职责门禁均已闭合。内存门禁证明
指定输入形状在固定堆下有界完成或确定性拒绝，不能外推为业务吞吐承诺。后续发布级回归仍按完整
验收链执行。

### 历史已闭合边界

上一轮冻结的四项实现已经按其当时的测试边界闭合：

1. `AdaptorResultContractValidator` 对 metadata、object、database-DDL 和 fallback
   relationship parser 的外部结果执行 detached copy、全批校验和延迟提交；callback warning
   由 core 丢弃 plugin 文本后重建。
2. `EvidenceWeightAdjustmentService` 对 hook 返回的 evidence 校验 identity/provenance，只允许修改范围内
   score；relationship 与 naming 的列表 replacement 全部成功后才应用，warning 副作用会拒绝。
3. live database-DDL namespace qualification 现在复制完整 candidate attributes，并由直接 API
   测试锁定 confidence、evidence、rawEvidence、warnings 和 attributes 状态。
4. SQL runner 的空 policy helper、误导 Javadoc 和 direct execution 无效 config overload 已删除；
   fallback parser 通过 detached context 与统一结果契约后才转发。

这些修改收紧了外部 v6 adaptor 的**生产主链**，不改变内置 parser 主事实语义或 golden。上一轮冻结的五项及parser selection前置shape处理均已闭合：

1. `AdaptorParseResultContractValidator` 对 `SqlLogExtractor`、`DialectScriptFramer` 以及生产 runner 中的
   `StructuredSqlParser` / `StructuredDdlParser` stream/result/warning 执行 detached、allowlist 和延迟提交
   校验。普通 full-grammar runtime failure 可以 fallback，但失败尝试 warning 被丢弃且固定脱敏；
   validator 已识别的`AdaptorContractException`不允许fallback。selector只捕获外部parser调用；
   null result/null attributes的shape检查和selection attribute装配发生在catch之外，因此同样保持
   contract violation类别且不会调用token parser。
2. `EvidenceWeightAdjustmentService` 向外部 hook 提供 deep-detached evidence 与 deep-immutable
   `AdaptorContext.options`，只接受 score 变化，并由 core 从 baseline 重建返回 evidence。
3. `ScanTaskExecutor` 在串行和并行路径原样保留 `AdaptorContractException`，CLI 均归类为
   `ADAPTOR_ERROR`。
4. MySQL `SHOW CREATE TABLE` 与 Oracle `DBMS_METADATA.GET_DDL` 成功但零行时生成带表身份的
   `DEFINITION_UNAVAILABLE`，不构造空 definition。
5. 双语 Javadoc 门禁已包含 `Executor/Runner/Scheduler/Loader/Normalizer/Dispatcher/Selector`，
   并补齐本轮实际命中的编排类和大型方法。

fake JDBC 和 adversarial unit tests 可以证明已覆盖的 core 合约；真实数据库驱动版本、权限行为与网络故障仍属于环境 smoke，
不能由上述单元测试替代。

本轮从 public/direct API、correctness 和 semantic consumer 反向追踪后，复核了五个相邻边界：

1. `StatementExecutionService.executeSql(StructuredSqlParser, ...)`、production runner 与
   `StructuredSqlRelationshipParser.parse(...)` 共用 `StructuredSqlParseExecutor`，在 detached context 中
   原子校验完整结果后才提交 warning 或抽取事实；common correctness direct 路径不再绕过该边界。
2. `AdaptorParseResultContractValidator` 按 sealed event family 验证必需 typed payload，并把 statement
   行范围、source/object/block identity 和 parser-origin provenance 作为事实抽取前的硬契约。
3. fallback `SqlRelationParser` evidence 的规范化 `source` 必须等于输入 statement source name；跨 source
   注入会使整个 fallback outcome 失败。
4. `SemanticEventExtractor` 使用完整 typed source identity 对 raw contributions 去重和排序；routine/trigger
   按对象聚合，普通 SQL write 按 statement/source object 与 target table 聚合，同一 event 汇总多个
   mapping kind 和不同证据位置。
5. `StructuredSqlRelationshipParser`、`StructuredSqlParseExecutor` 与 `SqlRelationParserRunner` 的双语说明已按
   当前 facade、trust boundary 和 extractor delegation 职责校准，并由架构测试锁定 direct consumer。

候选生成侧的第4项已经闭环，并使用精确 `sourceObjectType + sourceObjectIdentity`：
PostgreSQL full/live 路径使用只包含输入参数类型的 identity signature，pure `OUT`、参数名和默认值
不会进入身份；compact token-event 使用 typed kind/name 与声明 statement identity，避免复制完整
参数类型 grammar。`SemanticEventExtractor` 仍把 coarse semantic source type 分类为 `ROUTINE`，
但 group key/stable ID 使用精确 provenance。formal normalization从已验证的
`eventCandidateRef`派生默认event ID，不再处理`ROUTINE:`前缀。formal entity/event/metric/dimension
缺省ID统一通过`SemanticCanonicalIdentity`和`StableSemanticId`生成；物理/业务entity规则与shard
canonicalizer复用。显式输入ID不变。graph edge已脱离display slug；自动review先规范化section，
再以`targetSection + targetRef + type`生成稳定ID，可变reason不参与identity。
未审计 SQL statement family 和真实数据库 runtime smoke 继续按各自 backlog/环境边界管理。

## 本轮代码结构注释审视

生产代码结构注释的目标分成三层。package 契约、手写 public/protected 顶层类型和编排类都强制中文 / English 双语、具体且非模板的设计说明：

- package 层：每个生产 package 的 `package-info.java` 用中英双语说明职责、输入输出、上游/下游和禁止承载的逻辑。
- class 层：所有手写 public/protected 顶层类型和编排职责类使用 `CN:` / `EN:` 说明负责什么、不负责什么、位于哪条链路。
- method 层：有效代码超过 40 行的非 override 编排方法说明输入效果、输出/副作用和失败边界；简单 getter、record accessor 和显而易见的小工具方法不强制注释。

代表性检查范围包括（实际架构测试按 source roots 扫描，不以此列表作为穷举 allowlist）：

```text
contracts
contracts.model / metadata / parse / spi / scoring
core.scan / config / input / adaptor / execution / result
core.parser.antlr / parser.runtime / parser.tokenevent / parser.fullgrammar
core.relation / lineage / ddl / log / metadata / output / diagnostics / scoring
cli
mysql / mysql.tokenevent / mysql.fullgrammar.v5_7 / mysql.fullgrammar.v8_0
postgres / postgres.tokenevent / postgres.fullgrammar.common / postgres.fullgrammar.v16 / v17 / v18
oracle / oracle.tokenevent / oracle.fullgrammar.common / oracle.fullgrammar.v12c / v19c / v21c / v26ai
sqlserver / sqlserver.tokenevent / sqlserver.fullgrammar.common / sqlserver.fullgrammar.v2016 / v2017 / v2019 / v2022 / v2025
```

逐包审视结论：

- `contracts` 只承载跨模块契约、模型、SPI、parse result 和默认 score 常量，不依赖 core。
- `core.scan`只负责扫描编排与运行上下文；配置、输入、adaptor契约、statement执行和结果模型分别位于`core.config/input/adaptor/execution/result`。
- `core.parser.runtime`负责parser mode/profile选择；`core.parser.tokenevent`与`core.parser.fullgrammar`是事件来源基础设施，均不承载数据库版本实现。
- relationship / lineage语义被隔离在`core.relation`与`core.lineage`。
- `core.ddl` 是 token-event DDL event 支撑；DDL relationship 转换仍在 `core.relation.DdlRelationExtractionVisitor`。
- `adaptor-mysql` / `adaptor-postgres` / `adaptor-oracle` 根包只做 adaptor 装配；token-event parser 位于各自 `tokenevent` 子包，版本化 full-grammar 位于 `fullgrammar/v8_0`、PostgreSQL `fullgrammar/v16|v17|v18` 或 Oracle `fullgrammar/v12c|v19c|v21c|v26ai`，PostgreSQL/Oracle 公共抽象位于 `fullgrammar/common`。
- 没有发现 core 直接 import MySQL/PostgreSQL/Oracle/SQL Server full-grammar implementation 的职责倒置。
- 没有发现 adaptor 侧重复实现 relationship / lineage semantic extractor。
- 没有发现 contracts 反向依赖 core 的设计破坏。
- 手写生产 package、public/protected 顶层类型、已登记 suffix 编排类与大型编排方法已有中英双语契约。
  `DialectGrammarArchitectureTest` 和 `SemanticDocumentationArchitectureTest` 使用 JDK compiler/doc-tree API 验证 package 的 `CN:` / `EN:` 标记、
  职责、输入、输出、上下游与禁止边界，并对公开类型、当前登记的编排 suffix 和大方法验证最小具体内容及泛化模板禁止。generated Java、
  record accessor、getter 和显而易见的小方法按规则排除。当前 suffix 已覆盖 `Assembly`、`Factory`、
  `Assembler`、`Resolver`、`Index`、`Executor`、`Runner`、`Scheduler`、`Loader`、`Normalizer`、
  `Dispatcher` 与 `Selector`；`ResultAssembler`、`RelationshipAliasResolver`、
  `RelationshipCandidateFactory` 和 semantic factories/registries 具备完整双语设计说明。描述是否准确
  反映实际调用链继续由代码评审确认。

本报告和 `phase-06-parser-enhancement.md` 已按上述代码注释刷新。若后续新增生产 package、核心类或跨链路调用，必须同步新增/校准代码注释，并在 Phase 6 的结构表与调用链中登记。

本轮新增 [代码与设计对应审视报告](code-design-traceability.md)，按 CLI、ScanEngine、SQL/DDL parser、relationship、Data Lineage、confidence、输出和 correctness 报告逐环节列出代码入口、设计章节、测试覆盖和差异状态。

## 需要特别说明的实现事实

### 1. fallback 只发生在 parser selection 层

当`parser.mode=auto`且无法根据database type/profile/version选中full-grammar profile时，runner静默选择
adaptor暴露的token-event parser。显式`parser.mode=full-grammar`选择失败时才记录selection fallback诊断。

如果full-grammar profile已经选中，full-grammar parser自己返回structured events、partial result和
warning；它不会在event层委托token-event补齐事件。普通runtime hard failure会记录固定warning并
fallback到token-event。进入`AdaptorParseResultContractValidator`后的contract violation直接失败；
selector在外部parser调用返回后才执行shape检查与selection attribute装配，因此null result/null attributes
也直接失败，不会被误判为可恢复failure。

### 2. SQL relationship 与 Data Lineage 共享 structured result

生产`ScanEngine.scan(...)`当前通过`SourceCollectorPipeline`和`StatementParsePipeline`进入
`StatementExecutionService`。单条SQL只解析一次，同时生成relationship candidates并把同一个
`StructuredParseResult`交给Data Lineage extractor。SQL naming rule不在statement层执行；全部source
收集后、最终`RelationshipMerger`之前，scan-level `EvidenceEnhancementService`对原始candidate集合执行一次。
direct structured-parser overload不经过runner，但与runner共用`StructuredSqlParseExecutor`，因此
detached context、result validation和warning延迟提交契约一致。

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
- 全量 correctness golden：`bash relation-detector/scripts/run-correctness-isolated.sh`，必须检查聚合 run summary 而不只看 Maven exit code。
- 最终 parser CLI 矩阵、generated report 与 canonical output 验收：`bash relation-detector/scripts/verify-all.sh`。
- 无缓存 clean 复验：`bash relation-detector/scripts/verify-release.sh`；它先运行 no-cache smoke reactor，再进入隔离 full correctness 和 sample-data。
- 报告验收：显式运行 `CorrectnessSummaryGeneratorTest` 和 `DataLineageAuditGeneratorTest`，并传 `-DrunGeneratedReportTests=true`。
- 跨 parser 差异需联合阅读 [`parser-comparison-summary.md`](../../parser-audit/parser-comparison-summary.md)、各版本边界审计与 [`sample-data-output-audit-backlog.md`](../../parser-audit/sample-data-output-audit-backlog.md)；它们分别维护当前统计、确认的版本差异和未关闭问题。

当前`verify-all.sh`只有在最终`verification-manifest.json`实际生成且状态为PASS时才形成完整的
**当前工作区verification session证据**；它会记录dirty worktree但不会据此失败。无缓存且干净工作树的
正式发布证明仍必须使用`verify-release.sh`。
sample-data CLI生成38份JSON并不等同于后处理完成。内部Java verification子进程默认使用512 MiB堆，
流式生成`result-validation.json`并以外存归并计算fingerprint；manifest不再重读38份大JSON。
外存处理以更多磁盘I/O换取堆边界，因此完成条件仍是最终manifest，而不是中途CLI文件数量。
当前fingerprint是可复核artifact，没有与仓库内受控expected hash执行发布判定；它证明本次产物可比较，
不单独证明相对某个历史基线无变化。

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
2. `AdaptorContractValidator` 在 JDBC 前一次性校验并冻结 adaptor 的 SPI/id/database types/capabilities/identifier rules/grouped collectors/parsers/profiling；`ScanCapabilityValidator`只消费该快照验证实际请求。`AdaptorCollectors`不再把null Optional member归一为空，null顶层grouped record、nested member、core可见的畸形shape和null scope统一为`AdaptorContractException`，single/batch稳定保持`ADAPTOR_ERROR`。live DDL要求structured DDL parser，live objects要求structured SQL parser，纯文件scan不新增live capability要求。
3. `IndexEvidencePolicy` 不允许组合 PK/UNIQUE 成员证明单列唯一；普通组合索引仅首列可支持 lookup /
   `SOURCE_INDEX`，不单独决定方向。MySQL live metadata 的 `subParts`、visibility 和 expression 与
   token-event、full v5.7、full v8.0 的 typed index member 语义一致：只有可见、无表达式、无前缀的
   单物理列 unique/PK 才产生 `TARGET_UNIQUE`；前缀、组合和 expression unique 均不会伪造单列唯一，
   但合法的首个物理成员仍可提供 lookup 证据。
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
10. 内置 connection、metadata、object、database-DDL 和 profiler 的 SQLException failure 共用
    `JdbcExceptionClassifier` 与 `LiveDiagnosticSanitizer`，且不输出 JDBC URL、rendered SQL 或 driver message。
    共享 classifier 只识别 JDBC 类型和 SQLState；Oracle 1031、SQL Server 229/916 由对应 adaptor 显式提供。
    Pipeline 对第三方 collector 返回的 null/blank definition、null element 或 null list 统一输出 `DEFINITION_UNAVAILABLE` 并跳过解析。
    metadata snapshot 和 object/database-DDL warning callback 已经 core 重验与重建；log extractor、
    script framer 与 SQL/DDL parser 的 statement/event/warning 由独立的
    `AdaptorParseResultContractValidator` 全批校验并延迟提交。production runner、direct
    statement overload 与 relationship facade 共用 `StructuredSqlParseExecutor`，不再存在
    validator之后绕过structured SQL结果契约的core旁路；selector在fallback catch之外附加selection
    attributes并显式拒绝null result/null attributes。`StructuredParseResult`和
    `ScriptFrameResult`保留null collection和null element供core validator识别；所有可见shape
    违约在事实或warning提交前原子失败，所有已覆盖shape违约都保持禁止fallback的错误类别。
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
15. `derivedPaths.minConfidence` 已按未舍入的 BigDecimal 衰减值执行输出过滤；低分 relationship、
    lineage 和 naming path 在路径/事实配额与 raw-evidence 聚合前排除，等于阈值保留，输出 confidence
    只在最终呈现时保留四位小数。
16. `DataProfilePipeline` 的 request candidate 和 `ProfileOutcomeContractValidator` 的返回 evidence
    均复用 core 递归 detachment 原语。输入/输出嵌套 list、set、map 不与插件共享，未知可变 attribute
    类型原子失败。negative eligibility在插件调用前从原scan candidate固化；插件注入声明FK、删除guard、
    修改request或延迟修改result均不能回写scan或改变core-owned负向资格。
17. `ProfileOutcomeContractValidator` 的所有违约统一使用 `AdaptorContractException`。direct API
    原样抛出，single CLI 与 batch case 均归类为 `ADAPTOR_ERROR`；全批延迟提交保证最后一个 outcome
    失败时也不留下部分 profiling 状态。
18. `derivedPaths.maxFacts`在relationship、lineage和derived naming全部生成、naming稳定合并后，
    按`RELATIONSHIP`、`DATA_LINEAGE`、`NAMING`及类内canonical key实施scan级总配额；低分path先过滤，
    被裁剪naming的可选引用同步清理或重写，`0`保持全部结果。
19. `SourceNameNormalizer`对工作区文件输出相对路径，对工作区外文件输出
    `external/sha256-<完整摘要>/<文件名>`；读取竞态或失败输出`external/unavailable/<文件名>`。
    script framing、DDL/object、common与四方言log、parse result及file warning共用该source。

上述 live definition、warning sanitization 与 collector fail-fast 主链已有 focused tests；当前完整
验收数量应从生成报告与 verification manifest读取，不在本文复制。direct Java `ScanConfig.*Paths`、
public SPI/type/id 与 parser-half 边界已有 focused API tests；file-based fact parity 仍不能覆盖 profile-only、
跨 catalog 或真实 live 权限行为，也不能代替四个数据库的真实权限、版本、driver 与 catalog 组合 runtime smoke。
PostgreSQL/SQL Server 重建 DDL 明确属于 relationship parser 使用的 structural skeleton，不承诺完整
可回放 declaration；若未来增加回放契约，需另行补齐 type modifier、default、identity/generated/
computed/collation 并建立数据库执行测试。

代码结构方面，`DialectGrammarArchitectureTest` 对relation-detector与semantic-layer全部手写生产
Java实施职责规模门禁：Visitor/Collector上限400，
Analyzer/Support/Extractor/Resolver/Merger/Framer/Facade/Store/Planner/Publisher/Fingerprinter/
Canonicalizer/Handler/Writer上限450；
Javadoc、普通注释和空行不计入职责规模。generated Java、top-level record DTO 和 `package-info`
不参与行数约束，门禁没有永久allowlist。expression、relationship 和 lineage入口已经抽出typed helper；
`StructuredScriptFramer` 只负责编排，并由 200 行门禁保护；MySQL、PostgreSQL、Oracle、common 和
SQL Server 的 slice 算法位于五个独立 planner，各受 250 行门禁保护。行数和职责拆分已匹配，
top-level record 豁免通过 JDK compiler AST 检查实际顶层声明；普通类中的注释或字符串即使包含
`record TypeName(` 也不能绕过门禁。semantic input/result store、两套artifact writer、JSON writer
和canonical fingerprinter已按生命周期、校验、事务、section rendering与对象字段外排排序职责拆分；
原public facade保持不变。全仓职责规模状态为`MATCHED`。

## 后续技术债

- 真实MySQL/PostgreSQL/SQL Server版本的live metadata和profiling smoke仍是环境验收边界；fake JDBC
  测试不宣称覆盖所有驱动版本。
- Fingerprint按产品决定保留为manifest审计产物，不建立第二套tracked expected baseline；事实正确性仍由
  correctness golden、CLI结果验证和语义审计共同承担。
- `providedSourceLocationsValid=PASS`只证明实际提供的位置合法，报告同时记录校验数量；它不宣称
  live或derived事实必须具有文件位置。
- `verify-all`允许dirty工作树以支持开发验证；clean-worktree正式发布证明必须使用`verify-release`。
- Catalog identity 的 direct fact、live profile 和 derived path 边界已由 focused negative tests 闭环；
  当前 sample-data 仍不用于替代跨 catalog/quoted case 的专门测试。
- relationship 已将完整、顺序无关的 guard 数组纳入 candidate/observation/fingerprint identity，
  并按全部 structural observations 计算 conditional 与 polymorphic summary。grouped evidence 仅保留
  deep-consensus attributes；direct relationship、lineage和naming observation summary累加
  `occurrenceCount`，而 repeated-observation confidence仍按独立observation计数。Derived path不再
  构造证据笛卡尔积observation，而以typed evidence set和`BigInteger combinationCount`保留等价审计信息。
- negative profiling 的目标边界是不从普通 SQL/naming 候选推断 tenant、软删除、时间窗口、
  归档或行过滤上下文，只验证 typed 声明 FK。内置 builder和core SPI consumer均遵守该规则，并从
  pre-merge structural guards判断conditional/polymorphic。若未来要对
  普通推断关系产生反证，必须先引入可审计的过滤上下文模型。
- offline literal-INSERT profiling 仍未实现，也不再是公开配置或 SPI 承诺。如未来重新引入，
  必须同时提供 typed producer、sample completeness 契约、资源边界和独立 SPI 升级。
- CLI argument、config file、config format、adaptor、input、connection、runtime 和 output write
  failure 的 mapping 已有测试；batch partial failure 保持 exit 13，并只写 typed error code 与固定
  脱敏文本。`LiveSourceConfigurationException`已在single-scan映射为`CONFIG_FORMAT_ERROR`，batch case
  保留同一typed code；MySQL catalog/schema canonicalization冲突也使用同一typed异常，并在连接或查询前
  失败。adaptor
  SPI/type/id/capability/implementation 以及validator已接收的adaptor result-contract failure使用
  `AdaptorContractException / ADAPTOR_ERROR`；`ScanTaskExecutor` 在串行和并行路径保留同一异常类型。
  full-parser result的null attributes由selector/core wrapper显式转为`AdaptorContractException`，
  SQL/DDL负向测试确认token parser调用次数为0且失败尝试warning不泄漏。
  profile outcome contract violation 同样保持该类别，single 与 batch 的直接契约测试覆盖安全文本和
  case 级 error code。
- `DirectionConfidence` 和保留 error/evidence enum 继续作为 compatibility contract；所有 public production
  enum value 已由 AST discovery gate 逐值执行 Jackson serializer/deserializer round-trip，冻结的 CLI
  `ErrorCode` matrix 另有穷举集合断言和路径测试。
- 当前 natural corpus 与 semantic-equivalent benchmark 没有暴露未分类的 token/full parser gap。未进入 corpus 的官方 statement family 仍是 coverage backlog；只有具体同语义 SQL 和 exact observation 证明差异时，才能记为 typed visitor gap，不能以笼统“root 更弱”或数量差代替审计，也不能恢复 scanner、regex 或名字过滤。
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
- 生产 Javadoc 已清除已知泛化模板，`Assembly` / `Factory` / `Assembler` / `Resolver` / `Index` 以及
  `Executor/Runner/Scheduler/Loader/Normalizer/Dispatcher/Selector` 均已进入门禁；本轮命中的
  package-private 编排类和超过 40 行方法已补齐具体双语设计说明。
  `DialectGrammarArchitectureTest` 还直接验证 warning 内容边界：`ProfileOutcome` 是不可信 plugin
  envelope，core 仅验证 failure type/code 并重建固定 warning；parser/file warning 可为本地审计保留
  raw SQL/DDL/异常文本，live JDBC warning 必须经过 `LiveDiagnosticSanitizer`。
- `TableResultWriterTest` 固定 relationship 输入顺序、evidence 首次出现去重、长文本完整输出、空关系
  warning 明细和无副作用契约；`TableOutputCliTest` 固定 YAML override、文件输出及 table/direct-output
  冲突。table 仍不探测终端宽度、不折行/截断，只承诺轻量人工阅读视图。
- formal semantic normalization 与离线 `SemanticKgStore/SemanticReferenceClosureStore` 均拒绝无证据/不可解析 evidence 及冲突
  node/edge ID；完全相同 ID/content 仅做幂等去重。`SemanticEventExtractor` 的结构分类只消费 typed
  `sourceObjectType` / `mappingKind`，缺失时使用中性默认值，不读取 detail/path/source 前缀。当前
  event 聚合是 routine/trigger 对象级、普通 SQL statement/source + target-table 级；routine identity
  使用精确对象类型与`sourceObjectIdentity`，PostgreSQL full/live signature和compact token statement
  identity分别守住各自能力边界。
- relation-detector JSON现在始终携带metadata inventory status、scope、counts和四类typed facts；
  `COMPLETE`只表示配置scan scope完整。`PARTIAL/UNAVAILABLE`不会被warning隐藏伪装，direct/derived输出
  使用同一inventory。
- `build/extract/e2e`主链使用流式`SemanticInputStore`、section spool、外排identity/offset/component
  索引、全局owner plan和path-backed shard，不持有完整scan或最终全局KG。非COMPLETE或引用不闭合
  inventory在模型调用和正式artifact写入前失败。event contribution与association分别以磁盘descriptor
  在对应列表物化前执行预算；disk union-find使用有界迭代路径压缩。standalone
  `normalize-extraction`在`readTree()`前限制raw结果，evidence envelope以parser string constraint和
  有界writer累计输入预算。临时树通过`walkFileTree`逐项清理。发布128 MiB/96 MiB及按需
  1 GiB/512 MiB门禁与结构对抗测试共同守住该边界。
- 当前外排字符串索引的chunk排序、多路归并及整行/tab-key二分统一使用unsigned UTF-8 byte order，
  supplementary Unicode与组合字符测试固定写入/查找一致性。canonical JSON object field sorter采用
  固定32路多阶段归并，中间chunk只引用value spool区间；超宽object、低文件描述符、跨组重复键和
  历史SHA测试共同固定bounded descriptor与canonical byte兼容边界。
- correctness fixture 唯一性已闭环：fixture-local input 在相同执行配置下按 content hash 去重，
  correctness tree 外的 tracked sample-data 以规范 repo-relative path 作为独立 source-asset identity。Common 重复 fixture 已合并，MySQL 5.7 三个独立资产路径继续分别验收。
- release、correctness 与 sample-data 已共享 `heavy-job-lock.sh`。最外层 owner 从 smoke 开始持锁到
  manifest 完成，嵌套入口验证并借用同一 token；不完整 owner 元数据 fail-closed，完整 dead owner
  通过原子 quarantine 回收。并发首次抢锁、双向 active owner、stale owner、borrower 与错误 token
  均有 shell contract test，sample-data 发布默认 case parallelism 继续为 1。
- 双语 package Javadoc 和具体类/大方法 Javadoc 架构门禁能验证结构类别和禁用模板，但不能自动证明每句话与调用链一致；内容准确性仍需代码评审。
- 更广泛的 Oracle 官方语法覆盖仍需要补齐；当前 versioned sample-data golden 不能替代官方版本边界测试。
- SQL Server 已有独立 adaptor，不回退到 MySQL/PostgreSQL/Oracle parser；后续需要补更多 Microsoft 官方逐版本 T-SQL family 和 runtime smoke。
