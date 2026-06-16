# Phase 3：Adaptor API 和 SPI 详细设计

## 目标

定义可扩展数据库 adaptor API，并通过 Java SPI 支持内置 adaptor 和外部 jar adaptor。后续新增 SQL Server、Oracle 等数据库时，不需要修改 core 和 CLI 的主要逻辑。

## 总体原则

- adaptor API 独立放在 `relation-adaptor-api`。
- core 只依赖 adaptor API，不依赖具体 adaptor。
- adaptor 可以参与全链路扩展，但默认流程由 core 编排。
- core 统一做候选关系归并、最终评分和输出。
- adaptor 可以提供 evidence 权重修正，但不能绕过输出 evidence 的要求。

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
  DdlParser ddlParser();
  SqlLogExtractor sqlLogExtractor();
  SqlRelationParser sqlRelationParser();
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
}
```

对象类型：

- procedure。
- function。
- view。
- trigger。

如果数据库账号权限不足，返回 warning，不终止扫描。

### SqlLogExtractor

```java
public interface SqlLogExtractor {
  Stream<SqlStatementRecord> extract(Path file, LogFormatHint hint);
}
```

支持：

- 数据库原生日志。
- 清洗后的纯 SQL 文本。

## 解析器接口

### DdlParser

```java
public interface DdlParser {
  List<Evidence> parseDdl(Path file, ParseContext context);
}
```

DDL parser 可以直接产生 evidence，也可以产生 metadata-like 结构，再交给 core 转换。

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
