# 测试资产地图与 Parser 验收矩阵

本文档是后续判断 SQL/DDL Token/Event parser 正确性的入口。结论不靠记忆，而看这里列出的测试资产、fixture golden、warning 保护和方言边界矩阵。

逐条 fixture 明细由代码生成，见 [Correctness Test Summary](generated/correctness-test-summary.md)。该报告是轻量索引：展示每个 SQL/DDL 的 preview、input 文件路径、expected relationship/data-lineage fingerprints、warning codes 和 forbidden tables；完整 SQL/DDL 以 fixture 的 `input.sql` 或 `input.ddl.sql` 为准。

Data Lineage 的全量审核入口见 [Data Lineage Full Audit](parser-audit/data-lineage-full-audit.md)。它同样由 Java 测试工具从 fixture 和 `TokenEventDataLineageExtractor` 输出生成，不调用大模型；用于回答“哪些 SQL 已经有 lineage golden、哪些可建议加入、哪些需要人工审核、哪些不适用 v1 字段血缘”。

历史 parser 迁移对比报告保留在 `docs/parser-audit/archive`，只作为追溯资料；当前验收只看 correctness fixture 的 `expected-relations.json` / `expected-lineage.json` 和本文件列出的测试矩阵。

Token/Event 当前状态：MySQL/PostgreSQL SQL relation、Data Lineage 和 DDL 均使用 Token/Event primary；公共 relation、rowset/scope、Data Lineage 写入映射、derived aggregate projection 回溯、显式临时表过滤、MySQL/PostgreSQL DML 深水区、方言 rowset 防伪表都有事件/抽取测试。

## 当前默认策略

- MySQL/PostgreSQL SQL parser 以方言 Token/Event parser 为唯一正确性基线。
- MySQL/PostgreSQL DDL parser 以方言 Token/Event DDL pipeline 为唯一正确性基线。
- `simple`、`antlr-shadow`、`simple-ddl`、`antlr-ddl-shadow` 以及对应 parser mode/fallback 配置已经移除；配置中出现旧 key 时应报错。
- SQL Server/Oracle 只保留接口和 future fixture，不在本轮 primary 切换范围内。

## 目录地图

| 路径 | 类型 | 作用 |
| --- | --- | --- |
| `relation-core/src/test/java/com/relationdetector/core` | Java 单元/集成测试 | core parser、runner、scoring、warning、metadata enhancer、diagnostics 行为 |
| `adaptor-mysql/src/test/java/com/relationdetector/mysql` | Java 单元/导出工具测试 | MySQL adaptor、DDL parser、metadata collector、真实库 fixture golden |
| `adaptor-postgres/src/test/java/com/relationdetector/postgres` | Java 单元/导出工具测试 | Postgres adaptor、DDL parser、真实库 fixture exporter |
| `relation-cli/src/test/java/com/relationdetector/cli` | Java CLI/config/fixture runner 测试 | YAML/CLI 配置、统一 correctness fixture runner |
| `test-fixtures/correctness/common` | 文件化 SQL correctness fixture | 跨方言基础 SQL 行为，例如 basic join、JOIN USING |
| `test-fixtures/correctness/mysql` | 文件化 MySQL correctness fixture | MySQL SQL/DDL/parser/warning golden |
| `test-fixtures/correctness/postgres` | 文件化 Postgres correctness fixture | Postgres SQL/DDL/parser/warning golden |
| `test-fixtures/mysql/basic-correctness` | 匿名真实 MySQL 样本 | 多个真实库导出的 DDL/SQL/golden，输入给 correctness fixture |
| `test-fixtures/postgres/basic-correctness` | 匿名真实 Postgres 样本 | `case-01` 真实 catalog DDL、对象 SQL、statement 样本和 golden |
| `test-fixtures/examples` | 示例配置和文件输入 | 面向用户/运维的 file-only 配置示例 |
| `test-fixtures/local` | 本地真实库扫描配置 | 手动导出/扫描用，本地密码通过环境变量注入 |
| `docs/generated/correctness-test-summary.md` | 生成的测试明细报告 | 由 `CorrectnessSummaryGeneratorTest` 从 fixture/golden 生成，轻量列出 SQL/DDL preview、结果、测试点和完整 input 文件路径 |
| `docs/parser-audit/data-lineage-full-audit.md` | Data Lineage 全量审核索引 | 由 `DataLineageAuditGeneratorTest` 从全部 correctness fixture 和 extractor 候选生成，按 `EXISTING_GOLD` / `SUGGESTED_GOLD` / `PENDING_REVIEW` / `NOT_APPLICABLE` 分类 |
| `docs/parser-audit/data-lineage-pending-review.md` | Data Lineage 审核清单 | 记录暂不进入 golden 的复杂字段血缘候选，例如多 transform 混合表达式、参数/JSON_TABLE 输入和窗口函数派生 |
| `docs/parser-audit/archive` | 历史 parser 迁移审计归档 | 保留 v2/shadow 切换期报告，当前测试不再更新或依赖这些文件 |

