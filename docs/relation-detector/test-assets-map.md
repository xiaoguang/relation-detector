# 测试资产地图与 Parser 验收矩阵

本文档是后续判断 SQL/DDL token-event parser 正确性的入口。结论不靠记忆，而看这里列出的测试资产、fixture golden、warning 保护和方言边界矩阵。

逐条 fixture 明细由代码生成，见 [Correctness Test Summary](../generated/correctness-test-summary.md)。该报告是轻量索引：展示每个 SQL/DDL 的 preview、input 文件路径、expected relationship/data-lineage fingerprints、warning codes 和 forbidden tables；完整 SQL/DDL 以 fixture 的 `input.sql` 或 `input.ddl.sql` 为准。

Data Lineage 的全量审核入口见 [Data Lineage Full Audit](../parser-audit/data-lineage-full-audit.md)。它同样由 Java 测试工具从 fixture 和 `StructuredDataLineageExtractor` 输出生成，不调用大模型；用于回答“哪些 SQL 已经有 lineage golden、哪些可建议加入、哪些需要人工审核、哪些不适用 v1 字段血缘”。

full-grammer expression transform 的审核记录见 [full-grammer Expression Transform Compatibility Audit](../parser-audit/full-grammer-expression-transform-compatibility-audit.md)。它记录严格表达式分析曾暴露出的 transform 差异，例如疑似过粗的 `AGGREGATE` 判断和 `CASE_WHEN` 的 value/control flow 差异；本轮已按人工审核结论固化到 lineage golden。后续不再用跨 parser 对照机制遮蔽差异，每个 parser 必须直接通过自己的 golden。

MySQL 5.7 full-grammer 的迁移分类、5.7-compatible sample-data 改写和版本边界负向 fixture 见 [MySQL 5.7 Full-Grammer Migration Review](../parser-audit/mysql57-migration-review.md)。

SQL Server adaptor 的语法来源、sample-data correctness 和 MySQL 8.0 语义迁移状态见 [SQL Server Adaptor And MySQL 8.0 Migration Review](../parser-audit/sqlserver-migration-review.md)。当前 SQL Server 已有 root token-event 与 `sqlserver/2016|2017|2019|2022|2025` full-grammer sample-data golden；每组覆盖 38 个 ERP sample-data 文件。首批官方逐版本 T-SQL 边界见 [SQL Server Version Grammar Difference Audit](../parser-audit/sqlserver-version-grammar-diff.md)：2017 `STRING_AGG`、2022 `DATETRUNC` / `GENERATE_SERIES`、2025 `VECTOR(...)` 已在 `.g4` 和测试中收口；更广泛的 T-SQL family 硬化仍是 backlog。

历史 parser 迁移对比报告保留在 `docs/parser-audit/archive`，只作为追溯资料；当前验收只看 correctness fixture 的 `expected-relations.json` / `expected-lineage.json` 和本文件列出的测试矩阵。

token-event 当前状态：Common portable SQL 已通过 `database.type: common` 成为正式 CLI parser category，可运行 `relation-detector/sample-data/portable`；MySQL/PostgreSQL/Oracle/SQL Server SQL relation、Data Lineage 和 DDL 均使用 token-event 作为无 profile 或 unsupported version 时的正式 fallback；Oracle 使用 `relation-detector/adaptor-oracle` 自己的 `OracleRelationSql.g4` 与 typed visitor，SQL Server 使用 `relation-detector/adaptor-sqlserver` 自己的 compact `SqlServerRelationSql.g4` 与 typed visitor，二者都不复用 common parser 的装配入口。公共 relation、rowset/scope、Data Lineage 写入映射、derived aggregate projection 回溯、显式临时表过滤、MySQL/PostgreSQL/Oracle/SQL Server DML 深水区、方言 rowset 防伪表都有事件/抽取测试。profile 已选中后的 full-grammer parse warning / partial result 不会委托 token-event 补事件。

版本化 full-grammer 当前状态：MySQL 当前 module 为 `mysql-5.7`、`mysql-8.0`，并已有独立 `mysql/v5_7`、`mysql/v8_0` version golden；PostgreSQL 当前 module 为 `postgresql-16`、`postgresql-17`、`postgresql-18`，每个 major version 都有独立 package、`.g4`、parser support、typed visitor、DDL collector 和 version golden。PostgreSQL 的 generated lexer/parser 类名带版本前缀（`Postgres16FullGrammerLexer/Parser`、`Postgres17FullGrammerLexer/Parser`、`Postgres18FullGrammerLexer/Parser`），这是为了隔离 ANTLR `.tokens` 生成物，防止多个版本同名 grammar 在同一 adaptor module 下互相覆盖。Oracle 当前 module 为 `oracle-12c`、`oracle-19c`、`oracle-21c`、`oracle-26ai`：四个 profile 各自使用本版本 split lexer/parser grammar 和 typed visitor，不再桥接 Oracle token-event；但 Oracle full-grammer 当前状态是 `INCOMPLETE_VERSIONED`，只证明 scoped generated parser、首批版本边界和 Oracle sample-data fixture 真实接线，尚不是官方 SQL/PLSQL Reference 的完整转换。SQL Server 当前 module 为 `sqlserver-2016`、`sqlserver-2017`、`sqlserver-2019`、`sqlserver-2022`、`sqlserver-2025`：五个 profile 各自使用本版本 generated lexer/parser，并覆盖完整 SQL Server sample-data correctness；当前 sample-data 使用 2016-compatible T-SQL 子集，首批 Microsoft 官方逐版本语法裁剪已编码为 grammar-level 边界。官方 PostgreSQL `gram.y` / `scan.l` / keywords 是 source-of-truth，仓库中的 ANTLR `.g4` 是固定 grammars-v4 基础上的 checked-in projection，并按 major version 做边界约束；MySQL 5.7 / 8.0 grammar 来自固定 grammars-v4 MySQL 快照，5.7 版本边界以 MySQL 5.7 官方文档收紧；Oracle `.g4` 目前是受控的文档派生 projection，已编码 19c/21c/26ai 首批官方版本边界；SQL Server full-grammer 来自固定 grammars-v4 T-SQL 快照，已编码 2017/2022/2025 首批官方版本边界，后续 family 继续以 Microsoft Learn T-SQL reference 为准。具体实现归属 adaptor：MySQL 在 `relation-detector/adaptor-mysql` 的 `com.relationdetector.mysql.fullgrammer.v5_7` / `v8_0`，PostgreSQL 在 `relation-detector/adaptor-postgres` 的 `com.relationdetector.postgres.fullgrammer.v16` / `v17` / `v18`，Oracle 在 `relation-detector/adaptor-oracle` 的 `com.relationdetector.oracle.fullgrammer.v12c` / `v19c` / `v21c` / `v26ai`，SQL Server 在 `relation-detector/adaptor-sqlserver` 的 `com.relationdetector.sqlserver.fullgrammer.v2016` / `v2017` / `v2019` / `v2022` / `v2025`；profile bridge 由 package 表达。profile 选择由 core 的 `FullGrammerDialectModule` registry 注入，人工配置 `parser.grammarProfile` 优先，之后可使用 `parser.databaseVersion` 或 JDBC metadata；同一 major 的 minor 默认复用该 major profile，最多只允许高 1 个 major 的临时降级，完全没有方言/版本信息时回退 token-event。SQL/DDL full-grammer parser 可作为 `parser.mode=auto|full-grammer` 的正式运行选择；versioned correctness fixture 要求匹配版本 full-grammer 成功。PostgreSQL 低版本遇到高版本专属语法会返回 `FULL_GRAMMAR_VERSION_UNSUPPORTED_SYNTAX`；Oracle 和 SQL Server 当前低版本遇到已编码的高版本专属语法会在 grammar 层产生 syntax error，token-event 才承担宽松向前兼容。

