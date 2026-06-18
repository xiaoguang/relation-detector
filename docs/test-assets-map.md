# 测试资产地图与 Parser 验收矩阵

本文档是后续判断 “DDL/SQL parser 能否切 primary” 的入口。结论不靠记忆，而看这里列出的测试资产、fixture golden 和持续归零指标。

## 当前默认策略

- MySQL/PostgreSQL SQL parser 默认灰度为 `antlr-primary + fallbackOnFailure=true`。
- DDL parser 默认灰度为 `antlr-ddl-primary + fallbackOnFailure=true`。
- MySQL/PostgreSQL 的配置样例和运行默认值都应使用 SQL/DDL primary 灰度；`simple`、`antlr-shadow`、`simple-ddl` 和 `antlr-ddl-shadow` 保留在测试矩阵里，用于 baseline 对比和回归定位。
- SQL Server/Oracle 只保留接口和 future fixture，不在本轮 primary 切换范围内。

## 目录地图

| 路径 | 类型 | 作用 |
| --- | --- | --- |
| `relation-core/src/test/java/com/relationdetector/core` | Java 单元/集成测试 | core parser、runner、scoring、warning、metadata enhancer、fallback 行为 |
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

## Java 测试分类

| 测试类 | 主要分类 | 说明 |
| --- | --- | --- |
| `CorrectnessFixtureRunnerTest` | DDL primary / SQL primary / warning-fallback | 扫描 `test-fixtures/correctness`，按 manifest 执行 simple/shadow/primary 并比对 golden |
| `SqlParserModeConfigTest` | 配置 / warning-fallback | YAML 和 CLI parser mode、fallback、SQL log filter 配置解析 |
| `DdlRelationParserRunnerTest` | DDL primary / warning-fallback | DDL runner 模式切换、primary fallback warning |
| `SqlRelationParserRunnerTest` | SQL primary / warning-fallback | SQL runner 模式切换、ANTLR primary fallback |
| `AntlrDdlGoldenComparisonTest` | DDL primary | DDL simple baseline 与 ANTLR DDL comparison |
| `DdlRelationExtractionVisitorIndependenceTest` | DDL primary | DDL ANTLR 抽取不能委托 simple parser |
| `AntlrShadowGoldenComparisonTest` | SQL primary | SQL simple baseline 与 ANTLR shadow comparison |
| `ShadowSqlRelationParserParityTest` | SQL primary | shadow parity、missing/extra diagnostics |
| `RelationExtractionVisitorIndependenceTest` | SQL primary / 方言边界 | SQL ANTLR 抽取不能委托 simple parser；公共 visitor 不承接 MySQL-only 或 PostgreSQL-only rowset regex；公共层只保留 correlated EXISTS 这类跨方言关系语义，EXISTS 内部方言 rowset 仍由子类负责 |
| `MySqlAntlrParserSelectionTest` / `PostgresAntlrParserSelectionTest` | SQL primary / DDL primary / 方言边界 | SQL/DDL ANTLR parser、event visitor、relation visitor 选择；MySQL-only `STRAIGHT_JOIN`/ODBC `{ OJ ... }`/index hint/`PARTITION`/`JSON_TABLE`/multi-table DML 留在 MySQL visitor，Postgres 不继承；Postgres-only `ONLY`/`TABLESAMPLE`/`ROWS FROM`/`JOIN USING AS` 不污染 MySQL |
| `AntlrStructuredSqlParserTest` | SQL primary | ANTLR structured event 基础行为 |
| `DialectSqlRelationParserComplexMatrixTest` | SQL primary | 复杂 JOIN/CTE/DML 方言场景；含 1 个 SQL Server future fixture skipped |
| `SimpleSqlRelationParserComplexSqlTest` | SQL primary | simple parser 复杂 SQL baseline |
| `SimpleSqlRelationParserJoinSyntaxMatrixTest` | SQL primary | JOIN 写法矩阵：逗号 join、alias、quoted identifier 等 |
| `DialectParserEvidenceConfidenceTest` | SQL primary / confidence | evidence type/source type、joinKind、confidence 示例；验证 correlated EXISTS 输出 `SQL_LOG_EXISTS` 而不是普通 `SQL_LOG_JOIN` |
| `AntlrSqlNoiseAndUsingTest` | SQL primary / noise filter | SQL log 系统查询过滤、JOIN USING 防误报 |
| `SqlParserAdditionalSourceTypesTest` | SQL primary / warning-fallback | view/procedure/trigger/function 等 source type 行为 |
| `ScanEngineDiagnosticsTest` | warning-fallback | parse failure、raw SQL/DDL warning 保留 |
| `ScanEngineObjectWarningProvenanceTest` | warning-fallback | routine/object warning provenance 字段 |
| `ScanEngineDatabaseDdlSourceTest` | DDL primary / metadata | 数据库内 DDL source 接入 ScanEngine |
| `MetadataEvidenceEnhancerTest` | metadata / confidence | TARGET_UNIQUE、SOURCE_INDEX、COLUMN_TYPE_COMPATIBLE 增强 |
| `RelationshipMergerEvidenceAggregationTest` | confidence | raw/aggregated evidence、递减增益和上限 |
| `ConfidenceScoringExamplesTest` | confidence | 置信度公式示例回归 |
| `SimpleDdlParserMatrixTest` | DDL primary | simple DDL baseline 矩阵 |
| `JsonResultWriterEvidenceOutputTest` | output | 输出 raw/aggregated evidence 结构 |
| `MySqlBasicCorrectnessGoldenFixtureTest` | DDL primary / SQL primary / real fixture | MySQL 匿名真实库 golden comparison |
| `MySqlMetadataCollectorFactsTest` | metadata | MySQL catalog facts：table/column/index/constraint |
| `MySqlDatabaseDdlCollectorTest` | DDL primary / metadata | MySQL `SHOW CREATE TABLE` 数据库内 DDL source |
| `MySqlDdlParserTest` | DDL primary | MySQL DDL parser 方言行为 |
| `MySqlAntlrParserSelectionTest` | SQL primary / DDL primary | MySQL adaptor parser selection/wiring |
| `MySqlAntlrShadowZeroMissingTest` | SQL primary | MySQL SQL ANTLR zero-missing fixture |
| `PostgresDdlParserTest` | DDL primary | Postgres DDL parser 方言行为 |
| `PostgresAntlrParserSelectionTest` | SQL primary / DDL primary | Postgres adaptor parser selection/wiring |
| `PostgresAntlrShadowZeroMissingTest` | SQL primary | Postgres SQL ANTLR zero-missing fixture |
| `PostgresBasicCorrectnessFixtureExporterTest` | real fixture / security | Postgres exporter case 命名、匿名化、secret 不落盘 |