## Java 测试分类

| 测试类 | 主要分类 | 说明 |
| --- | --- | --- |
| `CorrectnessFixtureRunnerTest` | DDL primary / SQL primary / warning | 扫描 `test-fixtures/correctness`，按 manifest 执行 Token/Event parser 并比对 golden |
| `DataLineageAuditGeneratorTest` | Data Lineage / audit | 扫描全部 correctness fixture，生成 `docs/parser-audit/data-lineage-full-audit.md`，固定已有 lineage golden 数量、候选 fingerprints 和“不适用/待审核”原因；普通测试校验报告未漂移，只有显式 `-DupdateDataLineageAudit=true` 才刷新 |
| `ParserConfigRemovalTest` | 配置 / warning | YAML 和 CLI 拒绝已移除 parser mode/fallback 配置，同时验证 SQL log filter 配置解析 |
| `DdlRelationParserRunnerTest` | DDL primary / warning | DDL runner 只调用 structured DDL parser，空结果不被旧 parser 替换 |
| `SqlRelationParserRunnerTest` | SQL primary / warning | SQL runner 只调用 adaptor Token/Event parser，SQL log filter 仍生效 |
| `DdlRelationExtractionVisitorIndependenceTest` | DDL primary | DDL Token/Event 抽取不能委托旧 simple 实现 |
| `TokenEventArchitectureCleanupTest` | 架构 / 命名边界 | 防止生产代码、测试代码和主文档重新引入迁移期命名或旧 parser SPI |
| `TokenEventRelationEventsTest` | SQL primary / Token/Event relation + lineage | 验证 Token/Event 公共 relation、rowset/scope、DML 深水区和 Data Lineage：`JOIN USING`、raw equality、correlated `EXISTS`、scalar/tuple `IN`、列级弱共现、CTE/temp/trigger scope、MySQL multi-table `DELETE`、PostgreSQL `UPDATE FROM`、`UPDATE SET`、derived aggregate、`INSERT SELECT`、`MERGE` |
| `DialectSqlRelationParserComplexMatrixTest` | SQL primary / Token/Event | 复杂 JOIN/CTE/DML 方言场景直接跑 MySQL/PostgreSQL Token/Event parser；含 1 个 SQL Server future fixture skipped |
| `DialectParserEvidenceConfidenceTest` | SQL primary / Token/Event / confidence | evidence type/source type、joinKind、confidence 示例；验证 correlated EXISTS 输出 `SQL_LOG_EXISTS` 而不是普通 `SQL_LOG_JOIN` |
| `TokenEventSqlNoiseAndUsingTest` | SQL primary / Token/Event / noise filter | 系统 schema、截断 token、JOIN USING 防误报 |
| `SqlParserAdditionalSourceTypesTest` | SQL primary / Token/Event / warning diagnostics | view/procedure/trigger/function/rule/event/package/migration 等 source type 行为 |
| `ScanEngineDiagnosticsTest` | warning diagnostics | parse failure、raw SQL/DDL warning 保留 |
| `ScanEngineObjectWarningProvenanceTest` | warning diagnostics | routine/object warning provenance 字段 |
| `ScanEngineDatabaseDdlSourceTest` | DDL primary / metadata | 数据库内 DDL source 接入 ScanEngine |
| `MetadataEvidenceEnhancerTest` | metadata / confidence | TARGET_UNIQUE、SOURCE_INDEX、COLUMN_TYPE_COMPATIBLE 增强 |
| `RelationshipMergerEvidenceAggregationTest` | confidence | raw/aggregated evidence、递减增益和上限 |
| `ConfidenceScoringExamplesTest` | SQL primary / Token/Event / confidence | 置信度公式示例回归；SQL/DDL 解析型示例走 Token/Event，纯表级共现示例手工构造 evidence 测评分 |
| `JsonResultWriterEvidenceOutputTest` | output | 输出 raw/aggregated evidence 结构 |
| `TokenEventDataLineageExtractorTest` | Data Lineage / Token/Event | UPDATE SET、PostgreSQL UPDATE FROM、INSERT SELECT、MERGE、聚合 derived、CASE control、显式临时表过滤、字面量/函数跳过 |
| `MySqlMetadataCollectorFactsTest` | metadata | MySQL catalog facts：table/column/index/constraint |
| `MySqlDatabaseDdlCollectorTest` | DDL primary / metadata | MySQL `SHOW CREATE TABLE` 数据库内 DDL source |
| `MySqlDdlParserTest` | DDL primary | MySQL DDL parser 方言行为 |
| `MySqlTokenEventDialectBoundaryTest` | SQL primary / DDL primary / 方言边界 | MySQL adaptor parser selection/wiring 和 MySQL-only 语法隔离 |
| `MySqlTokenEventParserSelectionTest` | SQL primary / Token/Event / 方言边界 | MySQL production parser 使用 MySQL lexer/parser 和 `MySqlTokenEventSqlEventBuilder` |
| `PostgresDdlParserTest` | DDL primary | Postgres DDL parser 方言行为 |
| `PostgresTokenEventDialectBoundaryTest` | SQL primary / DDL primary / 方言边界 | Postgres adaptor parser selection/wiring 和 Postgres-only 语法隔离 |
| `PostgresTokenEventParserSelectionTest` | SQL primary / Token/Event / 方言边界 | PostgreSQL production parser 使用 PostgreSQL lexer/parser 和 `PostgresTokenEventSqlEventBuilder` |
| `PostgresBasicCorrectnessFixtureExporterTest` | real fixture / security | Postgres exporter case 命名、匿名化、secret 不落盘 |

