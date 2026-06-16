# 代码实现说明与运维测试指南

本文档面向后续实施、维护、运维和测试人员，解释当前代码如何对应设计文档，以及如何运行示例、投喂数据、理解输出和设计测试用例。

## 1. 当前实现状态

当前代码已经落成 Java 17 + Maven 多模块工程：

```text
relation-detector/
  pom.xml
  relation-adaptor-api/
  relation-core/
  relation-cli/
  adaptor-mysql/
  adaptor-postgres/
  test-fixtures/examples/
```

已实现能力：

- 稳定 enum 和 adaptor API。
- Java SPI adaptor 发现。
- MySQL/PostgreSQL 内置 adaptor。
- DDL 外键解析，包括 inline references、ALTER TABLE FK、复合 FK、quoted schema-qualified 名称，以及 PK/unique/index 辅助 evidence。
- 纯 SQL 文本、MySQL 日志、PostgreSQL statement log 的 SQL 抽取。
- 简易 JOIN、IN 子查询、表共现解析。
- 关系证据合并和置信度计算。
- JSON/table 输出。
- file-only 示例配置和输入数据。

第一版故意保持低外部依赖，目前没有引入 picocli、Jackson、JSqlParser、JUnit、Testcontainers。设计文档中这些仍是推荐演进方向；当前代码先保证结构清楚、可编译、可运行，后续可以按模块替换实现。

## 2. 模块说明

### 2.1 relation-adaptor-api

核心文件：

- `Enums.java`
- `DatabaseAdaptor.java`
- `Collectors.java`
- `RelationshipCandidate.java`
- `Evidence.java`
- `TableId.java`
- `ColumnRef.java`
- `Endpoint.java`

职责：

- 定义第三方数据库 adaptor 必须遵守的接口。
- 定义 JSON 输出和内部模型使用的 enum。
- 定义 evidence、warning、metadata snapshot、SQL statement 等跨模块类型。

设计对应：

- `docs/design/phase-02-core-model-scoring.md`
- `docs/design/phase-03-adaptor-api-spi.md`
- `docs/design/enum-reference.md`

维护注意：

- enum 的 JSON 字符串不能随意改名。
- `relation-adaptor-api` 不应依赖 `relation-core`，否则第三方 adaptor 会被迫依赖核心实现。
- 新增数据库 adaptor 时，应只依赖该 API 和必要的工具库。

### 2.2 relation-core

核心文件：

- `ScanEngine.java`
- `RelationshipMerger.java`
- `ConfidenceCalculator.java`
- `SimpleSqlRelationParser.java`
- `SqlLineageResolver.java`
- `SimpleDdlParser.java`
- `JsonResultWriter.java`
- `TableResultWriter.java`

职责：

- 编排扫描流程。
- 调用 adaptor 的 metadata、DDL、object、log、profile 钩子。
- 合并多个来源产生的关系候选。
- 计算最终 confidence。
- 输出 JSON/table。

设计对应：

- `docs/design/phase-02-core-model-scoring.md`
- `docs/design/phase-06-parser-enhancement.md`
- `docs/design/phase-08-output-ux.md`

维护注意：

- `ConfidenceCalculator` 是置信度公式的唯一默认实现。
- `RelationshipMerger` 负责 `relationSubType` 主导证据优先级。
- `SimpleSqlRelationParser` 是轻量实现，后续可替换为 JSqlParser 版本，但输出仍应是 `RelationshipCandidate`。
- `SqlLineageResolver` 为 `SimpleSqlRelationParser` 提供保守列血缘映射，支持 CTE、派生表和多层嵌套查询中的简单列投影回溯。
- `SimpleDdlParser` 把显式 FK/inline references 作为强关系证据，把 PK/unique/source index 作为已有 FK 的辅助 evidence；partial、expression、functional、prefix index 默认不作为全局唯一或全列索引证据。
- `JsonResultWriter` 当前手写 JSON，后续可替换为 Jackson，但字段结构应保持兼容。

### 2.3 relation-cli

核心文件：

