# Phase 3：Adaptor API 和 SPI 详细设计

## 目标

定义可扩展数据库 adaptor API，并通过 Java SPI 支持内置 adaptor 和外部 jar adaptor。当前内置 MySQL、PostgreSQL、Oracle 与 SQL Server adaptor；后续补强某个方言的大版本 grammar、metadata collector 或 profiling 能力时，不需要修改 core 和 CLI 的主要逻辑。

## 总体原则

- adaptor API 独立放在 `contracts`。
- core 只依赖 adaptor API，不依赖具体 adaptor。
- adaptor 可以参与全链路扩展，但默认流程由 core 编排。
- core 统一做候选关系归并、最终评分和输出。
- adaptor 可以提供 evidence 权重修正，但不能绕过输出 evidence 的要求。
- core 提供统一 runner、关系归并和评分；SQL/DDL 文本解析由对应数据库 adaptor 暴露的 token-event parser 或版本化 full-grammar module 承担，ANTLR 只作为底层 lexer/parser/token 支撑。明显属于某个数据库方言的 DDL、日志、对象定义差异应进入对应 adaptor。这样 MySQL、PostgreSQL、Oracle 和 SQL Server 可以独立演进，而不把所有语法分支堆进 core。

## DatabaseAdaptor 接口

当前公开契约是 **adaptor SPI v6**。`DatabaseAdaptor` 只暴露 grouped
capability；metadata、object、DDL、log、parser 和 profiling 的旧 getter 已从接口删除，
不再保留双接口兼容桥。这是一次明确的二进制 SPI 升级：外部 adaptor 必须基于
当前 `contracts` 重新编译。

```java
public interface DatabaseAdaptor {
  default int spiVersion() { return 1; }
  String id();
  String displayName();
  Set<DatabaseType> supportedDatabaseTypes();
  Set<AdaptorCapability> capabilities();
  IdentifierRules identifierRules();

  default ScanScope canonicalizeScope(ScanScope scope) { return scope; }
  default ScanScope resolveLiveScope(Connection connection, ScanScope scope) { return scope; }
  default Set<Integer> permissionDeniedVendorCodes() { return Set.of(); }

  AdaptorCollectors collectors();
  AdaptorParsers parsers();
  AdaptorProfiling profiling();
}
```

说明：

- `id()` 例如 `mysql`、`postgresql`。
- `supportedDatabaseTypes()` 用于匹配 YAML 中的 `database.type`。
- `capabilities()` 声明该 adaptor 支持哪些来源和功能。
- `canonicalizeScope()` 在 capability preflight 之后、打开 JDBC 连接之前将外部
  `ScanScope` 转成方言内部的 namespace 语义。默认不改动；MySQL 用它把
  database 统一放到 catalog 轴。该方法只做配置级规范化，不能用未打开的
  connection 猜测 live catalog。
- `resolveLiveScope()` 在 JDBC 连接建立后、任何 metadata、object、database-DDL 或
  profiling 查询之前由 `ScanEngine` 在生产 scan 边界调用一次。它用当前 connection
  证明并返回真正可执行的 catalog/schema；默认不改动，需要 current-database/owner
  校验或 fallback 的内置 adaptor 会覆盖。collector 的直接 SPI 入口仍可复用同一 resolver
  做幂等防御，但不得建立竞争的 namespace 契约。无法证明显式 catalog/owner 时抛出
  `LiveSourceConfigurationException`，不得把当前连接的结果重标为另一个 namespace。
- `permissionDeniedVendorCodes()` 是二进制兼容的方言诊断策略，默认空集合。共享层只识别 JDBC
  异常类型和 SQLState；Oracle 1031、SQL Server 229/916 等 vendor code 由拥有它们的 adaptor
  明确返回，不能全局应用到其他方言。
- `spiVersion()` 是二进制 API 版本。内置 adaptor 显式返回
  `AdaptorApiVersion.CURRENT`（当前为 6）；为了让旧二进制类可被加载并获得可读错误，
  接口 default 返回 1。
- `collectors()` 聚合 metadata、object definition、database DDL 和 SQL log extractor。
- `parsers()` 聚合 SQL relationship parser、structured SQL parser、structured DDL parser 和必需的 dialect script framer。
- `profiling()` 聚合 data profiler 和 evidence weight adjuster。
- core/CLI 只能通过这三个 grouped capability 访问 adaptor。
- `AdaptorRegistry` 在使用任何 capability 前检查 SPI 版本。不匹配时错误包含
  plugin id、actual、required 和“请重新编译”提示，避免运行到中途才出现
  `NoSuchMethodError`。