PostgreSQL versioned correctness 的真实目录只有 `postgres/v16`、`postgres/v17`、`postgres/v18`。测试命令、文档和验收描述必须显式列出这些目录，不使用 `postgres/v1` 这类聚合前缀；即使 fixture filter 技术上可以按字符串前缀匹配多个目录，也不能把它写成维护者可依赖的版本标识。

命名边界：`parser.mode` 是系统运行模式，只允许 `auto|full-grammer|token-event`；MySQL `SQL_MODE` 是 MySQL full-grammer runtime 的语法开关，由 `MySqlGrammarSqlMode` / `MySqlGrammarSqlModes` 表达；ANTLR lexer mode 是 `.g4` 内部词法状态。测试和文档不要把这三类 mode 混用。adaptor 根包只保留 database adaptor 装配类；MySQL/PostgreSQL/Oracle/SQL Server token-event parser 分别放在 `mysql.tokenevent` / `postgres.tokenevent` / `oracle.tokenevent` / `sqlserver.tokenevent` 子包。

## 当前 Golden 统计

统计口径：Relationship / Lineage 分别按 `expected-relations.json` / `expected-lineage.json` 的 `fingerprints` 数量计算；`Rel NAMING_MATCH` 是 relationship evidence 中引用的命名证据数量；`Top-level namingEvidence` 来自 `expected-naming-evidence.json`，表示独立命名证据池。

| Golden 组 | Fixture | SQL / DDL | Relationship fingerprints | Lineage fingerprints | Diagnostics | Rel NAMING_MATCH | Top-level namingEvidence |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 全部 correctness | 1194 | 981 / 213 | 17973 | 6301 | 25 | 6833 | 8085 |
| common token-event | 39 | 34 / 5 | 759 | 328 | 0 | 219 | 441 |
| MySQL root token-event | 83 | 65 / 18 | 659 | 349 | 0 | 252 | 321 |
| MySQL full-grammer v5_7 | 89 | 71 / 18 | 706 | 414 | 9 | 257 | 327 |
| MySQL full-grammer v8_0 | 89 | 71 / 18 | 923 | 398 | 6 | 421 | 491 |
| PostgreSQL root token-event | 111 | 92 / 19 | 1402 | 332 | 0 | 353 | 353 |
| PostgreSQL full-grammer v16 | 111 | 92 / 19 | 1474 | 351 | 10 | 374 | 447 |
| PostgreSQL full-grammer v17 | 113 | 94 / 19 | 1478 | 364 | 0 | 375 | 448 |
| PostgreSQL full-grammer v18 | 114 | 93 / 21 | 1477 | 362 | 0 | 374 | 447 |
| Oracle root token-event | 41 | 33 / 8 | 643 | 247 | 0 | 241 | 241 |
| Oracle full-grammer v12c | 42 | 34 / 8 | 681 | 249 | 0 | 273 | 341 |
| Oracle full-grammer v19c | 43 | 35 / 8 | 681 | 249 | 0 | 273 | 341 |
| Oracle full-grammer v21c | 43 | 35 / 8 | 681 | 249 | 0 | 273 | 341 |
| Oracle full-grammer v26ai | 43 | 35 / 8 | 681 | 249 | 0 | 273 | 341 |
| SQL Server root token-event | 38 | 32 / 6 | 703 | 360 | 0 | 275 | 275 |
| SQL Server full-grammer v2016 | 39 | 33 / 6 | 1005 | 360 | 0 | 520 | 586 |
| SQL Server full-grammer v2017 | 39 | 33 / 6 | 1005 | 360 | 0 | 520 | 586 |
| SQL Server full-grammer v2019 | 39 | 33 / 6 | 1005 | 360 | 0 | 520 | 586 |
| SQL Server full-grammer v2022 | 39 | 33 / 6 | 1005 | 360 | 0 | 520 | 586 |
| SQL Server full-grammer v2025 | 39 | 33 / 6 | 1005 | 360 | 0 | 520 | 586 |

