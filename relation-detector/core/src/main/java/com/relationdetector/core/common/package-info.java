/**
 * CN: core 内跨事实链路复用的无状态公共值类型。
 *
 * <p>EN: Stateless common value types shared across core fact pipelines.
 * <p>Responsibility: 提供无方言依赖的通用值处理 / Provides dialect-neutral value utilities.
 * <p>Inputs: core 内部的规范化字符串和小型模型 / Normalized strings and small core models.
 * <p>Outputs: 可复用、确定性的 helper 结果 / Reusable deterministic helper results.
 * <p>Upstream/Downstream: 被多个 core 语义包调用 / Called by multiple core semantic packages.
 * <p>Forbidden: 不解析 SQL 或执行 naming heuristic / Must not parse SQL or run naming heuristics.
 */
package com.relationdetector.core.common;
