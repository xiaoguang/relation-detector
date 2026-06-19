# Phase 3：Adaptor API 和 SPI 详细设计

## 目标

定义可扩展数据库 adaptor API，并通过 Java SPI 支持内置 adaptor 和外部 jar adaptor。后续新增 SQL Server、Oracle 等数据库时，不需要修改 core 和 CLI 的主要逻辑。

## 总体原则

- adaptor API 独立放在 `relation-adaptor-api`。
- core 只依赖 adaptor API，不依赖具体 adaptor。
- adaptor 可以参与全链路扩展，但默认流程由 core 编排。
- core 统一做候选关系归并、最终评分和输出。
- adaptor 可以提供 evidence 权重修正，但不能绕过输出 evidence 的要求。
- core 提供统一 runner、关系归并和评分；SQL/DDL 文本解析由对应数据库 adaptor 暴露的 Token/Event parser 承担，ANTLR 只作为底层 lexer/parser/token 支撑。明显属于某个数据库方言的 DDL、日志、对象定义差异应进入对应 adaptor。这样 MySQL、PostgreSQL、SQL Server、Oracle 后续可以独立演进，而不把所有语法分支堆进 core。

## DatabaseAdaptor 接口

建议接口：

```java
public interface DatabaseAdaptor {
  String id();
  String displayName();
  Set<String> supportedDatabaseTypes();
  IdentifierRules identifierRules();
  MetadataCollector metadataCollector();
  ObjectDefinitionCollector objectDefinitionCollector();
  SqlLogExtractor sqlLogExtractor();
  SqlRelationParser sqlRelationParser();
  Optional<StructuredSqlParser> structuredSqlParser();
  Optional<StructuredDdlParser> structuredDdlParser();
  Optional<DataProfiler> dataProfiler();
  EvidenceWeightAdjuster evidenceWeightAdjuster();
  AdaptorCapabilities capabilities();
}
```

说明：

- `id()` 例如 `mysql`、`postgresql`。
- `supportedDatabaseTypes()` 用于匹配 YAML 中的 `database.type`。
- `capabilities()` 声明该 adaptor 支持哪些来源和功能。

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
- trigger。

如果数据库账号权限不足，返回 warning，不终止扫描。

实现建议：

- 新 adaptor 应优先覆盖带 `warnings` 的重载。
- 如果 routine/view/trigger 中某一类对象读取失败，应记录对应 warning code，而不是吞掉异常。
- 已经读到的对象定义仍应返回，保持部分成功。

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
- 如果 hint 是 `PLAIN_SQL`，可以委托 core 的纯 SQL extractor；该 extractor 会在读取失败时记录 `SQL_FILE_EXTRACT_FAILED`。
- 对于原生日志，最好保留每条 SQL 的 `sourceName`、`startLine`、`endLine`，以便后续 SQL parser 抛异常时能把原始语句写入 warning。

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

DDL 不再通过旧 `DdlParser` SPI 暴露。adaptor 只通过 `structuredDdlParser()` 返回 Token/Event DDL parser，core 的 `DdlRelationParserRunner` 负责读取 DDL 文本、调用结构化 parser、再用 `DdlRelationExtractionVisitor` 生成统一 `RelationshipCandidate`。

方言拆分规则：

- MySQL/PostgreSQL DDL 均走 Token/Event DDL pipeline。MySQL 专属写法，例如反引号标识符、`KEY`/`INDEX` 选项、prefix index、invisible index、storage engine/table options、`SHOW CREATE TABLE` 格式，应在 `MySqlDdlStructuredEventVisitor` 或 `MySqlTokenEventStructuredDdlParser` 中处理。
- PostgreSQL 专属写法，例如 `ALTER TABLE ONLY`、`NOT VALID`、`CREATE INDEX CONCURRENTLY/IF NOT EXISTS`、`INCLUDE`、partial/expression index、opclass、partition/inheritance，应在 `PostgresDdlStructuredEventVisitor` 或 `PostgresTokenEventStructuredDdlParser` 中处理。
- adaptor parser 的输出仍必须是统一的 `RelationshipCandidate` 和 `Evidence`，不能绕过 core 的合并与置信度计算。
- 方言 parser 应有自己的单元测试，并包含正向和反向负向用例。这样可以证明某个数据库的语法增强不会悄悄改变其他数据库的解析行为。

### SqlRelationParser

```java
public interface SqlRelationParser {
  List<RelationshipEvidence> parse(SqlStatementRecord statement, ParseContext context);
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

ANTLR 迁移增加两个可选 SPI。默认方法返回 `Optional.empty()`，因此旧 adaptor 不需要立刻修改。

```java
default Optional<StructuredSqlParser> structuredSqlParser() {
  return Optional.empty();
}