- `Main.java`
- `SimpleYamlConfigLoader.java`
- `AdaptorRegistry.java`

职责：

- 解析命令行参数。
- 读取 YAML 配置。
- 通过 Java SPI 加载 adaptor。
- 调用 `ScanEngine`。
- 将结果写到 stdout 或文件。

设计对应：

- `docs/design/phase-01-project-skeleton.md`
- `docs/design/phase-03-adaptor-api-spi.md`
- `docs/design/phase-08-output-ux.md`

维护注意：

- `SimpleYamlConfigLoader` 只支持示例配置所需的 YAML 子集。
- 生产化时建议替换为 Jackson YAML。
- `AdaptorRegistry` 同时支持 classpath 内置 adaptor 和 `--plugin-dir` 外部 jar。

### 2.4 adaptor-mysql

核心文件：

- `MySqlDatabaseAdaptor.java`
- `META-INF/services/com.relationdetector.api.DatabaseAdaptor`

职责：

- 通过 `information_schema.KEY_COLUMN_USAGE` 读取显式 FK。
- 读取 MySQL routine、view、trigger 定义。
- 从 MySQL general/slow log 中抽取 SQL。
- 提供 MySQL 数据画像钩子。
- 通过 SPI 注册为 `mysql` adaptor。

设计对应：

- `docs/design/phase-04-mysql-adaptor.md`

维护注意：

- 当前元数据采集先实现显式 FK，unique/index 采集可以按 Phase 4 继续补。
- 当前数据画像只做轻量匹配 evidence，后续应补包含率、重合率、负向证据等完整指标。
- MySQL JDBC driver 没有内置到项目中，连接真实 MySQL 时需要运行环境提供驱动。

### 2.5 adaptor-postgres

核心文件：

- `PostgresDatabaseAdaptor.java`
- `META-INF/services/com.relationdetector.api.DatabaseAdaptor`

职责：

- 通过 `pg_catalog.pg_constraint` 读取显式 FK。
- 读取 PostgreSQL function/procedure/view 定义。
- 从 PostgreSQL statement log 中抽取 SQL。
- 提供 PostgreSQL 数据画像钩子。
- 通过 SPI 注册为 `postgresql` adaptor。

设计对应：

- `docs/design/phase-05-postgres-adaptor.md`

维护注意：

- 当前触发器关联 trigger function 的逻辑还可以继续增强。
- PostgreSQL 的 quoted identifier 规则已经在 `IdentifierRules` 中预留。
- PostgreSQL JDBC driver 没有内置到项目中，连接真实 PostgreSQL 时需要运行环境提供驱动。

## 3. 关键执行流程

一次扫描的主链路：

```text
Main
  -> SimpleYamlConfigLoader
  -> AdaptorRegistry
  -> ScanEngine
  -> adaptor metadata/object/ddl/log/profile hooks
  -> RelationshipMerger
  -> ConfidenceCalculator
  -> JsonResultWriter 或 TableResultWriter
```

关系从多个来源进入：

```text
DDL FK               -> DDL_FOREIGN_KEY
DB metadata FK       -> METADATA_FOREIGN_KEY
VIEW/PROCEDURE JOIN  -> VIEW_JOIN / PROCEDURE_JOIN
SQL log JOIN         -> SQL_LOG_JOIN
SQL log co-occurrence-> SQL_LOG_TABLE_CO_OCCURRENCE
data profile         -> VALUE_OVERLAP_HIGH / VALUE_CONTAINMENT_HIGH
```

最终每条关系都会包含：

- source table/column。
- target table/column。
- relationType。
- relationSubType。
- confidence。
- evidence list。
- warning list。

## 4. 运维示例：从构建到输出

本章给出足够细的 file-only 示例，不需要真实数据库。它适合本地验证、运维演示和黑盒测试。

### 4.1 构建项目

```bash
mvn test
```

预期：

```text
BUILD SUCCESS
```

### 4.2 示例输入文件

DDL 文件：

```text
test-fixtures/examples/schema.sql
```

SQL 日志文件：

```text
test-fixtures/examples/app-sql.sql
```