## Correctness Fixture 分类

| Fixture 组 | 覆盖分类 | 说明 |
| --- | --- | --- |
| `common/sql-basic-join` | SQL primary | 基础 join 关系，使用 Token/Event gold |
| `common/sql-join-using` | SQL primary / noise filter | `JOIN ... USING (...)` 不生成列名伪 rowset；Token/Event 不再为裸 `USING` 自动保留表级噪声共现 |
| `mysql/basic-correctness-case-*-ddl` | DDL primary / real fixture | 真实 MySQL `SHOW CREATE TABLE` DDL，使用 Token/Event DDL gold |
| `mysql/basic-correctness-case-*-sql` | SQL primary / real fixture / noise filter | 真实 MySQL performance/general log 样本，使用 Token/Event SQL gold |
| `mysql/basic-correctness-case-01-procedures-sql` / `mysql/basic-correctness-case-01-functions-sql` | SQL primary / real fixture / database object | 真实 MySQL `information_schema.ROUTINES` procedure/function 样本，使用 `statementFormat: OBJECT_BLOCKS`，确保 routine body 不按分号拆碎 |
| `mysql/mysql-business-*-procedure-*-sql` | SQL primary / database object / MySQL procedure / Data Lineage pending | 用户提供的 2 个 MySQL procedure 业务样本及等价写法：JSON_TABLE 输入、嵌套 CTE、窗口函数、multi-table UPDATE、LEFT/RIGHT UNION 模拟 FULL OUTER、correlated subquery、comma rowset；验证 procedure body 使用 `OBJECT_BLOCKS` 保持完整，INNER JOIN 与 comma join 关系端点一致；参数 JSON、字面量和复杂多源 Data Lineage 暂列入 pending review，不作为 v1 golden |
| `mysql/ddl-create-table-fk-index` | DDL primary | MySQL FK、index、unique DDL 行为 |
| `mysql/mysql-official-*-sql` | SQL primary | MySQL 官方测试/文档启发的 JOIN、comma join、`STRAIGHT_JOIN`、NATURAL/ODBC/nested join、嵌套/递归 CTE、CTE+DML、derived/LATERAL、EXISTS/IN/ANY/SOME/ALL 场景，使用 Token/Event SQL gold |
| `mysql/mysql-official-*-ddl` | DDL primary | MySQL 官方测试/文档启发的 functional/multi-valued JSON index、FULLTEXT/SPATIAL、VISIBLE/INVISIBLE、prefix/generated/unique、index options、ALTER INDEX 场景，使用 Token/Event DDL gold |
| `mysql/sql-cte-lateral` | SQL primary | MySQL CTE/LATERAL/derived table 行为 |
| `mysql/sql-delete-left-join` | SQL primary | MySQL DELETE JOIN、LEFT_JOIN attributes |
| `mysql/mysql-invalid-orders-delete-*-sql` | SQL primary / MySQL DML | 无效订单清理覆盖 MySQL multi-table `DELETE o, oi FROM ... JOIN ...` 与逗号 join 对照；两种写法 fingerprints 一致；级联删除的业务影响方向不改变 FK-like 方向，仍输出 `order_items.order_id -> orders.id`、`orders.user_id -> users.id`，不把 delete target alias 当物理表 |
| `mysql/mysql-orphan-reviews-delete-*-sql` | SQL primary / MySQL DML / EXISTS | 孤儿评论清理覆盖 `LEFT JOIN ... IS NULL` 与业务等价 `NOT EXISTS`；非 INNER JOIN 不要求逗号 join 对照；`NOT EXISTS` 版本输出 `SQL_LOG_EXISTS`，不强造语义不等价的逗号 join |
| `mysql/sql-multi-table-update` | SQL primary / confidence | MySQL multi-table UPDATE JOIN；保留明确列级 FK-like，不额外补间接表级共现 |
| `mysql/mysql-commerce-promotion-update-*-join-sql` | SQL primary / MySQL DML / confidence | 同一电商促销 UPDATE 业务语义覆盖显式 `INNER JOIN` 与传统逗号 join；两种写法的 relationship fingerprints 必须一致；`shop_id`、`merchant_id` 输出 FK-like，`category_id` 同名非唯一匹配输出表级共现 |
| `mysql/mysql-user-spending-*-update-sql` | SQL primary / MySQL DML / Data Lineage | 用户消费汇总回写覆盖 `LEFT JOIN` derived aggregate 与 comma join 对照；`u.id = o_summary.user_id` 归一为 `orders.user_id -> users.id`，不输出派生表伪关系；`expected-lineage.json` 固化 `VALUE:AGGREGATE:orders.pay_amount->users.total_spent` 和 `CONTROL:CASE_WHEN:orders.pay_amount->users.level` |
| `mysql/mysql-supply-chain-update-*-sql` | SQL primary / MySQL DML / derived lineage | 复杂供应链 multi-table `UPDATE` 覆盖窗口函数 derived table、聚合 derived table、列级弱共现、`INNER JOIN` 与 comma/subquery 等价输出；`latest_orders`、`sm`、`ranking`、`avg_cost` 不得作为物理关系端点 |
| `mysql/sql-system-log-noise` | noise filter | MySQL information_schema/system query filter |
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
| `postgres/postgres-official-*-sql` | SQL primary | PostgreSQL 官方 regression/docs 启发的 outer/natural/USING alias/nested join、嵌套与递归 CTE、MATERIALIZED/NOT MATERIALIZED、EXISTS/tuple IN/ANY/SOME/ALL、LATERAL/ROWS FROM/function rowset 场景，使用 Token/Event SQL gold |
| `postgres/postgres-official-*-ddl` | DDL primary | PostgreSQL 官方 create_index/docs 启发的 CONCURRENTLY/ONLY/opclass/collation/NULLS、INCLUDE/partial/NULLS NOT DISTINCT、expression/access method/storage parameter、ALTER INDEX 边界，使用 Token/Event DDL gold |