## 当前默认策略

- correctness、CLI E2E 和手工 SQL 分析前，先运行 `mvn clean -pl relation-detector/cli -am -DskipTests test-compile` 与 `relation-detector/scripts/check-no-jls-bad-classes.sh`。后者会拒绝 VS Code Java Language Server / Eclipse 编译器写入的 `Unresolved compilation problems` 占位 class，以及未真实实现 `DatabaseAdaptor` SPI 的坏 adaptor class。
- 手工 CLI 使用 `relation-detector/scripts/run-cli.sh ...`。该脚本先生成 Maven main jar，再执行坏 class 检查并组装运行 classpath；不要直接拼接未检查的 `*/target/classes`。
- MySQL/PostgreSQL/Oracle/SQL Server root correctness fixture 显式以方言 token-event 输出作为 baseline gold；versioned fixture 显式以 full-grammer 输出作为对应版本 gold。full-grammer 不再通过 token-event baseline 做跨 parser 保护，漏识别必须在自己的 versioned golden 中直接暴露。
- MySQL/PostgreSQL/Oracle/SQL Server root DDL correctness fixture 显式以方言 token-event DDL pipeline 作为 baseline gold；versioned DDL fixture 显式以 full-grammer DDL 输出作为对应版本 gold。full-grammer DDL 同样只由 versioned golden 验收，不再用 token-event DDL baseline 兜底。
- `simple`、`antlr-shadow`、`simple-ddl`、`antlr-ddl-shadow` 以及对应 parser mode/fallback 配置已经移除；配置中出现旧 key 时应报错。
- SQL Server 已有 adaptor、root token-event sample-data golden 和五个 versioned full-grammer sample-data golden；sample-data 已收敛为自然 ERP 业务 SQL；高密度关系探针迁入 semantic-equivalent benchmark。首批 Microsoft 官方逐版本语法边界已用 version-only fixture 和 grammar-level rejection 验收；JDBC collector、更多 T-SQL family 边界和 runtime smoke 仍在 backlog。Oracle 已进入 adaptor、root token-event golden 和四个 versioned sample-data full-grammer golden，但 Oracle official complete full-grammer visitor 仍在 backlog。

## 目录地图

