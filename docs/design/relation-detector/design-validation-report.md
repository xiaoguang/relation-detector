# 设计一致性检查报告

## 检查目标

本报告用于检查当前设计文档是否与代码实现保持一致，重点覆盖：

- core / adaptor 职责边界。
- `token-event` 与 `full-grammer` parser 模式。
- SQL / DML relationship 抽取。
- Data Lineage v1。
- DDL relationship 抽取。
- correctness fixture 与生成报告。

## 检查结论

当前主设计已经从早期 “Simple / ANTLR primary / shadow” 迁移为：

```text
parser.mode=auto|full-grammer|token-event

token-event:
  ANTLR lexer/parser support
  -> token-event structured events
  -> relationship / lineage / DDL semantic extractor

full-grammer:
  adaptor-owned versioned grammar profile
  -> typed parse-tree visitor
  -> same structured events
  -> same semantic extractor
```

代码中的主要行为与设计一致：

- MySQL/PostgreSQL 是当前工程覆盖最广的支持目标；两者已有 versioned parser、live collectors 和完整 sample-data regression 资产，但仍保留本报告后文列出的 parser/provenance 缺口。
- Oracle 是当前初始支持目标：已有 adaptor、Oracle token-event fallback、root correctness golden 和 `INCOMPLETE_VERSIONED` versioned full-grammer，但更广泛的 Oracle 官方语法覆盖仍是 backlog，当前状态为 `INCOMPLETE_VERSIONED`。
- SQL Server 已接入 adaptor、root token-event baseline 和 `sqlserver/2016|2017|2019|2022|2025` versioned full-grammer sample-data golden；sample-data 已收敛为自然 ERP 业务 SQL；高密度关系探针迁入 semantic-equivalent benchmark。首批 Microsoft 官方逐版本 T-SQL 边界已经进入 `.g4`、version-only fixture 和 architecture test。Database DDL collector 与 bounded data profiler 已实现；metadata/object collectors、更多 T-SQL family、version-only fixture 和 live runtime smoke 仍是 backlog。
- core 不直接 import MySQL/PostgreSQL/Oracle/SQL Server full-grammer 实现；版本化 module 由 adaptor 注册。
- Relationship 与 Data Lineage 是两个独立输出模型。
- Simple SQL/DDL parser 和旧 SQL/DDL parser mode 配置不再是当前能力。
- correctness fixture 以当前 parser golden 为回归基线；root token-event 与 versioned full-grammer 分别直接比对自己的 golden，不再用 token-event fallback 保护 full-grammer。Golden 通过只证明“输出没有偏离已审核基线”，不单独证明 SQL 资产符合真实数据库版本，也不证明每条 relation/lineage 的业务语义正确。

### 验证结论的三个层级

| 层级 | 能证明什么 | 不能证明什么 |
| --- | --- | --- |
| 架构测试 | parser ownership、token-event/full-grammer 独立、regex 边界、SPI 依赖方向。 | 某条 SQL 的 relation/lineage 一定正确。 |
| correctness golden | 当前 parser 输出与已保存 fingerprint 一致；生产与 correctness 共用 execution service。 | golden 本身没有 false positive/false negative；目标数据库 runtime 一定接受 SQL。 |
| SQL/版本/语义审计 | 具体 SQL、官方版本文档和 parser output 可以互相解释。 | 未审计 statement family 的完整覆盖。 |

本报告中的“通过”默认只表示对应层级通过；不得把 zero diagnostics 或 count parity 升格为 SQL/语义正确性证明。

## 本轮代码结构注释审视

本轮把生产代码结构注释分成三层，并要求中文 / English 双语说明同一职责边界：

- package 层：每个生产 package 的 `package-info.java` 说明职责、输入输出、上游/下游和禁止承载的逻辑。
- class 层：生产类 Javadoc 说明文件负责什么、不负责什么、位于哪条链路。
- method 层：关键 public 方法、核心编排方法、复杂 private helper 说明调用意图和边界；简单 getter、record accessor 和显而易见的小工具方法不强制注释。

检查范围覆盖：

```text
contracts
contracts.model / metadata / parse / spi / scoring
core.scan / parser / tokenevent / fullgrammer / relation / lineage / ddl
core.parse / log / metadata / output / diagnostics / scoring
cli
mysql / mysql.tokenevent / mysql.fullgrammer.v8_0
postgres / postgres.tokenevent / postgres.fullgrammer.common / postgres.fullgrammer.v16 / v17 / v18
oracle / oracle.tokenevent / oracle.fullgrammer.common / oracle.fullgrammer.v12c / v19c / v21c / v26ai
```

逐包审视结论：