## Correctness Fixture 分类

| Fixture 组 | 覆盖分类 | 说明 |
| --- | --- | --- |
| `common/sql-basic-join` | SQL primary | 基础 join 关系，跑 simple/shadow/primary |
| `common/sql-join-using` | SQL primary / noise filter | `JOIN ... USING (...)` 不生成列名伪 rowset，保留表级共现 |
| `mysql/basic-correctness-case-*-ddl` | DDL primary / real fixture | 真实 MySQL `SHOW CREATE TABLE` DDL，三模式对比 |
| `mysql/basic-correctness-case-*-sql` | SQL primary / real fixture / noise filter | 真实 MySQL performance/general log 样本，三模式对比 |
| `mysql/ddl-create-table-fk-index` | DDL primary | MySQL FK、index、unique DDL 行为 |
| `mysql/mysql-official-*-sql` | SQL primary | MySQL 官方测试/文档启发的 JOIN、comma join、`STRAIGHT_JOIN`、NATURAL/ODBC/nested join、嵌套/递归 CTE、CTE+DML、derived/LATERAL、EXISTS/IN/ANY/SOME/ALL 场景，跑 simple/shadow/primary |
| `mysql/mysql-official-*-ddl` | DDL primary | MySQL 官方测试/文档启发的 functional/multi-valued JSON index、FULLTEXT/SPATIAL、VISIBLE/INVISIBLE、prefix/generated/unique、index options、ALTER INDEX 场景，跑 simple-ddl/shadow/primary |
| `mysql/sql-cte-lateral` | SQL primary | MySQL CTE/LATERAL/derived table 行为 |
| `mysql/sql-delete-left-join` | SQL primary | MySQL DELETE JOIN、LEFT_JOIN attributes |
| `mysql/sql-multi-table-update` | SQL primary | MySQL multi-table UPDATE JOIN |
| `mysql/sql-system-log-noise` | noise filter | MySQL information_schema/system query filter |
| `postgres/ddl-alter-table-fk` | DDL primary | `ALTER TABLE ... FOREIGN KEY ... NOT VALID` |
| `postgres/ddl-partial-index-boundary` | DDL primary / metadata | partial index 不作为全局唯一证据 |
| `postgres/ddl-unique-include-index` | DDL primary | `CREATE UNIQUE INDEX ... INCLUDE` |
| `postgres/postgres-basic-correctness-case-01-ddl` | DDL primary / real fixture | 真实 Postgres catalog DDL，186 张表，516 条关系 |
| `postgres/postgres-basic-correctness-case-01-objects-sql` | SQL primary / warning-fallback / real fixture | 真实 object SQL；当前主要验证 `DYNAMIC_SQL_UNRESOLVED` warning |
| `postgres/postgres-basic-correctness-case-01-statements-sql` | SQL primary / real fixture | `pg_stat_statements` 样本入口；当前无可用样本 |
| `postgres/sql-delete-using-no-alias` | SQL primary | Postgres DELETE USING，无 alias 情况 |
| `postgres/sql-lateral-derived` | SQL primary | LATERAL/derived table 伪表过滤 |
| `postgres/sql-merge-using` | SQL primary | Postgres MERGE USING source-target |
| `postgres/sql-multi-layer-cte` | SQL primary | 多层 CTE lineage |
| `postgres/sql-quoted-mixed-alias` | SQL primary | quoted identifier、mixed alias |
| `postgres/sql-recursive-cte` | SQL primary | recursive CTE 边界 |
| `postgres/sql-unnest-ordinality` | SQL primary | UNNEST WITH ORDINALITY rowset 边界 |
| `postgres/sql-update-from-aliases` | SQL primary | UPDATE FROM alias/no alias |
| `postgres/postgres-official-*-sql` | SQL primary | PostgreSQL 官方 regression/docs 启发的 outer/natural/USING alias/nested join、嵌套与递归 CTE、MATERIALIZED/NOT MATERIALIZED、EXISTS/tuple IN/ANY/SOME/ALL、LATERAL/ROWS FROM/function rowset 场景，跑 simple/shadow/primary |
| `postgres/postgres-official-*-ddl` | DDL primary | PostgreSQL 官方 create_index/docs 启发的 CONCURRENTLY/ONLY/opclass/collation/NULLS、INCLUDE/partial/NULLS NOT DISTINCT、expression/access method/storage parameter、ALTER INDEX 边界，跑 simple-ddl/shadow/primary |

