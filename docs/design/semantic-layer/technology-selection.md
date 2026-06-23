# 技术选型文档

## 1. 选型原则

| 原则 | 说明 |
| --- | --- |
| 与 relation-detector 一致 | 复用已有技术栈，降低集成成本 |
| v1 简单优先 | 先用文件/内存方案快速验证，v2 再升级到数据库/消息队列 |
| 确定性优先 | 存储、通信、校验等基础设施选确定性的，不用 AI |
| 可替换 | 每个选型都有明确的升级路径 |

## 2. 存储选型

### 2.1 候选方案

| 方案 | 优点 | 缺点 | 适用场景 |
| --- | --- | --- | --- |
| **JSON 文件** | 零依赖、可 git 版本控制、人类可读、调试方便 | 无并发、查询慢、无事务、大文件性能差 | v1 原型验证 |
| **PostgreSQL + JSONB** | 支持 JSON 查询、事务、并发、成熟 | 需要数据库运维、JSONB 查询不如关系表 | v2 生产环境 |
| **PostgreSQL + JSONB + pgvector** | 上述 + 向量检索 | 需要 pgvector 扩展 | v2 生产环境（含向量搜索） |
| **SQLite** | 零运维、嵌入式、支持 SQL | 并发写入差、向量检索需扩展 | v1 替代方案 |
| **MongoDB** | 原生 JSON 存储、灵活 schema | 运维成本、向量检索需 Atlas | 备选 |
| **Elasticsearch** | 全文搜索强、向量检索 | 运维重、过度设计 | 不适合 |

### 2.2 选型决策

**v1: JSON 文件**

```
选型理由：
1. relation-detector 已经是 JSON 文件输出，一致性高
2. 零运维依赖，开发者只需 clone 代码即可运行
3. 可 git 版本控制，方便审计和回滚
4. 语义对象数量在 1000 以内时，文件读写性能足够（< 100ms）
5. 快速验证语义层设计，不需要在存储上花时间

文件结构：
semantic-catalog/
  semantic-objects.json       # ~500KB（100 表规模）
  semantic-evidence-refs.json # ~200KB
  semantic-lexicon.json       # ~100KB
  semantic-embeddings.jsonl  # ~5MB（1536 维 × 500 对象）
  semantic-review-queue.json  # ~50KB
  semantic-question-traces.jsonl  # 追加写入
  catalog-metadata.json       # ~1KB

限制：
- 并发读取：可以（多 reader）
- 并发写入：不支持（单 writer）
- 最大对象数：< 10000（超过时考虑迁移）
- 查询：全量加载到内存，HashMap 索引
```

**v2: PostgreSQL + JSONB + pgvector**

```
迁移触发条件（满足任一）：
1. 语义对象 > 10000
2. 需要多用户并发读写
3. 需要增量部分查询（不全量加载）
4. 向量检索需要更高效的索引

迁移方案：
- semantic_object 表：id, object_type, payload(JSONB), created_at, updated_at
- semantic_evidence_ref 表：object_id, evidence_type, fingerprint, payload(JSONB)
- semantic_lexicon 表：term, normalized_term, maps_to_object_id, source, confidence
- semantic_embedding 表：object_id, embedding(vector(1536)), model, updated_at
- 使用 pgvector 的 IVFFlat 索引加速向量检索
```

### 2.3 为什么不选其他方案

| 方案 | 不选原因 |
| --- | --- |
| SQLite | v1 用 JSON 文件更简单；v2 需要向量检索，SQLite 不原生支持 |
| MongoDB | 引入新运维依赖；项目已用 PostgreSQL（relation-detector 可连 PG），不想维护两种数据库 |
| Elasticsearch | 过度设计，语义对象搜索不需要全文搜索引擎的复杂度 |

## 3. 模块间通信（数据总线）选型

### 3.1 候选方案

| 方案 | 优点 | 缺点 | 适用场景 |
| --- | --- | --- | --- |
| **内存 Java 对象传递** | 零延迟、类型安全、无序列化开销 | 单进程、不可跨机、不可持久化中间状态 | v1 单机运行 |
| **JSON 文件** | 可持久化中间状态、可调试、可断点续跑 | 序列化开销、无类型安全 | 离线链路各阶段间 |
| **消息队列（RabbitMQ/Kafka）** | 解耦、异步、可重试 | 运维重、过度设计 | 不适合 |
| **HTTP/gRPC** | 跨机、跨语言 | 序列化开销、网络延迟 | v2 微服务化 |

### 3.2 选型决策

**v1: 内存 Java 对象 + JSON 文件**

```
离线链路：内存对象传递
  ScanBundle → EvidenceGraph → EnrichmentResult → Catalog
  原因：同一 JVM 进程内，离线批处理，不需要跨进程通信
  阶段间可选 JSON 文件保存中间状态，用于调试和断点续跑

在线链路：内存对象传递
  QuestionIntent → SearchResult → AnswerPlan → SqlDraft → ValidationResult → Answer
  原因：同一请求处理链，延迟敏感（< 3s 总预算）

模块内部：Java interface 调用
  每个模块暴露 Java interface，实现类通过构造函数注入依赖
  不使用 DI 框架（Spring），保持轻量
```

**为什么不用消息队列：**
- v1 是单机批处理，离线链路顺序执行，不需要异步解耦
- 在线链路延迟敏感，消息队列增加延迟
- 引入 RabbitMQ/Kafka 增加运维复杂度，与 v1 快速验证目标矛盾