- `contracts` 只承载跨模块契约、模型、SPI、parse result 和默认 score 常量，不依赖 core。
- `core.scan` 负责扫描编排；`core.parser` 负责 parser mode/profile 选择；二者没有承载数据库版本实现。
- `core.tokenevent` 与 `core.fullgrammer` 是事件来源基础设施；relationship / lineage 语义被隔离在 `core.relation` 与 `core.lineage`。
- `core.ddl` 是 token-event DDL event 支撑；DDL relationship 转换仍在 `core.relation.DdlRelationExtractionVisitor`。
- `adaptor-mysql` / `adaptor-postgres` / `adaptor-oracle` 根包只做 adaptor 装配；token-event parser 位于各自 `tokenevent` 子包，版本化 full-grammer 位于 `fullgrammer/v8_0`、PostgreSQL `fullgrammer/v16|v17|v18` 或 Oracle `fullgrammer/v12c|v19c|v21c|v26ai`，PostgreSQL/Oracle 公共抽象位于 `fullgrammer/common`。
- 没有发现 core 直接 import MySQL/PostgreSQL/Oracle/SQL Server full-grammer implementation 的职责倒置。
- 没有发现 adaptor 侧重复实现 relationship / lineage semantic extractor。
- 没有发现 contracts 反向依赖 core 的设计破坏。

本报告和 `phase-06-parser-enhancement.md` 已按上述代码注释刷新。若后续新增生产 package、核心类或跨链路调用，必须同步新增/校准代码注释，并在 Phase 6 的结构表与调用链中登记。

本轮新增 [代码与设计对应审视报告](code-design-traceability.md)，按 CLI、ScanEngine、SQL/DDL parser、relationship、Data Lineage、confidence、输出和 correctness 报告逐环节列出代码入口、设计章节、测试覆盖和差异状态。

## 需要特别说明的实现事实

### 1. fallback 只发生在 parser selection 层

当 `parser.mode=auto|full-grammer` 时，如果无法根据 database type / profile / version 选中 full-grammer profile，runner 会使用 adaptor 暴露的 token-event parser，并记录 fallback 诊断。

如果 full-grammer profile 已经选中，full-grammer parser 自己返回 structured events、partial result 和 warning；它不会在 event 层委托 token-event 补齐事件。只有 profile 缺失、版本不支持或 full-grammer hard failure 才 fallback 到 token-event，并输出 parser-mode fallback warning。

### 2. SQL relationship 与 Data Lineage 共享 structured result

`ScanEngine.scan(...)` 当前通过 `SourceCollectorPipeline` 和 `StatementParsePipeline` 进入 `StatementExecutionService`。单条 SQL 由 `StatementExecutionService.executeSql(...)` 调用 `SqlRelationParserRunner.parseStructuredAndRelations(...)`，一次结构化解析后同时生成 relationship candidates、naming evidence candidates，并把同一个 `StructuredParseResult` 交给 Data Lineage extractor。

这是当前实现事实，不改变 relationship / Data Lineage JSON schema，也不改变 semantic extractor 的职责边界。

### 3. full-grammer 与 token-event 共用语义层

full-grammer 只替换事件来源，不替换语义判断。以下逻辑仍在 Java semantic layer：

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
- adaptor 负责数据库元数据、日志/对象采集、token-event parser、versioned full-grammer module。
- MySQL `SQL_MODE` helper 只属于 MySQL full-grammer runtime，不是系统 `parser.mode`。

### Relationship 模型

结果：结构契约通过；跨 parser SQL 语义仍需逐条审计。

- `RelationType` 仍只保留 `FK_LIKE` 和 `CO_OCCURRENCE`。
- 列级弱共现使用 `RelationSubType.COLUMN_CO_OCCURRENCE`；evidence 保留具体 SQL 谓词来源，例如 `SQL_LOG_JOIN`、`SQL_LOG_EXISTS` 或 `SQL_LOG_SUBQUERY_IN`。
- `SQL_LOG_COLUMN_CO_OCCURRENCE` / `SQL_LOG_TABLE_CO_OCCURRENCE` 仍作为 enum、score 和 merger 兼容项保留，但当前生产 parser / extractor 不主动产出。前者由具体 SQL predicate evidence 替代；后者没有等价现役替代，纯表级同现默认不生成正式 relationship。
- 同表不同 alias 的 self-join 允许输出列级弱共现；同 alias 行内比较不输出关系。

### Data Lineage 模型

结果：结构契约通过；部分 parser 的 transform/source-role 与 provenance 仍有已确认缺口。

- `ScanResult` 已有独立 `dataLineages`。
- Data Lineage confidence 不参与 relationship confidence。
- v1 只输出数据库内部 `table.column -> table.column`，不做 Parameter Binding。
- `CUMULATIVE` 已作为累计/运行聚合 transform 与普通 `AGGREGATE` 区分。