| 路径 | 类型 | 作用 |
| --- | --- | --- |
| `relation-detector/core/src/test/java/com/relationdetector/core` | Java 单元/集成测试 | core parser、runner、scoring、warning、metadata enhancer、diagnostics 行为 |
| `relation-detector/adaptor-mysql/src/test/java/com/relationdetector/mysql` | Java 单元/导出工具测试 | MySQL adaptor、DDL parser、metadata collector、真实库 fixture golden |
| `relation-detector/adaptor-postgres/src/test/java/com/relationdetector/postgres` | Java 单元/导出工具测试 | Postgres adaptor、DDL parser、真实库 fixture exporter |
| `relation-detector/adaptor-oracle/src/test/java/com/relationdetector/oracle` | Java 单元测试 | Oracle adaptor、Oracle token-event grammar、`INCOMPLETE_VERSIONED` full-grammer generated parser wiring、profile attributes、Oracle SQL asset hygiene |
| `relation-detector/adaptor-sqlserver/src/test/java/com/relationdetector/sqlserver` | Java 单元测试 | SQL Server adaptor、token-event grammar、versioned full-grammer generated parser wiring、SQL asset hygiene |
| `relation-detector/cli/src/test/java/com/relationdetector/cli` | Java CLI/config/fixture runner 测试 | YAML/CLI 配置、统一 correctness fixture runner |
| `relation-detector/test-fixtures/correctness/common` | 文件化 SQL correctness fixture | 跨方言基础 SQL 行为，例如 basic join、JOIN USING |
| `relation-detector/test-fixtures/correctness/mysql` | 文件化 MySQL correctness fixture | MySQL SQL/DDL/parser/warning golden |
| `relation-detector/test-fixtures/correctness/mysql/v5_7` | 文件化 MySQL 5.7 correctness fixture | MySQL 5.7 strict full-grammer version golden，manifest 强制 `mysql/5.7` full-grammer |
| `relation-detector/test-fixtures/correctness/mysql/v8_0` | 文件化 MySQL 8.0 correctness fixture | MySQL 8.0 strict full-grammer version golden，manifest 强制 `mysql/8.0` full-grammer |
| `relation-detector/test-fixtures/correctness/postgres` | 文件化 Postgres correctness fixture | Postgres SQL/DDL/parser/warning golden |
| `relation-detector/test-fixtures/correctness/postgres/v16` | 文件化 Postgres 16 correctness fixture | 根 PostgreSQL fixture 的 16.x strict version golden，manifest 强制 `postgresql/16` full-grammer |
| `relation-detector/test-fixtures/correctness/postgres/v17` | 文件化 Postgres 17 correctness fixture | 根 PostgreSQL fixture 的 17.x version golden，加 PostgreSQL 17 SQL/JSON 与 MERGE 专属语法 |
| `relation-detector/test-fixtures/correctness/postgres/v18` | 文件化 Postgres 18 correctness fixture | 根 PostgreSQL fixture 的 18.x version golden，加 PostgreSQL 18 `old/new` RETURNING、virtual generated column、temporal constraint 语法 |
| `relation-detector/test-fixtures/correctness/oracle` | 文件化 Oracle correctness fixture | Oracle root token-event baseline，来自 Oracle sample-data 的有效输出 / 版本边界切片 |
| `relation-detector/test-fixtures/correctness/oracle/v12c` | 文件化 Oracle 12c correctness fixture | Oracle 12c sample-data full-grammer version golden，manifest 强制 `oracle/12c` |
| `relation-detector/test-fixtures/correctness/oracle/v19c` | 文件化 Oracle 19c correctness fixture | Oracle 19c sample-data full-grammer version golden，manifest 强制 `oracle/19c` |
| `relation-detector/test-fixtures/correctness/oracle/v21c` | 文件化 Oracle 21c correctness fixture | Oracle 21c sample-data full-grammer version golden，manifest 强制 `oracle/21c` |
| `relation-detector/test-fixtures/correctness/oracle/v26ai` | 文件化 Oracle 26ai correctness fixture | Oracle 26ai sample-data full-grammer version golden，manifest 强制 `oracle/26ai` |
| `relation-detector/test-fixtures/correctness/sqlserver` | 文件化 SQL Server correctness fixture | SQL Server root token-event baseline，覆盖 `sample-data/sqlserver/2025` 的 38 个 ERP SQL 文件 |
| `relation-detector/test-fixtures/correctness/sqlserver/v2016` | 文件化 SQL Server 2016 correctness fixture | SQL Server 2016 sample-data full-grammer version golden，manifest 强制 `sqlserver/2016` |
| `relation-detector/test-fixtures/correctness/sqlserver/v2017` | 文件化 SQL Server 2017 correctness fixture | SQL Server 2017 sample-data full-grammer version golden，manifest 强制 `sqlserver/2017` |
| `relation-detector/test-fixtures/correctness/sqlserver/v2019` | 文件化 SQL Server 2019 correctness fixture | SQL Server 2019 sample-data full-grammer version golden，manifest 强制 `sqlserver/2019` |
| `relation-detector/test-fixtures/correctness/sqlserver/v2022` | 文件化 SQL Server 2022 correctness fixture | SQL Server 2022 sample-data full-grammer version golden，manifest 强制 `sqlserver/2022` |
| `relation-detector/test-fixtures/correctness/sqlserver/v2025` | 文件化 SQL Server 2025 correctness fixture | SQL Server 2025 sample-data full-grammer version golden，manifest 强制 `sqlserver/2025` |
| `semantic-layer/semantic-core/src/test/java/com/relationdetector/semantic` | Java 语义层测试 | scan-result reader、evidence graph、KG JSON 构建、架构边界 |
| `semantic-layer/semantic-cli/src/test/java/com/relationdetector/semantic/cli` | Java 语义 CLI 测试 | `semantic build` 离线输入/输出链路，确保不触发 parser/adaptor |
| `docs/parser-audit/postgres-version-golden-diff.md` | PostgreSQL version golden 差异审核 | 以 v18 为最新基准，分类较低版本缺失的 relation/evidence/lineage |
| `relation-detector/test-fixtures/mysql/basic-correctness` | 匿名真实 MySQL 样本 | 多个真实库导出的 DDL/SQL/golden，输入给 correctness fixture |
| `relation-detector/test-fixtures/postgres/basic-correctness` | 匿名真实 Postgres 样本 | `case-01` 真实 catalog DDL、对象 SQL、statement 样本和 golden |
| `relation-detector/test-fixtures/examples` | 示例配置和文件输入 | 面向用户/运维的 file-only 配置示例 |
| `relation-detector/test-fixtures/local` | 本地真实库扫描配置 | 手动导出/扫描用，本地密码通过环境变量注入 |
| `docs/generated/correctness-test-summary.md` | 生成的测试明细报告 | 由 `CorrectnessSummaryGeneratorTest` 从 fixture/golden 生成，轻量列出 SQL/DDL preview、结果、测试点和完整 input 文件路径 |
| `docs/parser-audit/data-lineage-full-audit.md` | Data Lineage 全量审核索引 | 由 `DataLineageAuditGeneratorTest` 从全部 correctness fixture 和 extractor 候选生成，按 `EXISTING_GOLD` / `SUGGESTED_GOLD` / `PENDING_REVIEW` / `NOT_APPLICABLE` 分类 |
| `docs/parser-audit/data-lineage-pending-review.md` | Data Lineage 审核清单 | 记录暂不进入 golden 的复杂字段血缘候选，例如多 transform 混合表达式、参数/JSON_TABLE 输入和窗口函数派生 |
| `docs/parser-audit/archive` | 历史 parser 迁移审计归档 | 保留 v2/shadow 切换期报告，当前测试不再更新或依赖这些文件 |

## Java 测试分类