配置文件：

```text
test-fixtures/examples/file-only-config.yml
```

该配置关闭 JDBC 元数据，开启 DDL 和纯 SQL 文本日志，因此可以在没有数据库的环境里运行。

### 4.3 运行 JSON 输出

当前第一版没有打包 fat jar，直接用模块 classpath 运行：

```bash
java -cp "relation-cli/target/classes:relation-core/target/classes:relation-adaptor-api/target/classes:adaptor-mysql/target/classes:adaptor-postgres/target/classes" \
  com.relationdetector.cli.Main \
  scan \
  --config test-fixtures/examples/file-only-config.yml \
  --format json
```

预期输出应包含：

```json
{
  "relationships": [
    {
      "source": { "table": "orders", "column": "user_id" },
      "target": { "table": "users", "column": "id" },
      "relationType": "FK_LIKE",
      "relationSubType": "DDL_DECLARED_FK",
      "confidence": 0.9550
    },
    {
      "source": { "table": "users", "column": null },
      "target": { "table": "audit_logs", "column": null },
      "relationType": "CO_OCCURRENCE",
      "relationSubType": "TABLE_CO_OCCURRENCE",
      "confidence": 0.2500
    }
  ]
}
```

说明：

- `orders.user_id -> users.id` 同时来自 DDL FK 和 SQL JOIN，所以 confidence 高于单独 DDL FK。
- `users -> audit_logs` 只有共现证据，因此是表级弱关系。

### 4.4 运行 table 输出

```bash
java -cp "relation-cli/target/classes:relation-core/target/classes:relation-adaptor-api/target/classes:adaptor-mysql/target/classes:adaptor-postgres/target/classes" \
  com.relationdetector.cli.Main \
  scan \
  --config test-fixtures/examples/file-only-config.yml \
  --format table
```

预期输出形态：

```text
SOURCE                    TARGET                    TYPE            SUBTYPE                  CONF   EVIDENCE
orders.user_id            users.id                  FK_LIKE         DDL_DECLARED_FK          0.9550 DDL_FOREIGN_KEY,SQL_LOG_JOIN
users                     audit_logs                CO_OCCURRENCE   TABLE_CO_OCCURRENCE      0.2500 SQL_LOG_TABLE_CO_OCCURRENCE

Warnings: 0
```

### 4.5 写入输出文件

```bash
java -cp "relation-cli/target/classes:relation-core/target/classes:relation-adaptor-api/target/classes:adaptor-mysql/target/classes:adaptor-postgres/target/classes" \
  com.relationdetector.cli.Main \
  scan \
  --config test-fixtures/examples/file-only-config.yml \
  --format json \
  --output target/example-result.json
```

检查：

```bash
cat target/example-result.json
```

### 4.6 连接真实数据库的配置示例

MySQL：

```yaml
database:
  type: mysql
  jdbcUrl: jdbc:mysql://localhost:3306/shop
  username: readonly
  password: ${DB_PASSWORD}
  schema: shop

sources:
  metadata:
    enabled: true
  ddl:
    enabled: false
  objects:
    enabled: true
    fromDatabase: true
  logs:
    enabled: false
  dataProfile:
    enabled: false
```

PostgreSQL：

```yaml
database:
  type: postgresql
  jdbcUrl: jdbc:postgresql://localhost:5432/shop
  username: readonly
  password: ${DB_PASSWORD}
  schema: public

sources:
  metadata:
    enabled: true
  objects:
    enabled: true
    fromDatabase: true
  dataProfile:
    enabled: false
```

注意：

- 当前项目没有内置 JDBC driver。真实数据库运行时，需要把对应 JDBC driver 放到 classpath。
- 生产环境建议默认关闭 `dataProfile`。
- 如果 `${DB_PASSWORD}` 环境变量不存在，CLI 会失败并提示缺少环境变量。

## 5. 可测性设计

本章用于指导后续测试开发和系统测试。

### 5.1 白盒单元测试

建议优先覆盖 core，因为 core 决定输出稳定性。

核心测试对象：

