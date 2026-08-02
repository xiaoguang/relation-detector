/**
 * CN: 组装无方言 live 能力的 portable SQL adaptor。
 *
 * <p>EN: Portable SQL adaptor assembly without live-database capabilities.
 * <p>Responsibility: 绑定 common typed parser、script framer 和 log extractor / Binds the common typed parser, script framer, and log extractor.
 * <p>Inputs: portable SQL 文件与 scan parser 请求 / Portable SQL files and scan parser requests.
 * <p>Outputs: SPI v6 parser capability 组 / An SPI v6 parser capability group.
 * <p>Upstream/Downstream: 由 adaptor registry 装载并向 core parser runtime 提供实现 / Loaded by the adaptor registry and consumed by the core parser runtime.
 * <p>Forbidden: 不作为方言 fallback，不访问 JDBC，不合并事实 / Must not act as a dialect fallback, access JDBC, or merge facts.
 */
package com.relationdetector.core.adaptor.common;