- `capabilities()` 是可执行契约。`ScanCapabilityValidator` 已在打开 JDBC 前检查实际请求的 source、
  capability、collector/profiler 以及下游 parser；live database DDL 同时要求 structured DDL parser，
  live database objects 同时要求 structured SQL parser。纯文件 scan 不会因 live metadata 默认值被拒绝，
  custom adaptor 缺少任一生产者或消费者时也会在 JDBC 前失败。
- capability preflight failure 统一使用 `AdaptorContractException`，在 single-scan 映射为
  `ADAPTOR_ERROR`；batch case 保留同一 code，batch 整体仍返回 `BATCH_PARTIAL_FAILURE`。catalog/source
  等用户配置错误继续使用 `CONFIG_FORMAT_ERROR`，两类边界不会经通用异常混合。
- `ScanConfigurationValidator` 在 `ScanConfig.resolve()`、`ResolvedScanConfig` 构造和
  `ScanEngine.scan()` 三个边界执行统一规则。metadata、database-DDL、database-object 或 profiling
  任一 live 功能启用但缺少 JDBC URL 时会在 capability 检查及连接前失败；`ScanCapabilityValidator`
  只负责 capability、producer 和 consumer 契约，不再用 JDBC URL 猜测是否请求 live 功能。
- preflight只能证明请求有可调用实现，不能证明collector返回的是完整parser-grade declaration、所有
  namespace fallback一致或真实driver权限组合已经验证。live completeness必须由各adaptor设计和
  runtime/高保真contract test单独声明。

## 采集器接口

### MetadataCollector

```java
public interface MetadataCollector {
  MetadataSnapshot collect(Connection connection, ScanScope scope);
}
```

输出：

- tables。
- columns。
- primary keys。
- foreign keys。
- unique constraints。
- indexes。

### ObjectDefinitionCollector

```java
public interface ObjectDefinitionCollector {
  List<DatabaseObjectDefinition> collect(Connection connection, ScanScope scope);

  default List<DatabaseObjectDefinition> collect(
      Connection connection,
      ScanScope scope,
      Consumer<WarningMessage> warnings) {
    return collect(connection, scope);
  }
}
```

对象类型：

- procedure。
- function。
- view。
- materialized view。
- trigger。
- MySQL event。
- PostgreSQL rule。
- Oracle package / package body。

具体 adaptor 只返回数据库真实支持且当前 collector 已实现的子集。当前 Oracle live collector 包含 procedure、function、package、package body、view、materialized view 和 trigger；SQL Server live collector 包含 T-SQL procedure、function、view 和 trigger。PostgreSQL 返回 function/procedure、view/materialized-view query、rule definition 和 non-internal trigger definition；trigger function 作为独立 function definition 采集，不通过名称猜测额外 binding。

如果数据库账号权限不足，返回 warning，不终止扫描。

实现建议：

- 新 adaptor 应优先覆盖带 `warnings` 的重载。
- 如果 routine/view/trigger 中某一类对象读取失败，应记录对应 warning code，而不是吞掉异常。
- 已经读到的对象定义仍应返回，保持部分成功。

### SPI 返回值信任边界

`DataProfiler` 以及下列已接入契约验证器的 adaptor SPI 结果按不可信输入处理：

- `AdaptorResultContractValidator` 对 metadata、object、database-DDL 和 fallback
  relationship parser 的结果先做 detached copy，再验证 inventory、endpoint、evidence
  family/source、definition identity 和 warning envelope。
- `SourceCollectorPipeline` 只在整个对应 outcome 通过后写入 scan。null list、null element
  以及 null/blank SQL/DDL body 继续按 recoverable `DEFINITION_UNAVAILABLE` 处理；其余违约
  统一抛 `AdaptorContractException`，不会留下部分 fact、candidate 或 warning。
- snapshot/callback warning 的 plugin message、source 和 line 不被信任；core 只保留受限的
  SQLState、vendorCode、exceptionClass 和对象身份属性，并由 `LiveDiagnosticSanitizer`
  按 operation 重建固定消息与 source。