- `ConfidenceCalculator`
- `RelationshipMerger`
- `SimpleSqlRelationParser`
- `SimpleDdlParser`
- `SimpleYamlConfigLoader`
- `AdaptorRegistry`

建议用例：

- 单个 `METADATA_FOREIGN_KEY` 输出 0.98。
- `DDL_FOREIGN_KEY + SQL_LOG_JOIN` 合并后高于 0.90。
- `NEGATIVE_VALUE_MISMATCH` 会降低分数。
- 多 evidence 下 `DECLARED_FK` subtype 不被覆盖。
- JOIN `orders.user_id = users.id` 输出列级 `FK_LIKE`。
- `FROM users, audit_logs` 无连接条件时输出 `CO_OCCURRENCE`。
- `IN (SELECT ...)` 输出 `SUBQUERY_INFERRED_FK`。
- YAML 中环境变量缺失时报错。
- unknown adaptor 报 `ADAPTOR_ERROR`。

### 5.2 黑盒功能测试

从 CLI 入口测试完整链路。

建议用例：

- file-only DDL + SQL 日志输入，输出 JSON。
- file-only DDL + SQL 日志输入，输出 table。
- `--min-confidence 0.90` 过滤掉低置信共现关系。
- 输入文件不存在时返回非零错误码。
- 配置只开启 `dataProfile` 时失败。
- 配置 `database.type: oracle` 但无 adaptor 时失败。
- `--output target/result.json` 能写文件。

### 5.3 集成测试

MySQL：

- 使用 Testcontainers MySQL 8。
- 建表：显式 FK、unique index、普通 index。
- 创建 view、procedure、trigger。
- 投喂 general log/slow log fixture。
- 验证输出包含显式 FK、JOIN 推断、共现关系。

PostgreSQL：

- 使用 Testcontainers PostgreSQL 12+。
- 建 schema、FK、unique index。
- 创建 view、function、trigger function。
- 投喂 statement log fixture。
- 验证 schema、quoted identifier 和 FK 方向。

### 5.4 性能测试

目标：

- 验证 SQL 日志大文件解析吞吐。
- 验证大量候选关系归并性能。
- 验证数据画像受限，不会无限扫描。

建议指标：

- 10 万条 SQL 日志解析耗时。
- 100 万条 SQL 日志解析耗时。
- 10 万 candidate merge 内存占用。
- dataProfile 在 `sampleRows`、`timeoutSeconds`、`maxCandidatePairs` 下是否按预期停止。

### 5.5 稳定性和回归测试

建议维护一组 fixture：

- `simple-fk-schema.sql`
- `join-log.sql`
- `subquery-log.sql`
- `ambiguous-join.sql`
- `co-occurrence.sql`
- `mysql-slow.log`
- `postgres-statement.log`

每个 fixture 配一个 expected JSON。

回归测试策略：

- JSON snapshot 测试字段兼容性。
- enum 序列化值稳定性测试。
- warning code 稳定性测试。
- 置信度数值允许小范围精度变化，但 subtype 和 evidence 不应无故改变。

### 5.6 运维验收测试

上线前建议按以下清单验证：

- `mvn test` 成功。
- file-only 示例成功。
- MySQL 只读账号能读取 metadata。
- PostgreSQL 只读账号能读取 metadata。
- 无权限读取过程/函数时，工具给 warning 而不是崩溃。
- dataProfile 默认关闭。
- 输出 JSON 不包含 password 或真实采样值。
- 大日志文件解析失败时能定位到 source 和 line。

## 6. 后续演进建议

- 引入 picocli 替换手写 CLI 参数解析。
- 引入 Jackson YAML/JSON 替换轻量解析和手写 JSON。
- 引入 JSqlParser 或数据库方言 parser 替换 `SimpleSqlRelationParser`。
- 补充 JUnit 5、AssertJ、Testcontainers 自动化测试。
- 增加 Maven assembly/shade 打包，生成单个可执行发行包。
- 扩展 MySQL/PostgreSQL unique/index 元数据采集。
- 按 adaptor API 增加 SQL Server 和 Oracle 模块。