**为什么不用 HTTP/gRPC：**
- v1 不需要跨机通信
- v2 如需微服务化，可以按模块边界拆分为独立服务

## 4. 大模型选型

### 4.1 LLM 选型

| 模型 | 优点 | 缺点 | 适用场景 |
| --- | --- | --- | --- |
| **GPT-4.1** | 结构化输出强、中文好、instruction following 好 | 成本中等、需要 API key | 语义生成和问题理解 |
| GPT-4o | 多模态、速度快 | 结构化输出不如 4.1 稳定 | 备选 |
| Claude Sonnet 4.6 | 中文好、长上下文 | 结构化输出需 prompt 约束 | 备选 |
| 开源模型（Qwen/Llama） | 私有部署、无数据外泄 | 中文语义生成质量参差、需要 GPU | 对数据安全要求高的场景 |

**选型决策：GPT-4.1（默认），可配置切换**

```
选型理由：
1. 结构化 JSON 输出最稳定（JSON mode + structured output）
2. 中文业务语义理解好（表名翻译、同义词扩展）
3. Instruction following 好（严格遵守 evidenceRefs 约束）
4. 通过 EnrichmentConfig 可切换模型

配置示例：
enrichment:
  llm:
    model: gpt-4.1
    temperature: 0.1
    maxTokens: 4096

questionUnderstanding:
  llm:
    model: gpt-4.1
    temperature: 0.0
    maxTokens: 1024
```

### 4.2 Embedding 模型选型

| 模型 | 维度 | 优点 | 缺点 |
| --- | --- | --- | --- |
| **text-embedding-3-small** | 1536 | 性价比高、中文好、批量 API 快 | 需要 API key |
| text-embedding-3-large | 3072 | 精度最高 | 成本高 4 倍、存储大 2 倍 |
| text-embedding-ada-002 | 1536 | 兼容旧版 | 中文不如 3-small |
| 开源模型（BGE/M3E） | 可配置 | 私有部署 | 中文语义可能不如 OpenAI |

**选型决策：text-embedding-3-small（默认）**

```
选型理由：
1. 1536 维在 500 对象规模下，全量 cosine similarity 计算 < 10ms
2. 中文语义召回质量好
3. 成本低（$0.02/1M tokens，500 对象约 $0.001/次全量索引）
4. 批量 API 支持 2048 条/次，离线索引快
```

## 5. SQL 解析选型

### 5.1 SQL Validator 的 SQL 解析

| 方案 | 优点 | 缺点 |
| --- | --- | --- |
| **正则表达式** | 简单、零依赖 | 不准确、无法处理复杂 SQL |
| **复用 relation-detector parser** | 准确、已有、方言支持 | 重、需要适配 |
| ANTLR | 标准、可生成 parser | 需要 grammar 文件、重 |
| JSqlParser | 轻量、Java 原生 | 不支持所有方言 |

**选型决策：v1 正则 + v2 复用 relation-detector parser**

```
v1: 正则提取表名、列名、JOIN 条件
  原因：SQL draft 是模板生成的，结构固定简单，正则足够
  限制：无法处理 CTE、子查询等复杂 SQL

v2: 复用 relation-detector 的 StructuredSqlParser
  原因：relation-detector 已有完整的 MySQL/PG SQL 解析能力
  方式：SqlValidator 依赖 relation-detector-core 的 parser 模块
  收益：准确的表/列提取、方言校验、语法错误检测
```

## 6. 框架与语言选型

### 6.1 语言

**Java 17**（与 relation-detector 一致）

```
选型理由：
1. relation-detector 已用 Java 17 + Maven 多模块
2. 可直接依赖 relation-detector 的 contracts 和 core 模块
3. 类型安全，适合定义严格的接口契约
4. 团队熟悉

不选其他语言的原因：
- Python: LLM 调用方便，但与 relation-detector 集成困难
- TypeScript: 前端友好，但后端生态不如 Java
- Kotlin: 与 Java 互操作好，但多引入一门语言增加复杂度
```

### 6.2 构建工具

**Maven**（与 relation-detector 一致）

### 6.3 依赖

```
v1 最小依赖：
- Jackson（JSON 序列化）
- OkHttp/HttpClient（LLM API 调用）
- JUnit 5（测试）

v2 可选依赖：
- PostgreSQL JDBC Driver
- pgvector JDBC
- Picocli（CLI 框架）
```

## 7. 选型总结

| 维度 | v1 选择 | v2 升级路径 |
| --- | --- | --- |
| 存储 | JSON 文件 | PostgreSQL + JSONB + pgvector |
| 模块通信 | 内存 Java 对象 | 可选 HTTP/gRPC 微服务 |
| 中间状态 | 可选 JSON 文件 | 数据库持久化 |
| LLM | GPT-4.1（可配置） | 可切换其他模型 |
| Embedding | text-embedding-3-small | 可切换 text-embedding-3-large 或开源模型 |
| SQL 解析 | 正则（v1） | 复用 relation-detector parser |
| 语言 | Java 17 | 不变 |
| 构建 | Maven | 不变 |
| 依赖注入 | 构造函数手动注入 | 可选 Spring/Guice |
| 日志 | java.util.logging → SLF4J | 不变 |
| 测试 | JUnit 5 + AssertJ | 不变 |