| 测试类 | 主要分类 | 说明 |
| --- | --- | --- |
| `CorrectnessFixtureRunnerTest` | DDL / SQL / warning / naming evidence | 扫描 `relation-detector/test-fixtures/correctness`，通过 `FixtureInputLoader`、`FixtureExecutionEngine`、`GoldenAssertion`、`GoldenWriter` 四层执行；SQL/DDL actual 输出复用 production `StatementExecutionService`，并比对 `expected-relations.json`、`expected-lineage.json`、`expected-diagnostics.json`、`expected-naming-evidence.json`；默认 `mvn test` 只跑 `smoke` profile，合并前传 `-DcorrectnessFixtureProfile=full -DcorrectnessFixtureParallelism=8` 跑全量 |
| `CorrectnessNamingEvidenceGoldenTest` | naming evidence golden | 验证 relationship 中的 `NAMING_MATCH.evidenceRef` 能在 top-level `namingEvidence.id` 中找到，并防止 relationship 本地重新发明命名证据 |
| `CliEndToEndGoldenTest` | CLI / end-to-end / golden | JVM 内调用 CLI，从 Jackson YAML 配置和 CLI 参数进入 adaptor、ScanEngine、parser、merger、Jackson JSON writer，并复用现有 fixture golden 比对 relationship / Data Lineage / naming evidence |
| `CorrectnessSummaryGeneratorTest` | generated report | 生成 `docs/generated/correctness-test-summary.md`；默认跳过，显式 `-DrunGeneratedReportTests=true` 才验收，显式 `-DupdateCorrectnessSummary=true` 才刷新 |
| `DataLineageAuditGeneratorTest` | Data Lineage / audit | 扫描全部 correctness fixture，生成 `docs/parser-audit/data-lineage-full-audit.md`；默认跳过，显式 `-DrunGeneratedReportTests=true` 才验收，显式 `-DupdateDataLineageAudit=true` 才刷新 |
| `DialectSqlAssetHygieneTest` | SQL 资产卫生 / 方言边界 | 扫描 `relation-detector/sample-data/mysql|postgres|oracle|sqlserver` 和对应 correctness SQL，阻止明显跨方言残留进入测试资产；每个方言使用独立 forbidden pattern |
| `ParserConfigRemovalTest` | 配置 / warning | Jackson YAML loader 与 CLI 拒绝已移除 parser mode/fallback 配置，同时验证 SQL log filter、paths/include、环境变量和 parser 配置解析 |
| `DdlRelationParserRunnerTest` | DDL primary / warning | DDL runner 只调用 selected structured DDL parser，空结果不被旧 parser 替换 |
| `SqlRelationParserRunnerTest` | SQL parser mode / warning | SQL runner 按 `parser.mode` 选择 full-grammer 或 token-event fallback，SQL log filter 仍生效 |
| `DdlRelationExtractionVisitorIndependenceTest` | DDL primary | DDL token-event 抽取不能委托旧 simple 实现 |
| `CommonTokenEventStructuredSqlParserTest` / `TokenEventStructuredSqlParserTest` | common token-event | 验证 common portable typed grammar、rowset/predicate/write mapping 和 DDL/SQL 结构事件 |
| `ProjectionTraceResolverTest` | Data Lineage / projection trace | 验证 CTE、derived table、projection alias、表达式来源和字段写入映射都通过结构化 `ProjectionTrace`，不使用 SQL 文本 regex/token span fallback |
| `VisitorThreadSafetyTest` | parser state / 并发稳定性 | 并行解析同一批 SQL，验证 visitor/collector per-parse state 不泄漏 |
| `NamingEvidenceExtractorTest` / `NamingMatchEvidenceEnhancerTest` / `ParserConfigRemovalTest` | naming evidence | 验证 top-level naming evidence 池、relationship `evidenceRef` 引用、“不从 relationship 里本地重算 NAMING_MATCH”、系统默认规则 YAML、客户 `USER_CONFIGURED` ruleFiles / inline rules、重复 id 和非法 `TRANSITIVE_NAMING_PATH` 配置拒绝 |
| `DialectParserEvidenceConfidenceTest` | SQL primary / token-event / confidence | evidence type/source type、confidence 示例；验证 JOIN / correlated EXISTS / IN 子查询分别保留 `SQL_LOG_JOIN`、`SQL_LOG_EXISTS`、`SQL_LOG_SUBQUERY_IN`，且 SQL evidence 需要结合 unique/metadata/profile 或 `NAMING_MATCH` 方向提示才决定 FK-like 方向 |
| `SqlParserAdditionalSourceTypesTest` | SQL primary / token-event / warning diagnostics | view/procedure/trigger/function/rule/event/package/migration 等 source type 行为 |
| `ScanEngineDiagnosticsTest` | warning diagnostics | parse failure、raw SQL/DDL warning 保留 |
| `ScanEngineObjectWarningProvenanceTest` | warning diagnostics | routine/object warning provenance 字段 |
| `ScanEngineDatabaseDdlSourceTest` | DDL primary / metadata | 数据库内 DDL source 接入 ScanEngine |
| `MetadataEvidenceEnhancerTest` | metadata / confidence | TARGET_UNIQUE、SOURCE_INDEX、COLUMN_TYPE_COMPATIBLE 增强 |
| `RelationshipMergerEvidenceAggregationTest` | confidence | raw/aggregated evidence、递减增益和上限 |
| `ConfidenceScoringExamplesTest` | SQL primary / token-event / confidence | 置信度公式示例回归；SQL/DDL 解析型示例走 token-event，纯表级共现示例手工构造 evidence 测评分 |
| `JsonResultWriterEvidenceOutputTest` | output | Jackson JSON 输出 raw/aggregated evidence 结构、top-level `namingEvidence` 和 `includeEvidence=false` 行为 |
| `MySqlMetadataCollectorFactsTest` | metadata | MySQL catalog facts：table/column/index/constraint |
| `MySqlDatabaseDdlCollectorTest` | DDL primary / metadata | MySQL `SHOW CREATE TABLE` 数据库内 DDL source |
| `MySqlDdlParserTest` | DDL primary | MySQL DDL parser 方言行为 |
| `MySqlTokenEventDialectBoundaryTest` | SQL primary / DDL primary / 方言边界 | MySQL adaptor parser selection/wiring 和 MySQL-only 语法隔离 |
| `MySqlTokenEventParserSelectionTest` | SQL primary / token-event / 方言边界 | MySQL production parser 使用 MySQL lexer/parser 和 `MySqlTokenEventParseTreeVisitor` |
| `MySqlTokenEventProcedureRelationBehaviorTest` | MySQL routine / token-event | MySQL procedure body 中的 relationship / lineage 行为 |
| `MySql57FullGrammerVersionBoundaryTest` / `MySqlFullGrammerGeneratedParserSmokeTest` / `MySqlFullGrammerExpressionAnalyzerTest` | MySQL full-grammer | MySQL 5.7/8.0 generated parser、版本边界和 expression analyzer 行为 |
| `PostgresDdlParserTest` | DDL primary | Postgres DDL parser 方言行为 |
| `PostgresTokenEventDialectBoundaryTest` | SQL primary / DDL primary / 方言边界 | Postgres adaptor parser selection/wiring 和 Postgres-only 语法隔离 |
| `PostgresTokenEventParserSelectionTest` | SQL primary / token-event / 方言边界 | PostgreSQL production parser 使用 PostgreSQL lexer/parser 和 `PostgresTokenEventParseTreeVisitor` |
| `PostgresFullGrammerVersionBoundaryTest` / `PostgresFullGrammerGeneratedParserSmokeTest` / `PostgresFullGrammerExpressionAnalyzerTest` | PostgreSQL full-grammer | PostgreSQL 16/17/18 generated parser、版本边界和 expression analyzer 行为 |
| `OracleAdaptorParserTest` / `OracleParserArchitectureTest` / `OracleDatabaseDdlCollectorTest` / `OracleRoutineScopeTest` | Oracle adaptor | Oracle token-event/full-grammer 独立性、ServiceLoader、DDL collector 和 routine scope policy |
| `SqlServerTokenEventParserTest` / `SqlServerParserArchitectureTest` / `SqlServerDatabaseDdlCollectorTest` | SQL Server adaptor | SQL Server token-event/full-grammer 独立性、T-SQL parser smoke 和 live DDL collector |
| `PostgresBasicCorrectnessFixtureExporterTest` | real fixture / security | Postgres exporter case 命名、匿名化、secret 不落盘 |