### Parser 模式

结果：通过，需注意文档用词。

- 用户可见模式名是 `full-grammer` 与 `token-event`。
- Java package 使用 `fullgrammer` / `tokenevent`，因为 Java package 不能包含横线。
- `full-grammer` 具体版本实现在 adaptor，例如 `mysql.fullgrammer.v5_7|v8_0`、`postgres.fullgrammer.v16|v17|v18`、`oracle.fullgrammer.v12c|v19c|v21c|v26ai`。
- 无方言或无合理版本信息时使用 token-event。
- PostgreSQL full-grammer 当前有严格版本 profile：`postgresql/16`、`postgresql/17`、`postgresql/18`。三者分别有独立 versioned correctness golden。root `postgres` fixture 目录是历史兼容 baseline，不代表 `v1` 数据库版本。
- MySQL full-grammer 当前有 `mysql/5.7`、`mysql/8.0` profile，并已有独立 `test-fixtures/correctness/mysql/v5_7`、`test-fixtures/correctness/mysql/v8_0` versioned correctness golden。root `mysql` fixture 目录是 token-event baseline，不代表严格 MySQL 版本证明。
- Oracle full-grammer 当前有 `oracle/12c`、`oracle/19c`、`oracle/21c`、`oracle/26ai` profile，并已有独立 `test-fixtures/correctness/oracle/v12c|v19c|v21c|v26ai` sample-data correctness golden。当前 Oracle full-grammer 使用本版本 generated parser/visitor，但状态是 `INCOMPLETE_VERSIONED`，尚不代表 更广泛的 Oracle 官方语法 已完成。
- SQL Server full-grammer 当前有 `sqlserver/2016`、`sqlserver/2017`、`sqlserver/2019`、`sqlserver/2022`、`sqlserver/2025` profile，并已有独立 `test-fixtures/correctness/sqlserver/v2016|v2017|v2019|v2022|v2025` sample-data correctness golden。当前 SQL Server sample-data 使用跨版本保守 T-SQL 子集；首批官方逐版本语法边界已通过 2017 `STRING_AGG`、2022 `DATETRUNC` / `GENERATE_SERIES`、2025 `VECTOR(...)` fixture 和低版本拒绝测试锁定。

### 当前 golden 与验证结果

当前 correctness 资产统计如下：

| Golden 组 | Fixture | SQL / DDL | Relationship fingerprints | Lineage fingerprints | Diagnostics | Rel NAMING_MATCH | Top-level namingEvidence |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 全部 correctness | 1197 | 983 / 214 | 16151 | 5936 | 0 | 5935 | 7330 |
| common token-event | 39 | 34 / 5 | 756 | 328 | 0 | 219 | 441 |
| MySQL root token-event | 83 | 65 / 18 | 652 | 350 | 0 | 252 | 321 |
| MySQL full-grammer v5_7 | 89 | 71 / 18 | 696 | 414 | 0 | 257 | 327 |
| MySQL full-grammer v8_0 | 89 | 71 / 18 | 883 | 398 | 0 | 421 | 491 |
| PostgreSQL root token-event | 111 | 92 / 19 | 1365 | 332 | 0 | 353 | 353 |
| PostgreSQL full-grammer v16 | 111 | 92 / 19 | 1417 | 351 | 0 | 374 | 450 |
| PostgreSQL full-grammer v17 | 113 | 94 / 19 | 1420 | 364 | 0 | 375 | 451 |
| PostgreSQL full-grammer v18 | 114 | 93 / 21 | 1420 | 362 | 0 | 374 | 450 |
| Oracle root token-event | 41 | 33 / 8 | 623 | 247 | 0 | 241 | 309 |
| Oracle full-grammer v12c | 42 | 34 / 8 | 654 | 249 | 0 | 273 | 341 |
| Oracle full-grammer v19c | 43 | 35 / 8 | 654 | 249 | 0 | 273 | 341 |
| Oracle full-grammer v21c | 43 | 35 / 8 | 654 | 249 | 0 | 273 | 341 |
| Oracle full-grammer v26ai | 43 | 35 / 8 | 654 | 249 | 0 | 273 | 341 |
| SQL Server root token-event | 38 | 32 / 6 | 465 | 299 | 0 | 125 | 191 |
| SQL Server full-grammer v2016 | 39 | 33 / 6 | 767 | 299 | 0 | 370 | 436 |
| SQL Server full-grammer v2017 | 40 | 34 / 6 | 768 | 299 | 0 | 371 | 437 |
| SQL Server full-grammer v2019 | 39 | 33 / 6 | 767 | 299 | 0 | 370 | 436 |
| SQL Server full-grammer v2022 | 40 | 34 / 6 | 768 | 299 | 0 | 371 | 437 |
| SQL Server full-grammer v2025 | 40 | 33 / 7 | 768 | 299 | 0 | 370 | 436 |

