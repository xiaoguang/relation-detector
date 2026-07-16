/**
 * CN: PostgreSQL parser mode 共享的 typed projection 与 set-operation 语义。
 *
 * <p>EN: Typed projection and set-operation semantics shared by PostgreSQL parser modes.
 * <p>Responsibility: 提供 token/full 共享但无 generated-parser 依赖的 PostgreSQL semantics / Shares neutral semantics.
 * <p>Inputs: typed set-operation and projection descriptors / Typed set-operation and projection descriptors.
 * <p>Outputs: deterministic branch layouts used by both parser modes / Deterministic layouts for parser modes.
 * <p>Upstream/Downstream: version/token adapters 上游，event emitters 下游 / Between adapters and event emitters.
 * <p>Forbidden: 不 import generated contexts 或让 parser modes 互相 delegate / Must not couple parser modes.
 */
package com.relationdetector.postgres.common;
