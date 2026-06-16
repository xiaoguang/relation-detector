# Phase 8：输出和用户体验详细设计

## 目标

完善 CLI 使用体验、JSON 输出、table 输出、错误码、warning 摘要、README 和示例配置，使用户可以稳定运行工具并理解每条关系的来源与置信度。

## CLI 命令

主命令：

```bash
relation-detector scan --config config.yml
```

常用参数：

```bash
relation-detector scan \
  --config config.yml \
  --format json \
  --output result.json \
  --plugin-dir plugins/
```

参数：

- `--config`：必填，YAML 配置路径。
- `--format`：可选，`json` 或 `table`，默认读取配置。
- `--output`：可选，输出文件；未指定时输出到 stdout。
- `--plugin-dir`：可选，外部 adaptor jar 目录。
- `--min-confidence`：可选，覆盖配置中的最小置信度。
- `--verbose`：可选，输出更多 warning 和诊断信息。

## YAML 配置

完整示例：

```yaml
database:
  type: mysql
  adaptorId: mysql
  jdbcUrl: jdbc:mysql://localhost:3306/shop
  username: readonly
  password: ${DB_PASSWORD}
  schema: shop

filters:
  includeTables:
    - orders
    - users
    - audit_logs
  excludeTables:
    - tmp_*
    - archive_*

sources:
  metadata:
    enabled: true
  ddl:
    enabled: true
    files:
      - schema.sql
  objects:
    enabled: true
    fromDatabase: true
    files:
      - routines.sql
  logs:
    enabled: true
    files:
      - mysql-general.log
      - app-sql.sql
  dataProfile:
    enabled: false
    sampleRows: 10000
    timeoutSeconds: 30
    maxCandidatePairs: 1000

output:
  format: json
  minConfidence: 0.30
  includeEvidence: true
  includeWarnings: true
```

配置校验：

- `database.type` 必填。
- 至少启用一种 source。
- 启用文件 source 时文件必须存在。
- 启用 JDBC source 时 jdbcUrl、username、password 必须可解析。
- `sampleRows`、`timeoutSeconds` 必须为正数。

## JSON 输出

顶层：

```json
{
  "database": {
    "type": "mysql",
    "schema": "shop"
  },
  "generatedAt": "2026-06-14T00:00:00Z",
  "summary": {
    "relationshipCount": 1,
    "warningCount": 0,
    "sources": ["metadata", "ddl", "logs"]
  },
  "relationships": [],
  "warnings": []
}
```

关系：

```json
{
  "source": {
    "table": "orders",
    "column": "user_id"
  },
  "target": {
    "table": "users",
    "column": "id"
  },
  "relationType": "FK_LIKE",
  "relationSubType": "PROFILE_SUPPORTED_FK",
  "confidence": 0.91,
  "evidence": [
    {
      "type": "SQL_LOG_JOIN",
      "score": 0.55,
      "source": "mysql-slow-log",
      "detail": "JOIN orders.user_id = users.id appeared 143 times",
      "attributes": {
        "count": 143
      }
    }
  ],
  "warnings": []
}
```

规则：

- `confidence` 保留两位或四位小数，内部计算用高精度。
- 表级关系的 `column` 为 `null`。
- `evidence` 默认输出，除非用户关闭。
- 输出字段保持稳定，后续新增字段应向后兼容。

## Table 输出

适合终端阅读：

```text
SOURCE              TARGET        TYPE     SUBTYPE              CONF  EVIDENCE
orders.user_id      users.id      FK_LIKE  DECLARED_FK          0.98  METADATA_FOREIGN_KEY
users               audit_logs    CO_OCCURRENCE  TABLE_CO_OCCURRENCE  0.25  SQL_LOG_TABLE_CO_OCCURRENCE

Warnings: 2
- mysql-slow.log:128 parse failed: unsupported syntax
- routines.sql:42 ambiguous join condition
```

规则：

- 默认按 confidence 降序。
- 同分时按 source table、target table 排序。
- `--verbose` 时显示更多 evidence detail。
- table 输出仍受 `minConfidence` 过滤。

## Warning 设计

warning 类型：

- `CONFIG_WARNING`
- `PERMISSION_WARNING`
- `PARSE_WARNING`
- `PROFILE_WARNING`
- `AMBIGUOUS_RELATION_WARNING`
- `ADAPTOR_CAPABILITY_WARNING`

warning 字段：

- code。
- message。
- source。
- line。
- severity。

严重程度：

- `INFO`
- `WARN`
- `ERROR`

`ERROR` 级 warning 不一定让 CLI 失败，只有不可恢复错误才返回非零。

## 错误码

- `0`：成功。
- `1`：配置文件不存在或不可读。
- `2`：配置格式错误。
- `3`：参数错误。
- `4`：adaptor 未找到或冲突。
- `5`：输入文件错误。
- `10`：数据库连接失败。
- `11`：扫描运行失败。
- `12`：输出写入失败。

## README 内容

README 至少包含：

- 工具目标。
- 快速开始。
- MySQL 示例。
- PostgreSQL 示例。
- YAML 配置说明。
- JSON 输出说明。
- confidence 解释。
- adaptor 插件说明。
- 数据画像安全说明。
- 常见问题。

## 示例资产

在 `test-fixtures` 或 `examples` 提供：

- `mysql-config.yml`
- `postgres-config.yml`
- `schema.sql`
- `routines.sql`
- `mysql-general.log`
- `postgres-statement.log`
- `expected-output.json`

## 验收标准

- 用户能根据 README 跑通 MySQL 示例。
- 用户能根据 README 跑通 PostgreSQL 示例。
- JSON 输出可被 Jackson 稳定反序列化。
- table 输出在窄终端中仍可读。
- warning 摘要能帮助定位输入问题。
- 错误码稳定且有文档说明。

## 测试设计

- JSON schema 兼容性测试。
- table 排序测试。
- `minConfidence` 过滤测试。
- `--format` 覆盖配置测试。
- `--output` 写文件测试。
- warning 摘要测试。
- 错误码测试。
- README 示例命令 smoke test。