default Optional<StructuredDdlParser> structuredDdlParser() {
  return Optional.empty();
}
```

职责：

- 提供方言感知的结构化解析前端。
- 输出 `StructuredParseResult`，包含 parser backend、dialect、结构化事件、warning 和诊断 attributes。
- 不直接决定最终 confidence。
- SQL 与 DDL 是两个独立 SPI，不应合并为一个 `structuredParser()`。二者可以共享底层方言 lexer/parser 规则，但 Java 端必须保留不同入口，便于独立 visitor、diagnostics、warning provenance 和 correctness fixture 验收。

当前策略：

- MySQL adaptor 暴露 `MySqlTokenEventStructuredSqlParser` / `MySqlTokenEventStructuredDdlParser`。
- PostgreSQL adaptor 暴露 `PostgresTokenEventStructuredSqlParser` / `PostgresTokenEventStructuredDdlParser`。
- SQL 关系输出由 `SqlRelationParserRunner` 调度到 adaptor 的 Token/Event SQL parser。`parser.sql.mode` 和 simple/shadow fallback 已移除；硬失败记录 warning 后继续扫描。
- DDL 关系输出由 `DdlRelationParserRunner` 调度到 adaptor 的 Token/Event DDL parser。`parser.ddl.mode` 和 simple-ddl/shadow fallback 已移除；硬失败记录 warning 后继续扫描。
- 第三方 adaptor 可以只实现 `structuredSqlParser()` 或只实现 `structuredDdlParser()`，但缺失的一侧不应再由 core simple parser 假装支持；应明确作为 future capability 或返回空/ warning。
- 如果某个方言后续引入完整 ANTLR grammar，推荐在同一方言 grammar 下拆出 SQL entry rule 与 DDL entry rule，再分别返回 `StructuredSqlParser` 和 `StructuredDdlParser` 实例；不推荐把 DDL constraint、SQL join 和日志噪声过滤写进一个通用 visitor。

### DatabaseDdlCollector

```java
default Optional<DatabaseDdlCollector> databaseDdlCollector() {
  return Optional.empty();
}
```

职责：

- 从 live database 读取表定义 DDL 文本，但不直接生成关系。
- MySQL v1 使用 `SHOW CREATE TABLE schema.table`，返回 `DatabaseDdlDefinition(schema, table, ddl, "SHOW CREATE TABLE")`。
- `ScanEngine` 把返回的 DDL text 喂给 `DdlRelationParserRunner.parseText(...)`，因此统一走 ANTLR DDL extraction。
- 解析出的 evidence 使用 `EvidenceSourceType.DATABASE_DDL`，与用户提供的 `DDL_FILE` 区分。
- collector 必须遵守 `includeTables/excludeTables`，并且单表读取失败时记录 warning 后继续读取其它表。

## 数据画像接口

```java
public interface DataProfiler {
  List<Evidence> profile(Connection connection, ProfileRequest request);
}
```

限制：

- 只对已有候选关系运行。
- 不对所有表列做全组合扫描。
- 必须遵守 `sampleRows` 和 `timeoutSeconds`。
- 默认关闭。

## 权重修正接口

```java
public interface EvidenceWeightAdjuster {
  BigDecimal adjust(Evidence evidence, AdaptorContext context);
}
```

用途：

- 数据库特定日志可信度修正。
- 特殊对象定义可信度修正。
- 方言解析置信度修正。

约束：

- 修正后 evidence score 仍必须在合理范围内。
- core 负责最终合并和封顶。

## Java SPI 注册

每个 adaptor jar 包含：

```text
META-INF/services/com.example.relation.api.DatabaseAdaptor
```

内容：

```text
com.example.relation.mysql.MySqlDatabaseAdaptor
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

`AdaptorCapabilities` 示例：

```java
public record AdaptorCapabilities(
    boolean metadata,
    boolean ddlParsing,
    boolean databaseObjects,
    boolean nativeLogs,
    boolean dataProfiling
) {}
```

CLI 可以据此提前校验配置：

- 用户启用 `dataProfile`，但 adaptor 不支持，则失败或 warning。
- 用户提供 native log，但 adaptor 不支持对应格式，则提示使用 pure SQL 模式。

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

- 内置 MySQL/PostgreSQL adaptor 可以通过 Java SPI 被发现。
- 外部 mock adaptor jar 可以通过 `--plugin-dir` 被发现。
- `database.type` 可以匹配到唯一 adaptor。
- adaptor id 冲突时 CLI 给出明确错误。
- adaptor capabilities 可以用于校验配置。
- adaptor 提供的 evidence 仍由 core 完成最终归并和评分。

## 测试设计

- classpath 内置 adaptor 发现测试。
- `--plugin-dir` 外部 adaptor 发现测试。
- adaptor id 冲突测试。
- database.type 无匹配测试。
- capabilities 与配置冲突测试。
- 权重修正后仍由 core 完成最终合并测试。
