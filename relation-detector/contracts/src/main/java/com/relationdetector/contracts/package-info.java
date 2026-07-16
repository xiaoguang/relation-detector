/**
 * CN: 契约模块的公共枚举入口。
 *
 * <p>EN: Public enum entry point for the contracts module.
 * <p>Responsibility: 定义跨模块稳定契约与公共枚举 / Defines stable cross-module contracts and enums.
 * <p>Inputs: adaptor、core 与 CLI 的公共类型需求 / Shared type requirements from adaptors, core, and CLI.
 * <p>Outputs: 可序列化模型和 SPI 类型 / Serializable models and SPI types.
 * <p>Upstream/Downstream: 由所有 relation-detector 模块编译依赖 / Consumed by every relation-detector module.
 * <p>Forbidden: 不包含 parser、JDBC 或扫描编排 / Must not contain parser, JDBC, or scan orchestration logic.
 */
package com.relationdetector.contracts;
