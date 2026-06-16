# Phase 7：可选数据画像详细设计

## 目标

实现可选数据画像能力，用真实数据统计增强或削弱关系置信度。

数据画像默认关闭，只有用户显式配置 `sources.dataProfile.enabled: true` 时才运行。该能力必须严格受采样、超时和候选范围限制，避免对生产库造成不可控压力。

## 设计原则

- 默认不读取业务数据。
- 只对已有候选关系运行，不做全库列组合扫描。
- 所有查询必须受 `sampleRows` 和 `timeoutSeconds` 控制。
- 画像结果只作为 evidence，不直接覆盖显式 FK。
- 画像可以增强关系，也可以提供负向证据降低置信度。
- 输出中必须说明样本规模、命中率、阈值和限制。

## 配置

```yaml
sources:
  dataProfile:
    enabled: false
    sampleRows: 10000
    timeoutSeconds: 30
    minContainmentRatio: 0.95
    minOverlapRatio: 0.80
    maxCandidatePairs: 1000
    verifyDeclaredForeignKeys: false
```

字段说明：

- `sampleRows`：每个候选关系最多抽样 distinct source 值数量。
- `timeoutSeconds`：单个画像查询最大执行时间。
- `minContainmentRatio`：值域包含强证据阈值。
- `minOverlapRatio`：值重合辅助证据阈值。
- `maxCandidatePairs`：本次扫描最多画像的候选关系数量。
- `verifyDeclaredForeignKeys`：是否也对显式 FK 做画像验证，默认 false。

## 候选选择

画像候选来源：

- JOIN 推断关系。
- 子查询推断关系。
- 命名 + 索引/unique 支持的关系。
- 表级共现中存在强命名候选列的关系。

默认跳过：

- 已经是 `DECLARED_FK` 的关系。
- 无任何列级候选的纯表级共现关系。
- 类型明显不兼容的列。
- 被 include/exclude 过滤排除的表。

排序优先级：

1. 已有 JOIN/子查询 evidence 的候选。
2. 有 target unique 的候选。
3. 有 source index 的候选。
4. 命名匹配候选。
5. 共现中派生的候选。

## 画像指标

### 列定义相似

检查：

- 数据类型兼容。
- 长度兼容。
- precision/scale 兼容。
- 字符集或排序规则兼容。
- nullable 形态合理。

输出 evidence：

```text
COLUMN_TYPE_COMPATIBLE
score: 0.08
```

类型明显不兼容时输出负向 evidence。

### 目标唯一性

判断 target 列是否：

- primary key。
- unique constraint。
- unique index。
- 抽样近似唯一。

输出 evidence：

```text
TARGET_UNIQUE
score: 0.18
```

### 源列索引

判断 source 列是否有普通索引或复合索引前缀。

输出 evidence：

```text
SOURCE_INDEX
score: 0.10
```

### 值域包含

计算 source distinct 非空值中，能在 target 列命中的比例。

```text
containmentRatio = matchedDistinctSourceValues / sampledDistinctSourceValues
```

当 `containmentRatio >= minContainmentRatio`：

```text
VALUE_CONTAINMENT_HIGH
score: 0.30
```

当比例很低，例如小于 0.50：

```text
NEGATIVE_VALUE_MISMATCH
score: -0.30
```

### 值重合率

用于辅助判断，不如值域包含强。

```text
overlapRatio = matchedValues / sampledRows
```

当 `overlapRatio >= minOverlapRatio`：

```text
VALUE_OVERLAP_HIGH
score: 0.20
```

### 基数形态

典型外键形态：

- source distinct 值数量小于 source 总行数。
- target distinct 值数量接近 target 总行数。
- source 多行可指向同一个 target。

基数形态作为 attributes 记录，v1 不单独给高分，避免统计误导。

### 空值比例

计算 source null ratio：

- 高空值比例表示可选关系。
- 不直接否定关系。
- 写入 evidence attributes。

## 查询策略

必须满足：

- 只读查询。
- 只抽样候选列。
- 不取出敏感值到输出。
- 输出只包含统计结果，不输出实际业务值。

抽样方式：

- 优先抽 distinct source 值。
- 每个候选最多 `sampleRows`。
- 大表由数据库执行 limit。
- 每个查询设置超时。

## 与评分模型关系

画像 evidence 进入 Phase 2 的统一评分模型：

- 增强：`COLUMN_TYPE_COMPATIBLE`、`TARGET_UNIQUE`、`VALUE_CONTAINMENT_HIGH`。
- 降低：`NEGATIVE_VALUE_MISMATCH`。

subtype 规则：

- 如果关系原本是 `DECLARED_FK`，保持 `DECLARED_FK`。
- 如果关系原本是 `DDL_DECLARED_FK`，保持 `DDL_DECLARED_FK`。
- 如果关系原本是 JOIN/命名推断，并且画像强支持，则可提升为 `PROFILE_SUPPORTED_FK`。
- 如果画像反证强，降低 confidence，但保留 evidence 说明原因。

## 安全与性能

安全要求：

- 不输出采样到的具体值。
- password 不进入日志。
- 查询错误只记录表/列名和错误摘要。

性能要求：

- 总候选数量受 `maxCandidatePairs` 限制。
- 单候选查询受 `timeoutSeconds` 限制。
- 可通过 CLI 输出画像被跳过的原因。

跳过原因示例：

- `candidate limit exceeded`
- `incompatible column type`
- `missing target column`
- `profile disabled`
- `query timeout`

## 验收标准

- 默认配置不执行任何业务数据查询。
- 开启画像后只对候选关系查询。
- 高值域包含率能增加置信度。
- 明显值不匹配能降低置信度。
- 超时不会中断整体扫描。
- 输出不包含真实业务值。

## 测试设计

- profile disabled 测试。
- candidate selection 测试。
- type compatible evidence 测试。
- target unique evidence 测试。
- high containment evidence 测试。
- negative mismatch evidence 测试。
- timeout warning 测试。
- maxCandidatePairs 限制测试。
- 不输出真实值测试。