验证入口：

- 日常 smoke：`mvn test`。
- 全量 correctness golden 与 generated report：`mvn -T 2 -Pacceptance verify`。
- 最终 parser CLI 矩阵与 canonical output 验收：`bash relation-detector/scripts/verify-all.sh`。
- 无缓存参考验收：`mvn -T 2 -Pacceptance -Dmaven.build.cache.enabled=false clean verify`。
- 报告验收：显式运行 `CorrectnessSummaryGeneratorTest` 和 `DataLineageAuditGeneratorTest`，并传 `-DrunGeneratedReportTests=true`。
- 跨 parser 差异需联合阅读 `docs/parser-audit/all-golden-semantic-review.md`、`parser-comparison-summary.md` 与 sample-data JSON/SQL 审计；它们不只包含 root token-event coverage 和 expected version delta，也包含 transform/source-role、provenance、derived dedupe 与版本资产真实性问题。

### DDL

结果：统一 event/merger 链路通过；Oracle 版本资产与部分 token-event/full-grammer typed coverage 仍未完成官方 runtime 验证。

- 当前 DDL production parser 是 token-event DDL structured parser 或被 parser selection 选中的 full-grammer DDL parser。
- 两者都输出 `DDL_FOREIGN_KEY` / `DDL_INDEX` 事件。
- `DdlRelationExtractionVisitor` 只消费 DDL events，不参与 SQL relation / lineage。

### 测试资产

结果：回归框架通过；测试资产真实性与 golden 语义不能只靠 runner 自动证明。

- `CorrectnessFixtureRunnerTest` 保护当前 parser golden。
- `CorrectnessSummaryGeneratorTest` 生成轻量索引报告。
- `DataLineageAuditGeneratorTest` 维护 lineage 审核入口。
- full-grammer 不再通过 token-event 跨 parser 兜底；版本化 SQL/DDL golden 直接暴露 full-grammer 的 missing / extra。
- `CliEndToEndGoldenTest` 保护从 CLI YAML/参数到 JSON 输出的完整系统链路，并复用现有 fixture golden。

## 反向审计发现的当前不匹配

2026-07 的结构/SQL 审计已经修复以下历史不匹配：derived lineage 按 canonical path 合并、不同 edge variant 保留为 raw observations；naming inventory 合并同 endpoint 的全部 metadata/DDL observation；Oracle natural assets 使用 `GENERATED ALWAYS AS (...) VIRTUAL` 且无参 routine 不再写空 `()`；common natural 只保留一份 canonical `payments`；已审计 CASE/scalar-subquery、trigger provenance、非平凡 self-update 和 Oracle transform gap 均由 typed context 测试保护。当前 38 份 direct/derived sample-data JSON 通过数组计数、路径去重、循环和来源可移植性检查，Oracle token-event 与四个 full-grammer profile 的 audited fact sets 一致。

以下项目仍是“修订后的设计要求、当前代码尚未满足”的事实，不应通过刷新 golden 或保持 zero diagnostics 掩盖：

1. **SQL Server/Oracle capability 声明与 collector 实现不完全一致。** 两个 adaptor 都声明 `METADATA` 与 `DATABASE_OBJECTS`，但对应 collector 当前返回空 snapshot/list；DDL collector 和 profiler 已实现。capability 应表达真实可用能力，或增加“placeholder/partial”状态，避免配置预检误判。
2. **`AdaptorCapability` 尚未成为 core/CLI preflight gate。** SPI 暴露 capability set，但生产扫描路径没有基于它拒绝不支持的 source/profile 配置；Phase 3 的 capability 验收仍未完全落地。

上述差异的优先级高于继续扩大数量统计。修复时应先增加具体 SQL/graph/provenance 测试，再调整实现；只有确认旧 golden 错误时才更新 golden。

## 后续技术债

- root token-event 虽已使用 typed structural grammar/visitor，但复杂 routine、业务查询和部分 DDL evidence coverage 仍弱于对应 full-grammer；后续应继续扩展 typed grammar/visitor，不能恢复 scanner、regex 或名字过滤。
- full-grammer profile 当前覆盖 MySQL 8.0、PostgreSQL 16/17/18 与 Oracle 12c/19c/21c/26ai；新增大版本需新增 adaptor module、严格 versioned fixture 和版本边界测试。
- 更广泛的 Oracle 官方语法覆盖仍需要补齐；当前 versioned sample-data golden 不能替代官方版本边界测试。
- SQL Server 已有独立 adaptor，不回退到 MySQL/PostgreSQL/Oracle parser；后续需要补更多 Microsoft 官方逐版本 T-SQL family 和 runtime smoke。