## Primary 切换验收矩阵

| 验收问题 | 当前状态 | 判断依据 |
| --- | --- | --- |
| MySQL DDL 是否可灰度 primary | 可以 | MySQL DDL fixture 覆盖真实 `SHOW CREATE TABLE`，`simple-ddl/antlr-ddl-shadow/antlr-ddl-primary` 在 correctness runner 中持续通过 |
| Postgres DDL 是否可灰度 primary | 可以 | `postgres-basic-correctness-case-01-ddl`：186 张表，516 条关系，ANTLR DDL primary 与 simple DDL 一致，无 fallback warning |
| MySQL SQL 是否可灰度 primary | 可以，需 fallback | MySQL correctness fixture、ANTLR zero-missing、noise filter、UPDATE/DELETE/JOIN 矩阵均覆盖 `antlr-primary + fallbackOnFailure=true` |
| Postgres SQL 是否可灰度 primary | 可以，需 fallback | Postgres correctness fixture、真实库对象/statement 样本、三份复杂 SQL 样本均覆盖 `antlr-primary + fallbackOnFailure=true` |
| SQL Server/Oracle SQL 是否可 primary | 不切 | 当前只保留接口和 future fixture，尚未建立独立 adaptor/golden 验收 |
| warning/fallback 链路是否有保护 | 有 | runner、diagnostics、object provenance、dynamic SQL warning、parser failure warning 均有测试 |
| metadata 增强是否有保护 | 有 | MySQL metadata facts、database DDL collector、metadata evidence enhancer 测试覆盖 |
| confidence 是否有保护 | 有 | confidence examples、evidence aggregation、dialect parser evidence/confidence 测试覆盖；correlated EXISTS 验证为 `SQL_LOG_EXISTS`，且同 endpoint pair 不应再重复产出普通 `SQL_LOG_JOIN` 造成虚高计分 |
| noise filter 是否有保护 | 有 | MySQL system log fixture、ANTLR USING/noise 单元测试覆盖 |

## 后续补强优先级

1. 补真实 SQL log 样本，优先来自应用日志、慢查询日志或有权限导出的 `pg_stat_statements.query`。
2. 为 Postgres 增加第二个真实 schema case，前提是可获得非空 SQL/DDL 样本。
3. 将新增 parser 行为优先沉淀到 `test-fixtures/correctness/<dialect>`，Java 单元测试只保留局部逻辑和故障注入。
4. 每次扩展 SQL fixture 后，检查 MySQL/PostgreSQL SQL fixture 的 `missingSimpleRelations=[]`，并人工审核 `extraAntlrRelations`，以决定是否继续维持 `antlr-primary + fallbackOnFailure=true` 灰度。