## Correctness Fixture 分类

| Fixture 组 | 覆盖分类 | 说明 |
| --- | --- | --- |
| `common/sql-basic-join` | SQL primary | 基础 join 关系，使用 token-event gold |
| `common/sql-join-using` | SQL primary / noise filter | `JOIN ... USING (...)` 不生成列名伪 rowset；token-event 不再为裸 `USING` 自动保留表级噪声共现 |
| `mysql/basic-correctness-case-*-ddl` | DDL primary / real fixture | 真实 MySQL `SHOW CREATE TABLE` DDL，使用 token-event DDL gold |
| `mysql/basic-correctness-case-*-sql` | SQL primary / real fixture / noise filter | 真实 MySQL performance/general log 样本，使用 token-event SQL gold |
| `mysql/basic-correctness-case-01-procedures-sql` / `mysql/basic-correctness-case-01-functions-sql` | SQL primary / real fixture / database object | 真实 MySQL `information_schema.ROUTINES` procedure/function 样本，使用 `statementFormat: OBJECT_BLOCKS`，确保 routine body 不按分号拆碎 |
| `mysql/mysql-business-*-procedure-*-sql` | SQL primary / database object / MySQL procedure / Data Lineage pending | 用户提供的 2 个 MySQL procedure 业务样本及等价写法：JSON_TABLE 输入、嵌套 CTE、窗口函数、multi-table UPDATE、LEFT/RIGHT UNION 模拟 FULL OUTER、correlated subquery、comma rowset；验证 procedure body 使用 `OBJECT_BLOCKS` 保持完整，INNER JOIN 与 comma join 关系端点一致；参数 JSON、字面量和复杂多源 Data Lineage 暂列入 pending review，不作为 v1 golden |
| `mysql/ddl-create-table-fk-index` | DDL primary | MySQL FK、index、unique DDL 行为 |
| `mysql/mysql-official-*-sql` | SQL primary | MySQL 官方测试/文档启发的 JOIN、comma join、`STRAIGHT_JOIN`、NATURAL/ODBC/nested join、嵌套/递归 CTE、CTE+DML、derived/LATERAL、EXISTS/IN/ANY/SOME/ALL 场景，使用 token-event SQL gold |
| `mysql/mysql-official-*-ddl` | DDL primary | MySQL 官方测试/文档启发的 functional/multi-valued JSON index、FULLTEXT/SPATIAL、VISIBLE/INVISIBLE、prefix/generated/unique、index options、ALTER INDEX 场景，使用 token-event DDL gold |
| `mysql/sql-cte-lateral` | SQL primary | MySQL CTE/LATERAL/derived table 行为 |
| `mysql/sql-delete-left-join` | SQL primary | MySQL DELETE JOIN、LEFT_JOIN attributes |
| `mysql/mysql-invalid-orders-delete-*-sql` | SQL primary / MySQL DML | 无效订单清理覆盖 MySQL multi-table `DELETE o, oi FROM ... JOIN ...` 与逗号 join 对照；两种写法 fingerprints 一致；级联删除的业务影响方向不改变 FK-like 方向，仍输出 `order_items.order_id -> orders.id`、`orders.user_id -> users.id`，不把 delete target alias 当物理表 |
| `mysql/mysql-orphan-reviews-delete-*-sql` | SQL primary / MySQL DML / EXISTS | 孤儿评论清理覆盖 `LEFT JOIN ... IS NULL` 与业务等价 `NOT EXISTS`；非 INNER JOIN 不要求逗号 join 对照；`NOT EXISTS` 版本输出 `SQL_LOG_EXISTS`，不强造语义不等价的逗号 join |
| `mysql/sql-multi-table-update` | SQL primary / confidence | MySQL multi-table UPDATE JOIN；保留明确列级 FK-like，不额外补间接表级共现 |
| `mysql/mysql-commerce-promotion-update-*-join-sql` | SQL primary / MySQL DML / confidence | 同一电商促销 UPDATE 业务语义覆盖显式 `INNER JOIN` 与传统逗号 join；两种写法的 relationship fingerprints 必须一致；`shop_id`、`merchant_id` 输出 FK-like，`category_id` 同名非唯一匹配输出表级共现 |
| `mysql/mysql-user-spending-*-update-sql` | SQL primary / MySQL DML / Data Lineage | 用户消费汇总回写覆盖 `LEFT JOIN` derived aggregate 与 comma join 对照；`u.id = o_summary.user_id` 归一为 `orders.user_id -> users.id`，不输出派生表伪关系；`expected-lineage.json` 固化 `VALUE:AGGREGATE:orders.pay_amount->users.total_spent` 和 `CONTROL:CASE_WHEN:orders.pay_amount->users.level` |
| `mysql/mysql-supply-chain-update-*-sql` | SQL primary / MySQL DML / derived lineage | 复杂供应链 multi-table `UPDATE` 覆盖窗口函数 derived table、聚合 derived table、列级弱共现、`INNER JOIN` 与 comma/subquery 等价输出；`latest_orders`、`sm`、`ranking`、`avg_cost` 不得作为物理关系端点 |
| `mysql/sql-system-log-noise` | noise filter | MySQL information_schema/system query filter |
| `mysql/v5_7/*` | SQL/DDL versioned full-grammer | 89 个 MySQL 5.7 strict version golden；manifest 强制 `parserMode: full-grammer`、`grammarProfile: mysql/5.7`；包含 MySQL 8.0 fixture 的 5.7 原样兼容、语义等价改写和 8.0-only 版本边界负向用例 |
| `mysql/v8_0/*` | SQL/DDL versioned full-grammer | 89 个 MySQL 8.0 strict version golden；manifest 强制 `parserMode: full-grammer`、`grammarProfile: mysql/8.0`；相对 root MySQL token-event 的差异记录在 `docs/parser-audit/all-golden-semantic-review.md` |
| `postgres/ddl-alter-table-fk` | DDL primary | `ALTER TABLE ... FOREIGN KEY ... NOT VALID` |
| `postgres/ddl-partial-index-boundary` | DDL primary / metadata | partial index 不作为全局唯一证据 |
| `postgres/ddl-unique-include-index` | DDL primary | `CREATE UNIQUE INDEX ... INCLUDE` |
| `postgres/postgres-basic-correctness-case-01-ddl` | DDL primary / real fixture | 真实 Postgres catalog DDL，186 张表，516 条关系 |
| `postgres/postgres-basic-correctness-case-01-objects-sql` | SQL primary / warning diagnostics / real fixture | 真实 object SQL；当前主要验证 `DYNAMIC_SQL_UNRESOLVED` warning |
| `postgres/postgres-basic-correctness-case-01-statements-sql` | SQL primary / real fixture | `pg_stat_statements` 样本入口；当前无可用样本 |
| `postgres/sql-delete-using-no-alias` | SQL primary | Postgres DELETE USING，无 alias 情况 |
| `postgres/sql-lateral-derived` | SQL primary | LATERAL/derived table 伪表过滤 |
| `postgres/sql-merge-using` | SQL primary | Postgres MERGE USING source-target |
| `postgres/sql-multi-layer-cte` | SQL primary | 多层 CTE lineage |
| `postgres/sql-quoted-mixed-alias` | SQL primary | quoted identifier、mixed alias |
| `postgres/sql-recursive-cte` | SQL primary | recursive CTE 边界 |
| `postgres/sql-unnest-ordinality` | SQL primary | UNNEST WITH ORDINALITY rowset 边界 |
| `postgres/sql-update-from-aliases` | SQL primary | UPDATE FROM alias/no alias |
| `postgres/postgres-business-*-sql` | SQL primary / Data Lineage | 用户提供的 11 个 PostgreSQL 业务 SQL 及等价写法：UPDATE FROM、DELETE USING、嵌套 CTE、aggregate derived table、data-modifying CTE DELETE RETURNING、orphan cleanup、inventory/warehouse/ledger/financial 更新与删除；验证 INNER JOIN 与 comma join fingerprint 一致、非 INNER 使用等价子查询、Postgres DML 列级弱共现、CTE/derived/COALESCE 首列 lineage；部分 UPDATE fixture 已有 `expected-lineage.json` |
| `postgres/postgres-business-*-function-sql` | SQL primary / database object | 用户提供的 PostgreSQL function 样本：PL/pgSQL body 通过 `statementFormat: OBJECT_BLOCKS` 保持完整；覆盖 array `UNNEST`、临时表、嵌套 CTE、FULL/LEFT JOIN、correlated scalar subquery 和 comma rowset 等价写法 |
| `postgres/postgres-official-*-sql` | SQL primary | PostgreSQL 官方 regression/docs 启发的 outer/natural/USING alias/nested join、嵌套与递归 CTE、MATERIALIZED/NOT MATERIALIZED、EXISTS/tuple IN/ANY/SOME/ALL、LATERAL/ROWS FROM/function rowset 场景，使用 token-event SQL gold |
| `postgres/postgres-official-*-ddl` | DDL primary | PostgreSQL 官方 create_index/docs 启发的 CONCURRENTLY/ONLY/opclass/collation/NULLS、INCLUDE/partial/NULLS NOT DISTINCT、expression/access method/storage parameter、ALTER INDEX 边界，使用 token-event DDL gold |
| `postgres/v16/*` | SQL/DDL versioned full-grammer | 100 个 PostgreSQL 16.x strict version golden；manifest 强制 `parserMode: full-grammer`、`grammarProfile: postgresql/16`；PG17/18-only SQL 使用版本边界 fixture 表达 unsupported diagnostic |
| `postgres/v17/*` | SQL/DDL versioned full-grammer | 102 个 PostgreSQL 17.x version golden，并新增 `JSON_TABLE()`、SQL/JSON、`MERGE ... WHEN NOT MATCHED BY SOURCE`、`MERGE RETURNING merge_action()` 专属语法 fixture；manifest 强制 `parserMode: full-grammer`、`grammarProfile: postgresql/17` |
| `postgres/v18/*` | SQL/DDL versioned full-grammer | 103 个 PostgreSQL 18.x version golden，并新增 `RETURNING old/new`、virtual generated column、`WITHOUT OVERLAPS` / `PERIOD` FK 专属语法 fixture；manifest 强制 `parserMode: full-grammer`、`grammarProfile: postgresql/18` |

