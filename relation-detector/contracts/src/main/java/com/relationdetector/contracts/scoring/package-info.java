/**
 * 默认证据分值契约层。
 *
 * <p>CN: 本包只放跨模块共享的基础分值常量。最终置信度计算在 core.scoring，
 * adaptor 和 parser 只引用这些常量，不在本包实现评分流程。
 *
 * <p>EN: Default evidence-score contract layer. This package exposes shared
 * base score constants; final confidence calculation lives in core.scoring.
 * <p>Responsibility: 定义 evidence score 常量和评分契约 / Defines evidence-score constants and scoring contracts.
 * <p>Inputs: evidence type 与 scoring policy / Evidence types and scoring policy.
 * <p>Outputs: core scoring 可复用的稳定分值 / Stable weights consumed by core scoring.
 * <p>Upstream/Downstream: contracts 声明，core.scoring 应用 / Declared here and applied by core.scoring.
 * <p>Forbidden: 不读取 SQL、metadata 或 profile 数据 / Must not read SQL, metadata, or profile data.
 */
package com.relationdetector.contracts.scoring;
