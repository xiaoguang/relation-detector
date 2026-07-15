# Phase 1：工程骨架详细设计

## 目标

建立可编译、可运行、可测试的 Java 17 + Maven 多模块工程骨架，为后续核心模型、adaptor、CLI 输出和集成测试提供稳定结构。

本阶段不实现真实关系探测，只实现空扫描流程和基本配置加载。

当前实现注记：Phase 1 记录的是工程骨架起点；当前仓库根已经收口为 `relation-detector/` 与 `semantic-layer/` 两个同级目录。`relation-detector/` 内包含 `contracts/core/cli/adaptor-mysql/adaptor-postgres/adaptor-oracle/adaptor-sqlserver` 等短目录模块，并实现 token-event / full-grammar parser、relationship、Data Lineage、DDL、naming evidence、confidence、CLI E2E golden 等完整链路。`semantic-layer/` 内包含独立的 KG 构建核心和 semantic CLI，只消费 relation-detector JSON。当前实现细节以 Phase 6、代码实现指南和代码与设计对应审视报告为准。

## 模块结构

```text
relation-detector/
  contracts/
  core/
  cli/
  adaptor-mysql/
  adaptor-postgres/
  adaptor-oracle/
  adaptor-sqlserver/
  sample-data/
  test-fixtures/
  scripts/
semantic-layer/
  semantic-core/
  semantic-cli/
```

## 父 POM 设计

父 POM 负责统一：

- Java 版本：17。
- 编码：UTF-8。
- Maven compiler plugin。
- Surefire/Failsafe 测试插件。
- 依赖版本管理。
- 所有子模块声明。

建议在父 POM 中集中管理依赖版本：

- `picocli`
- `jackson-databind`
- `jackson-dataformat-yaml`
- `slf4j-api`
- `logback-classic`
- `junit-jupiter`
- `assertj-core`
- `testcontainers`
- MySQL JDBC driver
- PostgreSQL JDBC driver

## 子模块职责

### contracts

只放稳定 API 和少量无业务依赖的类型：

- `DatabaseAdaptor`
- `AdaptorContext`
- `DatabaseType`
- 采集器接口
- 解析器接口
- 数据画像接口
- warning/error 基础类型

该模块不能依赖 `core`，避免第三方 adaptor 被迫依赖 core 内部实现。

### core

实现通用核心能力：

- 扫描编排抽象。
- 空扫描结果模型。
- 候选关系模型占位。
- 输出 DTO 占位。
- warning 聚合。

Phase 1 中只提供最小实现，后续 Phase 2 扩展为完整模型。

### cli

实现命令行入口：

```bash
relation-detector scan --config config.yml --format json
```

Phase 1 的 `scan` 命令只做：

- 读取 YAML。
- 校验必需字段。
- 初始化空 `ScanResult`。
- 输出空 JSON 或空 table。

### adaptor-mysql / adaptor-postgres

Phase 1 只建模块和空 adaptor 声明，不实现真实数据库访问。

### test-fixtures

存放后续集成测试资源：

- MySQL schema。
- PostgreSQL schema。
- DDL 文件。
- SQL 日志文件。
- 期望输出 JSON。

Phase 1 可先放一个最小 `config-example.yml`。

## CLI 配置模型

建议 Java 配置类型：

```java
public record AppConfig(
    DatabaseConfig database,
    FilterConfig filters,
    SourceConfig sources,
    OutputConfig output
) {}
```

Phase 1 支持最小字段：

```yaml
database:
  type: mysql
  jdbcUrl: jdbc:mysql://localhost:3306/shop
  username: readonly
  password: ${DB_PASSWORD}
  catalog: shop

output:
  format: json
  minConfidence: 0.30
```

环境变量替换规则：

- 支持 `${ENV_NAME}`。
- 如果环境变量不存在，CLI 启动失败并输出明确错误。
- 不在日志中打印 password 原文。

## 空扫描输出

JSON：

```json
{
  "database": {
    "type": "mysql",
    "schema": "shop"
  },
  "generatedAt": "2026-06-14T00:00:00Z",
  "relationships": [],
  "warnings": []
}
```

Table：

```text
No relationships detected.
Warnings: 0
```

## 错误处理

本阶段定义 CLI 错误码：

- `0`：成功。
- `1`：配置文件不存在或不可读。
- `2`：配置格式错误。
- `3`：参数错误。
- `10`：扫描运行失败，后续阶段使用。

## 验收标准

- `mvn test` 可以通过。
- `relation-detector scan --help` 可以显示帮助。
- `relation-detector scan --config config.yml --format json` 可以输出空结果。
- YAML 缺少 `database.type` 时有明确错误。
- password 不出现在普通日志中。

## 测试设计

- CLI help 快照测试。
- YAML 成功加载测试。
- YAML 缺字段失败测试。
- `${ENV_NAME}` 替换测试。
- JSON 空结果序列化测试。
- table 空结果输出测试。