## Primary 切换验收矩阵

| 验收问题 | 当前状态 | 判断依据 |
| --- | --- | --- |
| MySQL DDL fallback gold 是否完整 | 是 | MySQL DDL fixture 覆盖真实 `SHOW CREATE TABLE` 和官方复杂 DDL；root token-event DDL gold 只验收 token-event 自身 |
| Postgres DDL fallback gold 是否完整 | 是 | `postgres-basic-correctness-case-01-ddl` 与官方复杂 DDL fixture 使用 token-event DDL gold；versioned full-grammer DDL gold 单独验收对应版本 |
| MySQL SQL fallback gold 是否完整 | 是 | MySQL correctness fixture、noise filter、UPDATE/DELETE/JOIN 矩阵均有 token-event SQL gold；MySQL 5.7 / 8.0 full-grammer 分别由 `mysql/v5_7`、`mysql/v8_0` 独立 golden 验收 |
| Postgres SQL fallback gold 是否完整 | 是 | Postgres correctness fixture、真实库对象/statement 样本、复杂 SQL 样本均有 token-event SQL gold；PostgreSQL full-grammer 由 `postgres/v16|v17|v18` 独立 golden 验收 |
| Oracle SQL 是否可 primary | token-event 可用；full-grammer `INCOMPLETE_VERSIONED` | 已有 Oracle adaptor、Oracle token-event root golden 和 `oracle/12c|19c|21c|26ai` sample-data versioned full-grammer golden；当前 full-grammer 不再桥接 token-event，更广泛的 Oracle 官方语法覆盖是后续补强 |
| SQL Server SQL 是否可 primary | sample-data 覆盖 + 首批 version boundary | 已有 SQL Server adaptor、root token-event baseline 和 `sqlserver/2016|2017|2019|2022|2025` versioned full-grammer sample-data golden；当前 sample-data 为 2016-compatible 保守 T-SQL 子集，2017/2022/2025 首批官方语法边界已在 `.g4` 和 version-only fixture 中补强 |
| warning/diagnostics 链路是否有保护 | 有 | runner、diagnostics、object provenance、dynamic SQL warning、parser failure warning 均有测试 |
| metadata 增强是否有保护 | 有 | MySQL metadata facts、database DDL collector、metadata evidence enhancer 测试覆盖 |
| confidence 是否有保护 | 有 | confidence examples、evidence aggregation、dialect parser evidence/confidence 测试覆盖；SQL-only predicate 保留 `SQL_LOG_JOIN` / `SQL_LOG_EXISTS` / `SQL_LOG_SUBQUERY_IN` 等具体 evidence，方向由 DDL/metadata/data-profile、unique-vs-nonunique evidence 或 relationship 引用的 top-level `namingEvidence` 方向提示提供 |
| noise filter 是否有保护 | 有 | MySQL system log fixture、token-event USING/noise 单元测试覆盖 |
| full-grammer 是否覆盖 SQL fixture | 是 | `CorrectnessFixtureRunnerTest` 直接扫描 `mysql/v5_7`、`mysql/v8_0`、`postgres/v16`、`postgres/v17`、`postgres/v18`；full-grammer 漏识别会在自身 versioned golden 中失败 |
| full-grammer 是否覆盖 DDL fixture | 是 | `CorrectnessFixtureRunnerTest` 直接扫描 versioned DDL fixture；不再使用 token-event DDL baseline 做 cross-parser 保护 |
| full-grammer 是否覆盖 MySQL 5.7 version fixture | 是 | `CorrectnessFixtureRunnerTest` 扫描 `mysql/v5_7`，manifest 强制 `parserMode: full-grammer` 和 `grammarProfile: mysql/5.7`，比对独立 version golden |
| full-grammer 是否覆盖 MySQL 8.0 version fixture | 是 | `CorrectnessFixtureRunnerTest` 扫描 `mysql/v8_0`，manifest 强制 `parserMode: full-grammer` 和 `grammarProfile: mysql/8.0`，比对独立 version golden |
| full-grammer 是否覆盖 PostgreSQL 16/17/18 version fixture | 是 | `CorrectnessFixtureRunnerTest` 扫描 `postgres/v16`、`postgres/v17`、`postgres/v18`，manifest 强制 `parserMode: full-grammer` 和对应 `grammarProfile`，比对独立 version golden |
| full-grammer 是否覆盖 Oracle 12c/19c/21c/26ai version fixture | sample-data 覆盖 | `CorrectnessFixtureRunnerTest` 扫描 `oracle/v12c`、`oracle/v19c`、`oracle/v21c`、`oracle/v26ai`，manifest 强制 `parserMode: full-grammer` 和对应 `grammarProfile`；当前结果证明每个 profile 使用本版本 generated parser 覆盖 sample-data，不证明官方严格语法边界 |

