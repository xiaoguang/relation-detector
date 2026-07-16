/**
 * 诊断告警工厂层。
 *
 * <p>CN: 本包统一构造 parser、日志、DDL、对象采集和扫描失败 warning，保证 code、
 * source、line 与 raw statement attributes 的格式一致。业务层不要各自拼装不兼容
 * 的 warning。
 *
 * <p>EN: Diagnostic warning factory layer. It standardizes parser, log, DDL,
 * object-collection, and scan-failure warnings so code, source, line, and raw
 * statement attributes stay consistent across the system.
 * <p>Responsibility: 统一构造和脱敏 parser/live warnings / Constructs and sanitizes parser and live warnings.
 * <p>Inputs: typed parser failures、SQLException 与 definition context / Parser failures, SQL exceptions, and context.
 * <p>Outputs: code、source、line 和安全 attributes 一致的 warnings / Consistent warnings with safe attributes.
 * <p>Upstream/Downstream: collectors 调用，ScanResult/CLI 输出 / Called by collectors and emitted through ScanResult.
 * <p>Forbidden: 不输出 JDBC URL、SQL、driver message 或业务值 / Must not expose URLs, SQL, driver messages, or values.
 */
package com.relationdetector.core.diagnostics;