其余 parser-facing SPI 由 `AdaptorParseResultContractValidator` 闭环：
`SqlLogExtractor` 的 stream 在提交前完整 materialize，`DialectScriptFramer`、
`StructuredSqlParser` 与 `StructuredDdlParser` 的 statement/event/provenance/attributes/warnings
先 deep-detach，再执行类型、来源和 warning allowlist 校验。任一元素违约都会使该次 outcome
整体失败，前序 statement、event 和 warning 均不会进入 scan。

### SqlLogExtractor

```java
public interface SqlLogExtractor {
  Stream<SqlStatementRecord> extract(Path file, LogFormatHint hint);

  default Stream<SqlStatementRecord> extract(
      Path file,
      LogFormatHint hint,
      Consumer<WarningMessage> warnings) {
    return extract(file, hint);
  }
}
```

支持：

- 数据库原生日志。
- 清洗后的纯 SQL 文本。

实现建议：

- 文件级读取失败记录 `LOG_EXTRACT_FAILED`。
- plain SQL、DDL file、object file 和 native-log 记录都先由 `parsers().scriptFramer().frame(...)` 做方言 client-script framing，再把产生的 server statement 交给 structured SQL/DDL parser；`DDL_FILE` 不得绕过这一步直接按文本整体提交给 `DdlRelationParserRunner`。文件读取失败仍记录 `SQL_FILE_EXTRACT_FAILED`。
- 对于原生日志，最好保留每条 SQL 的 `sourceName`、`startLine`、`endLine`，以便后续 SQL parser 抛异常时能把原始语句写入 warning。
- native log 的系统 catalog 噪声不能在原始 SQL 文本阶段靠关键词或正则删除。`TypedLogNoiseClassifier` 只在 structured parser 已输出 typed physical rowset event 后分类；当没有 physical rowset 时，才允许使用显式 metadata marker 作为运营输入过滤条件。

### AdaptorContext 诊断通道

```java
public record AdaptorContext(
    ScanScope scope,
    Map<String, Object> options,
    Consumer<WarningMessage> warningSink) {
  public void warn(WarningMessage warning) { ... }
}
```

用途：

- DDL parser、SQL parser、adaptor 私有预处理器可以在局部失败时调用 `context.warn(...)`。
- warning 最终进入 `ScanResult.warnings`，并由 JSON/table writer 输出。
- `rawStatement` 应放在 `warning.attributes.rawStatement`，不要只拼在 message 字符串里。

## 解析器接口

### StructuredDdlParser

DDL 不再通过旧 `DdlParser` SPI 暴露。adaptor 通过
`parsers().structuredDdl()` 提供 token-event DDL parser，并可通过
`FullGrammarDialectModule` 注册版本化 full-grammar DDL parser。core 的
`DdlRelationParserRunner` 负责读取 DDL 文本、按 `parser.mode` 选择结构化 parser、
再用 `DdlRelationExtractionVisitor` 生成统一 `RelationshipCandidate`。

方言拆分规则：

- MySQL/PostgreSQL/Oracle/SQL Server DDL 均走 token-event DDL pipeline。MySQL 专属写法，例如反引号标识符、`KEY`/`INDEX` 选项、prefix index、invisible index、storage engine/table options、`SHOW CREATE TABLE` 格式，应在 `MySqlRelationSql.g4` / `MySqlTokenEventParseTreeVisitor` 或 `mysql.tokenevent.MySqlTokenEventStructuredDdlParser` 中处理。
- PostgreSQL 专属写法，例如 `ALTER TABLE ONLY`、`NOT VALID`、`CREATE INDEX CONCURRENTLY/IF NOT EXISTS`、`INCLUDE`、partial/expression index、opclass、partition/inheritance，应在 `PostgresRelationSql.g4` / `PostgresTokenEventParseTreeVisitor` 或 `postgres.tokenevent.PostgresTokenEventStructuredDdlParser` 中处理。
- Oracle 专属写法，例如 `VARCHAR2` / `NUMBER` / `CLOB` / `XMLTYPE`、`COMMENT ON`、PL/SQL object DDL 和 Oracle identity/sequence 写法，应在 `OracleRelationSql.g4` / `OracleTokenEventParseTreeVisitor` 或 `oracle.tokenevent.OracleTokenEventStructuredDdlParser` 中处理。
- SQL Server 专属写法，例如 bracket identifier、`IDENTITY`、`CREATE OR ALTER`、schema-qualified `[dbo].[table]`、`WITH (...)` table/index options，应在 `SqlServerRelationSql.g4` / `SqlServerTokenEventParseTreeVisitor` 或 `sqlserver.tokenevent.SqlServerTokenEventStructuredDdlParser` 中处理。
- adaptor parser 的输出仍必须是统一的 `RelationshipCandidate` 和 `Evidence`，不能绕过 core 的合并与置信度计算。
- 方言 parser 应有自己的单元测试，并包含正向和反向负向用例。这样可以证明某个数据库的语法增强不会悄悄改变其他数据库的解析行为。

