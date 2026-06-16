# Phase 2：核心模型和评分详细设计

## 目标

建立统一领域模型、证据模型、关系归并规则和置信度计算规则。后续 MySQL、PostgreSQL 以及外部 adaptor 都必须输出或转换成这些模型。

本阶段重点是模型正确性和评分可解释性，不依赖真实数据库。

## 核心模型

### TableId

表示一个表的稳定身份：

```java
public record TableId(
    String catalog,
    String schema,
    String tableName,
    String normalizedName
) {}
```

规则：

- `catalog` 可为空。
- MySQL 中 `schema` 通常对应 database。
- PostgreSQL 中 `schema` 通常是 `public` 或用户 schema。
- `normalizedName` 由 adaptor 按数据库规则提供，core 不自行猜测大小写。

### ColumnRef

表示一个列引用：

```java
public record ColumnRef(
    TableId table,
    String columnName,
    String normalizedName,
    ColumnType type,
    boolean nullable
) {}
```

列可以在输出关系中为空，表示表级关系。

### RelationshipCandidate

候选关系：

```java
public final class RelationshipCandidate {
  private Endpoint source;
  private Endpoint target;
  private RelationType relationType;
  private RelationSubType relationSubType;
  private BigDecimal confidence;
  private List<Evidence> evidence;
  private List<WarningMessage> warnings;
}
```

`Endpoint` 包含 table 和可空 column。

方向规则：

- 显式 FK：子表/引用列是 source，父表/被引用列是 target。
- JOIN 推断：若一侧是 PK/unique 或命名为 `id`，另一侧命名如 `xxx_id`，则 `xxx_id` 一侧为 source。
- 数据画像：若 A 列值域大部分包含于 B，且 B 唯一或接近唯一，则 A 为 source，B 为 target。
- 无法确定方向时，不生成列级 `FK_LIKE`；退化为表级 `CO_OCCURRENCE`，并记录 warning。

## 关系类型

`RelationType`：

- `FK_LIKE`
- `CO_OCCURRENCE`

`RelationSubType`：

- `DECLARED_FK`
- `DDL_DECLARED_FK`
- `INFERRED_JOIN_FK`
- `SUBQUERY_INFERRED_FK`
- `PROFILE_SUPPORTED_FK`
- `NAMING_SUPPORTED_FK`
- `TABLE_CO_OCCURRENCE`

多证据场景下，`relationSubType` 采用主导证据原则：

1. `DECLARED_FK`
2. `DDL_DECLARED_FK`
3. `PROFILE_SUPPORTED_FK`
4. `INFERRED_JOIN_FK`
5. `SUBQUERY_INFERRED_FK`
6. `NAMING_SUPPORTED_FK`
7. `TABLE_CO_OCCURRENCE`

说明：

- `relationSubType` 只表达当前关系的主要可信形态。
- 所有细节差异必须保留在 evidence 列表中。
- 数据画像可以把 JOIN/命名推断提升为 `PROFILE_SUPPORTED_FK`，但不能覆盖显式 FK。

## Evidence 模型

```java
public record Evidence(
    EvidenceType type,
    BigDecimal score,
    EvidenceSource source,
    String detail,
    Map<String, Object> attributes
) {}
```

`EvidenceType` 初始集合：

- `METADATA_FOREIGN_KEY`
- `DDL_FOREIGN_KEY`
- `VIEW_JOIN`
- `PROCEDURE_JOIN`
- `TRIGGER_REFERENCE`
- `SQL_LOG_JOIN`
- `SQL_LOG_SUBQUERY_IN`
- `SQL_LOG_EXISTS`
- `SQL_LOG_TABLE_CO_OCCURRENCE`
- `NAMING_MATCH`
- `SOURCE_INDEX`
- `TARGET_UNIQUE`
- `COLUMN_TYPE_COMPATIBLE`
- `VALUE_CONTAINMENT_HIGH`
- `VALUE_OVERLAP_HIGH`
- `NEGATIVE_VALUE_MISMATCH`

## 归并规则

同一关系的归并 key：

```text
normalizedSourceTable
normalizedSourceColumn or "*"
normalizedTargetTable
normalizedTargetColumn or "*"
relationType
```

归并规则：

- 列级关系优先于表级关系。
- 如果已有 `orders.user_id -> users.id`，再出现 `orders -> users`，表级 evidence 可并入列级关系，但不降低列级关系精度。
- 如果只有表级关系，则保留表级关系。
- A->B 和 B->A 不自动视为相同，除非 relationType 是 `CO_OCCURRENCE` 且没有列信息。

## 置信度计算

基础分：

| EvidenceType | Score |
| --- | ---: |
| `METADATA_FOREIGN_KEY` | 0.98 |
| `DDL_FOREIGN_KEY` | 0.90 |
| `VIEW_JOIN` | 0.72 |
| `PROCEDURE_JOIN` | 0.70 |
| `TRIGGER_REFERENCE` | 0.65 |
| `SQL_LOG_JOIN` | 0.55 |
| `SQL_LOG_SUBQUERY_IN` | 0.58 |
| `SQL_LOG_EXISTS` | 0.58 |
| `SQL_LOG_TABLE_CO_OCCURRENCE` | 0.25 |
| `NAMING_MATCH` | 0.20 |
| `SOURCE_INDEX` | 0.10 |
| `TARGET_UNIQUE` | 0.18 |
| `COLUMN_TYPE_COMPATIBLE` | 0.08 |
| `VALUE_CONTAINMENT_HIGH` | 0.30 |
| `VALUE_OVERLAP_HIGH` | 0.20 |
| `NEGATIVE_VALUE_MISMATCH` | -0.30 |

正向证据合并：

```text
confidence = 1 - product(1 - evidenceScore)
```

负向证据处理：

```text
confidenceAfterNegative = positiveConfidence * product(1 + negativeScore)
```

例如 `NEGATIVE_VALUE_MISMATCH = -0.30`，则最终分数乘以 `0.70`。

边界：

- `METADATA_FOREIGN_KEY` 关系最低不低于 `0.95`。
- 最终最高封顶 `0.99`。
- 最终最低不低于 `0.0`。

## 可解释性要求

最终输出不能只给分数，必须包含：

- 每个 evidence 的 type。
- 每个 evidence 的 score。
- evidence 来源，例如 `metadata`、`ddl-file`、`mysql-slow-log`。
- 简短 detail。
- 可选 attributes，例如出现次数、样本行数、包含率。

## 验收标准

- 多 evidence 可归并成一条关系。
- 显式 FK 不会被弱证据降级为普通推断关系。
- 命名证据不能单独生成高置信 FK。
- 负向数据画像能降低最终分数。
- 无法确定方向的 JOIN 不生成方向错误的列级 FK-like。

## 测试设计

- `METADATA_FOREIGN_KEY` 单证据输出 0.98。
- `SQL_LOG_JOIN + TARGET_UNIQUE + NAMING_MATCH` 分数高于单独 `SQL_LOG_JOIN`。
- `NEGATIVE_VALUE_MISMATCH` 降低最终分数。
- `DECLARED_FK + SQL_LOG_JOIN` 的 subtype 仍是 `DECLARED_FK`。
- `JOIN a.x = b.y` 且两侧都非 unique、命名无提示时退化为 `CO_OCCURRENCE`。