## 可选手工结构检查

构建卫生检查：

```bash
mvn clean -pl relation-detector/cli -am -DskipTests test-compile
relation-detector/scripts/check-no-jls-bad-classes.sh
```

默认 `mvn test` 不再运行目录/命名/迁移过程守卫测试。需要人工检查代码结构残留时，使用：

```bash
rg -n "TokenEventV2|shadow runner|current ANTLR|SimpleSqlRelationParser|SimpleDdlParser|fullgrammar|full grammar|FullGrammar" \
  relation-detector/contracts relation-detector/core relation-detector/cli relation-detector/adaptor-mysql relation-detector/adaptor-postgres docs/design docs/relation-detector/code-implementation-guide.md docs/relation-detector/test-assets-map.md
```

这类检查只辅助 code review，不作为 SQL/DDL correctness 验收入口。

## 后续补强优先级

1. 补真实 SQL log 样本，优先来自应用日志、慢查询日志或有权限导出的 `pg_stat_statements.query`。
2. 为 Postgres 增加第二个真实 schema case，前提是可获得非空 SQL/DDL 样本。
3. 将新增 parser 行为优先沉淀到 `relation-detector/test-fixtures/correctness/<dialect>`，Java 单元测试只保留局部逻辑和故障注入。
4. 每次扩展 SQL/DDL fixture 后，更新当前 parser golden，并人工审核关系 fingerprint、lineage fingerprint 或 warning code 的变化；legacy Simple/ANTLR delta 不再作为当前验收材料。