### SqlRelationParser

```java
public interface SqlRelationParser {
  List<RelationshipCandidate> parse(SqlStatementRecord statement, AdaptorContext context);
}
```

职责：

- JOIN 关系。
- `IN` 子查询关系。
- `EXISTS` 关系。
- 表共现关系。
- 解析失败 warning。

当前实现边界：

- `ScanEngine` 会包装每次 `SqlRelationParser.parse(...)` 调用；如果 parser 抛异常，会生成 `SQL_PARSE_FAILED`，并把原始 SQL 放入 `attributes.rawStatement`。
- parser 正常返回空列表不自动等价为失败，因为很多 SQL 本来就不包含表关系证据。

### StructuredSqlParser / StructuredDdlParser

当前 SQL/DDL 文本解析统一通过 `adaptor.parsers()` 内的 structured parser
进入 core runner。ANTLR 是底层 lexer/parser 技术名；用户可见运行模式是
`token-event` 或 `full-grammar`。`AdaptorParsers` 的 structured SQL/DDL 字段是
`Optional`，但 `scriptFramer` 是必需的 `DialectScriptFramer`，`DatabaseAdaptor` 本身不再提供旧式 default getter。缺失的一侧不会由
core Simple parser 假装支持。

### DialectScriptFramer

`DialectScriptFramer` 将 client script 的边界处理与 server SQL grammar 明确分开：前者只
产生可解析的 `SqlStatementRecord` 和 framing warning，后者才产生 `StructuredParseResult`。
这是 adaptor SPI v6 的必需能力，不能再用 core 的通用文本 splitter、对象文件 extractor
或 raw-SQL regex 推断 statement / object 边界。

```java
public record AdaptorParsers(
    SqlRelationParser sqlRelations,
    Optional<StructuredSqlParser> structuredSql,
    Optional<StructuredDdlParser> structuredDdl,
    DialectScriptFramer scriptFramer
) {}

@FunctionalInterface
public interface DialectScriptFramer {
  ScriptFrameResult frame(ScriptFrameRequest request);
}
```

`ScriptFrameRequest` 保留原始文本、source file 和默认 `StatementSourceType`；
`ScriptFrameResult` 返回带准确 source/line provenance 的 server statements 与 framing
warnings。共享 `StructuredScriptFramer` 只消费各 adaptor generated script lexer 的 typed
lexeme，不能按 rule name、反射或 raw SQL 文本作结构推断。

`StructuredScriptFramer` 只编排 lexeme 校验、fixture marker 和 statement/provenance 装配；
`MySqlScriptSlicePlanner`、`PostgresScriptSlicePlanner`、`OracleScriptSlicePlanner`、
`SqlServerScriptSlicePlanner`、`CommonScriptSlicePlanner` 分别拥有各自方言的 slice 算法。
这些 planner 是 core 内部职责类，不改变 `DialectScriptFramer` SPI。

当前 `ScriptFileExtractor` 直接转发 `ScriptFrameResult.statements/warnings`，尚未对外部
framer 返回的 null result、statement provenance、warning envelope 和嵌套 attributes 做原子契约校验。
record 构造器的顶层 `List.copyOf` 只防止列表结构被后续改写，不能证明其元素语义合法。
这是实现缺口，不是 framer 可以生成事实或修改 server SQL 的授权。

script framer 不改变 server SQL 内显式写出的 catalog、schema、quote 或标识符拼写。后续
SQL/DDL parser 必须保留这些显式限定名；对于 bare table，scan pipeline 可以使用已经规范化且
唯一的 `ScanScope` / object definition namespace 构造 `TableId` / `ColumnRef` / `Endpoint`，使
生产输出与 live metadata、database DDL 精确对齐。没有唯一 namespace 时保持 bare，且不得按名称
搜索、降级或把 bare 与任意 qualified endpoint 等价。该 materialization 是 identifier resolution，
不是 script framing 或 naming inference。