## Primary 切换验收矩阵

| 验收问题 | 当前状态 | 判断依据 |
| --- | --- | --- |
| MySQL DDL 是否为 Token/Event primary | 是 | MySQL DDL fixture 覆盖真实 `SHOW CREATE TABLE` 和官方复杂 DDL，correctness runner 以 Token/Event DDL gold 验收 |
| Postgres DDL 是否为 Token/Event primary | 是 | `postgres-basic-correctness-case-01-ddl` 与官方复杂 DDL fixture 使用 Token/Event DDL gold |
| MySQL SQL 是否为 Token/Event primary | 是 | MySQL correctness fixture、noise filter、UPDATE/DELETE/JOIN 矩阵均以 Token/Event SQL gold 验收 |
| Postgres SQL 是否为 Token/Event primary | 是 | Postgres correctness fixture、真实库对象/statement 样本、三份复杂 SQL 样本均以 Token/Event SQL gold 验收 |
| SQL Server/Oracle SQL 是否可 primary | 不切 | 当前只保留接口和 future fixture，尚未建立独立 adaptor/golden 验收 |
| warning/diagnostics 链路是否有保护 | 有 | runner、diagnostics、object provenance、dynamic SQL warning、parser failure warning 均有测试 |
| metadata 增强是否有保护 | 有 | MySQL metadata facts、database DDL collector、metadata evidence enhancer 测试覆盖 |
| confidence 是否有保护 | 有 | confidence examples、evidence aggregation、dialect parser evidence/confidence 测试覆盖；correlated EXISTS 验证为 `SQL_LOG_EXISTS`，且同 endpoint pair 不应再重复产出普通 `SQL_LOG_JOIN` 造成虚高计分 |
| noise filter 是否有保护 | 有 | MySQL system log fixture、ANTLR USING/noise 单元测试覆盖 |

## 后续补强优先级

1. 补真实 SQL log 样本，优先来自应用日志、慢查询日志或有权限导出的 `pg_stat_statements.query`。
2. 为 Postgres 增加第二个真实 schema case，前提是可获得非空 SQL/DDL 样本。
3. 将新增 parser 行为优先沉淀到 `test-fixtures/correctness/<dialect>`，Java 单元测试只保留局部逻辑和故障注入。
4. 每次扩展 SQL/DDL fixture 后，更新 Token/Event golden，并人工审核关系 fingerprint、lineage fingerprint 或 warning code 的变化；Simple/ANTLR delta 不再作为当前验收材料。