| Dialect | Client-script framing |
| --- | --- |
| MySQL | `DELIMITER` directive 改变后续 statement terminator；quoted/comment 区间不参与 delimiter 匹配。 |
| PostgreSQL | `$tag$ ... $tag$` dollar-quoted 区间内的 semicolon 不分割 statement。 |
| Oracle | 单独一行 `/` 结束 PL/SQL / object block；普通 SQL 仍由 semicolon 分割。 |
| SQL Server | 单独一行 `GO` 结束 batch；procedure/function/trigger batch 不再被内部 semicolon 拆开。 |
| Common | 使用 typed semicolon framing。 |

职责：

- 只提供方言感知的 client-script framing：statement 字符区间、起止行、对象类型和 framing warning。
- 输出 `ScriptFrameResult`；不生成 relationship、lineage、naming、DDL event 或 `StructuredParseResult`。
- framed server SQL 随后才交给 structured SQL/DDL parser；framer 不直接决定最终 confidence。
- SQL 与 DDL 是两个独立 SPI，不应合并为一个 `structuredParser()`。二者可以共享底层方言 lexer/parser 规则，但 Java 端必须保留不同入口，便于独立 visitor、diagnostics、warning provenance 和 correctness fixture 验收。

当前策略：

- MySQL adaptor 根包只保留 `MySqlDatabaseAdaptor` 装配入口；token-event parser 位于 `com.relationdetector.mysql.tokenevent`，暴露 `MySqlTokenEventStructuredSqlParser` / `MySqlTokenEventStructuredDdlParser`。
- PostgreSQL adaptor 根包只保留 `PostgresDatabaseAdaptor` 装配入口；token-event parser 位于 `com.relationdetector.postgres.tokenevent`，暴露 `PostgresTokenEventStructuredSqlParser` / `PostgresTokenEventStructuredDdlParser`。
- Oracle adaptor 根包只保留 `OracleDatabaseAdaptor` 装配入口；token-event parser 位于 `com.relationdetector.oracle.tokenevent`，暴露 `OracleTokenEventStructuredSqlParser` / `OracleTokenEventStructuredDdlParser`。`OracleRelationSql.g4` 归 `grammar/oracle-token-event` artifact 所有。
- SQL Server adaptor 根包只保留 `SqlServerDatabaseAdaptor` 装配入口；token-event parser 位于 `com.relationdetector.sqlserver.tokenevent`，暴露 `SqlServerTokenEventStructuredSqlParser` / `SqlServerTokenEventStructuredDdlParser`。compact `SqlServerRelationSql.g4` 归 `grammar/sqlserver-token-event` artifact 所有。
- SQL/DDL parser 由 `ParserBundleSelector` 按 `parser.mode: auto|full-grammar|token-event` 统一选择，并一次性返回同一模式下的 SQL parser 与 DDL parser。profile/version/JDBC metadata 足够时可通过 adaptor 注册的 `FullGrammarDialectModule` 使用 full-grammar；无合理配置、profile 不支持或 full-grammar hard failure 时 fallback 到 adaptor token-event parser 并记录 warning。profile 已选中后的 syntax warning / partial result 属于 full-grammar 结果，不在 event 层委托 token-event 补齐。`parser.sql.mode`、`parser.ddl.mode` 和 simple/shadow fallback 已移除。
- `SqlRelationParserRunner` 与 `DdlRelationParserRunner` 都从 `ParserBundle` 取 parser，不再分别重复 profile selection。SQL runner 还会复用同一个 `StructuredParseResult` 供 relationship 与 Data Lineage 抽取使用。
- 第三方 adaptor 可以在 `AdaptorParsers` 中只提供 structured SQL 或 structured DDL
  一侧，但缺失的一侧不应由 core simple parser 假装支持；应明确返回空 capability/
  warning。
- 新增大版本 full-grammar 支持时，应在对应 adaptor 内新增 version package 和 `FullGrammarDialectModule`，由 core registry 通过 `ServiceLoader` 注入；core 不直接 import 方言实现类。
- `FullGrammarDialectModule` 不属于 `DatabaseAdaptor` 接口本身；它是同一 adaptor jar 中的版本化 grammar module，通过 `META-INF/services/com.relationdetector.core.fullgrammar.FullGrammarDialectModule` 注册。这样 core 可以做统一 profile selection，而具体 grammar、generated parser、parse-tree visitor 和 expression analyzer 仍归属 MySQL/PostgreSQL/Oracle/SQL Server adaptor。
- 版本化 full-grammar module 与 token-event parser 的职责不同：token-event 是 adaptor 暴露的宽松生产 parser / fallback；full-grammar 是 adaptor jar 额外注册的严格版本 grammar profile。parser selection 可以在两者之间选择，但 full-grammar parser 内部不再委托 token-event 生成事件。

`SqlRelationParserRunner` 与 `DdlRelationParserRunner` 使用独立的 warning buffer 和 detached
`AdaptorContext`。整个 `StructuredParseResult` 及 callback warning 通过
`AdaptorParseResultContractValidator` 后，runner 才执行 provenance/fact 提取并一次提交 warning。
`ParserBundleSelector` 允许普通 full-grammar runtime failure 回退到 token-event，但会丢弃失败尝试的
warning，并使用不含插件异常消息的固定 fallback 文本；`AdaptorContractException` 表示 SPI 契约违约，
必须原样上抛，禁止通过 fallback 掩盖。

### DatabaseDdlCollector

```java
Optional<DatabaseDdlCollector> ddl = adaptor.collectors().databaseDdl();
```

职责：

- 从 live database 读取表定义 DDL 文本，但不直接生成关系。
- `DatabaseDdlDefinition.ddl()` 允许是 parser-grade structural declaration，但 adaptor 文档必须明确
  它是完整可执行 DDL 还是关系解析骨架。缺少 type modifier、default、identity/generated/computed、
  collation 等信息时不得标记为 full-fidelity declaration。
- MySQL 实现使用 `SHOW CREATE TABLE catalog.table`，返回
  `DatabaseDdlDefinition(catalog, schema, name, ddl, "SHOW CREATE TABLE")`。MySQL database
  在统一身份模型中映射为 `catalog`，不能降级写入 `schema`。
- MySQL adaptor 的 `canonicalizeScope()` 在 JDBC 前把 `database.catalog` 规范为 database；旧
  `database.schema` 仅作为兼容回退。两者同时非空且不同必须配置失败；canonical scope 固定为
  `catalog=<database>, schema=null`，且 include/exclude table 配置原样保留。
- `ScanEngine` 把返回的 DDL text 喂给 `DdlRelationParserRunner.parseText(...)`，因此统一走 `parser.mode` 选择后的 DDL extraction；默认无 profile/version 时使用 token-event DDL。
- 解析出的 evidence 使用 `EvidenceSourceType.DATABASE_DDL`，与用户提供的 `DDL_FILE` 区分。
- collector 必须遵守 `includeTables/excludeTables`，并且单表读取失败时记录 warning 后继续读取其它表。当前这两个字段是经 adaptor identifier rules 规范化后的精确表名列表，不是 glob 或正则；文件输入的 `paths + include` 才是路径 glob 契约。
- 已枚举表身份后，definition query 返回 null/blank body 或成功但零行都表示
  definition unavailable，并输出带 catalog/schema/name 的安全 `DEFINITION_UNAVAILABLE`。
  MySQL `SHOW CREATE TABLE` 和 Oracle `DBMS_METADATA.GET_DDL` 的 null/blank 与零行路径均不构造
  空 `DatabaseDdlDefinition`。

## 数据画像接口

```java
public interface DataProfiler {
  ProfileOutcome profile(Connection connection, ProfileRequest request);
}
```

限制：

- 只对已有候选关系运行。
- 不对所有表列做全组合扫描。
- live 模式对每个候选执行 exact aggregate query，并遵守 `timeoutSeconds`、候选总量和每个 source
  的 target 数预算。SPI v6 不包含离线样本选项。
- `ProfileOutcome.evidence` 只允许 `VALUE_CONTAINMENT_HIGH`、`VALUE_OVERLAP_HIGH` 和
  `NEGATIVE_VALUE_MISMATCH`。adaptor 不得借 profiler 注入 metadata、DDL、SQL、naming 或 derived evidence。
- 只有 `SUCCESS` 可携带非空 evidence；`NO_EVIDENCE`、skip 和 failure status 必须返回空 evidence。
- core consumer 必须重验上述 allowlist/status invariant，并对 `NEGATIVE_VALUE_MISMATCH` 再执行
  声明 FK、live mode 和 conditional/polymorphic policy，不能把外部 adaptor 视为可信边界。
- `ProfileOutcome.warnings` 同样属于不可信 SPI 输入。core 只验证 status 对应的 warning type/code；
  plugin message/source/attributes 不进入 `ScanResult`，固定安全 warning 由 core 按 status、adaptor id 与
  candidate endpoints 重建。所有 bounded outcomes 先完整验证再统一应用，任一违规不会留下部分结果。
- 默认关闭。

## 权重修正接口

```java
public interface EvidenceWeightAdjuster {
  Evidence adjust(Evidence evidence, AdaptorContext context);
}
```

用途：

- 数据库特定日志可信度修正。
- 特殊对象定义可信度修正。
- 方言解析置信度修正。

约束：

- 该 hook 只允许替换 evidence score；`type`、`sourceType`、`source`、`detail`
  和 `attributes` 必须与输入完全相同。
- 修正后 evidence score 仍必须在 `[-1, 1]` 范围内。
- 应先校验本轮全部调整结果，再原子替换 relationship/naming observations；
  任一返回值违约时不得留下前序部分修改。
- core 负责最终合并和封顶。

`EvidenceWeightAdjustmentService` 向 adjuster 提供拒绝 warning 的 detached context、
deep-immutable options 和 deep-detached evidence baseline。service 先计算全部 relationship
replacement，再完成 naming raw-evidence 转换；返回值相对 baseline 只允许 score 变化，core 使用
baseline identity/attributes 与新 score 重建 evidence。relationship、naming 和 warning 只有整批成功后
才替换，因此 hook 修改嵌套 list/map、保留插件容器引用或在最后一项违约都不会留下部分状态。

## Java SPI 注册

每个 adaptor jar 包含：

```text
META-INF/services/com.relationdetector.contracts.spi.DatabaseAdaptor
```

内容：

```text
com.relationdetector.mysql.MySqlDatabaseAdaptor
```

CLI 启动时：

1. 加载应用 classpath 中的 adaptor。
2. 如果配置了 `--plugin-dir`，将目录中的 jar 加入独立 classloader。
3. 通过 `ServiceLoader` 发现 adaptor。
4. 根据 `database.type` 选择唯一匹配 adaptor。

冲突处理：

- 如果没有匹配 adaptor，启动失败。
- 如果多个 adaptor 匹配同一 `database.type`，除非用户指定 `adaptorId`，否则启动失败。
- 外部 adaptor 与内置 adaptor id 冲突时，默认拒绝并提示用户。

## Capabilities

当前 capability 是显式枚举集合：

```java
Set<AdaptorCapability> capabilities();
```

生产链路在 JDBC 连接前完成统一 preflight：

- 用户实际配置 JDBC metadata、database DDL、database objects 或 data profile 时，capability、
  collector/profiler 及必需的 structured parser 必须同时存在，否则在打开 JDBC 前失败。
- metadata、database DDL 或 database objects 等 live-backed source 一旦启用，`jdbcUrl` 必须非空。
  `ScanConfigurationValidator` 在 capability preflight 和 JDBC 打开前执行该组合校验，不能依赖
  `live = hasText(jdbcUrl)` 来反向证明请求不是 live。
- 用户提供 native log，但 adaptor 不支持对应格式时，在文件处理前失败并指出 adaptor id、
  source kind 和 required capability。
- 纯文件扫描不会因为 live capability 的默认配置而失败。

## Mock adaptor 验收

提供一个测试用 `mock-adaptor.jar`：

- id: `mockdb`
- 支持 metadata。
- 返回固定 `orders.user_id -> users.id` evidence。

验收：

```bash
relation-detector scan --config mock.yml --plugin-dir target/mock-plugins
```

结果中应出现 mock adaptor 的固定关系。

## 验收标准

- 内置 MySQL/PostgreSQL/Oracle/SQL Server adaptor 可以通过 Java SPI 被发现。
- 外部 mock adaptor jar 可以通过 `--plugin-dir` 被发现。
- `database.type` 可以匹配到唯一 adaptor。
- adaptor id 冲突时 CLI 给出明确错误。
- adaptor capabilities 必须在连接数据库前通过配置预检；声明与 optional collector/parser/profiler 必须同时存在。
- adaptor 提供的 evidence 仍由 core 完成最终归并和评分。

## 测试设计

- classpath 内置 adaptor 发现测试。
- `--plugin-dir` 外部 adaptor 发现测试。
- adaptor id 冲突测试。
- database.type 无匹配测试。
- capabilities 与配置冲突测试。
- 权重修正后仍由 core 完成最终合并测试。
